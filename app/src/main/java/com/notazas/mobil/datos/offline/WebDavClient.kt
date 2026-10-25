package com.notazas.mobil.datos.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Cliente WebDAV mínimo contra BoxAbalar (Nextcloud) para o modo
 * "fóra do colexio": só GET/PUT/MKCOL, cos mesmos verbos e a mesma
 * construción de URL que app/services/nextcloud_client.py
 * (_url_webdav/_asegurar_cartafoles) para que un ficheiro escrito por
 * un lado o lea ben o outro.
 */
class WebDavClient(
    private val urlServidor: String,
    private val usuarioNextcloud: String,
    private val token: String
) {
    private val cliente = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val credencial = Credentials.basic(usuarioNextcloud, token)

    private fun codificarSegmento(segmento: String) =
        URLEncoder.encode(segmento, "UTF-8").replace("+", "%20")

    private fun urlCompleta(ruta: String): String {
        val base = urlServidor.trimEnd('/')
        val camino = ruta.trim('/').split("/").joinToString("/") { codificarSegmento(it) }
        return "$base/remote.php/dav/files/${codificarSegmento(usuarioNextcloud)}/$camino"
    }

    /** null se o ficheiro non existe aínda (404) — non é un erro. */
    suspend fun ler(ruta: String): String? = withContext(Dispatchers.IO) {
        val peticion = Request.Builder().url(urlCompleta(ruta)).header("Authorization", credencial).get().build()
        cliente.newCall(peticion).execute().use { resposta ->
            when {
                resposta.code == 404 -> null
                resposta.isSuccessful -> resposta.body?.string()
                else -> throw IOException("BoxAbalar devolveu ${resposta.code} ao ler '$ruta'")
            }
        }
    }

    suspend fun escribir(ruta: String, contido: String): Int = withContext(Dispatchers.IO) {
        asegurarCartafoles(ruta)
        val corpo = contido.toRequestBody("application/octet-stream".toMediaType())
        val peticion = Request.Builder().url(urlCompleta(ruta)).header("Authorization", credencial).put(corpo).build()
        cliente.newCall(peticion).execute().use { resposta ->
            if (!resposta.isSuccessful) throw IOException("BoxAbalar devolveu ${resposta.code} ao gardar '$ruta' (url: ${urlCompleta(ruta)})")
            resposta.code
        }
    }

    /** Contido dun ficheiro remoto xunto co seu ETag (para subir despois con If-Match). */
    class FicheiroRemoto(val contido: String, val etag: String?)

    /** null se non existe (404). Calquera outro erro lanza IOException: a
     * sincronización ABORTA, non se sube nunca por riba dun ficheiro que non se le. */
    suspend fun lerConEtag(ruta: String): FicheiroRemoto? = withContext(Dispatchers.IO) {
        val peticion = Request.Builder().url(urlCompleta(ruta)).header("Authorization", credencial).get().build()
        cliente.newCall(peticion).execute().use { resposta ->
            when {
                resposta.code == 404 -> null
                resposta.isSuccessful -> FicheiroRemoto(resposta.body?.string().orEmpty(), resposta.header("ETag"))
                else -> throw IOException("BoxAbalar devolveu ${resposta.code} ao ler '$ruta'")
            }
        }
    }

    /** PUT condicional: con `etag` só sobrescribe se o ficheiro non cambiou desde que
     * o leímos (If-Match). Devolve 412 se algén subiu mentres tanto (o chamante
     * debe volver ler e fusionar); calquera outro fallo lanza IOException. */
    suspend fun escribirSe(ruta: String, contido: String, etag: String?): Int = withContext(Dispatchers.IO) {
        asegurarCartafoles(ruta)
        val corpo = contido.toRequestBody("application/octet-stream".toMediaType())
        val construtor = Request.Builder().url(urlCompleta(ruta)).header("Authorization", credencial).put(corpo)
        if (etag != null) construtor.header("If-Match", etag)
        cliente.newCall(construtor.build()).execute().use { resposta ->
            if (resposta.code != 412 && !resposta.isSuccessful) {
                throw IOException("BoxAbalar devolveu ${resposta.code} ao gardar '$ruta'")
            }
            resposta.code
        }
    }

    /** MKCOL de cada anaco do camiño (coma fai o servidor). 405/409
     * ("xa existe") non son erro. */
    private suspend fun asegurarCartafoles(ruta: String) = withContext(Dispatchers.IO) {
        val segmentos = ruta.trim('/').split("/").dropLast(1)
        var acumulado = ""
        for (segmento in segmentos) {
            acumulado += "/$segmento"
            val peticion = Request.Builder().url(urlCompleta(acumulado)).header("Authorization", credencial).method("MKCOL", null).build()
            cliente.newCall(peticion).execute().use { resposta ->
                if (resposta.code != 201 && resposta.code != 405 && resposta.code != 409) {
                    throw IOException("Non se puido crear o cartafol '$acumulado' en BoxAbalar (HTTP ${resposta.code})")
                }
            }
        }
    }
}
