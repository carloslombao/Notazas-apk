package com.notazas.mobil.datos.rede

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Punto de acceso único á API do backend Notazas (mesmo servidor
 * FastAPI que xa usa a web).
 *
 * En modo "No colexio" isto apunta á IP local do servidor. Por
 * defecto úsase 10.0.2.2, que é o alias especial que o EMULADOR de
 * Android Studio usa para referirse a "localhost do PC" — así, se
 * arrincas o backend en local (Iniciar_Notazas.bat), o emulador xa o
 * atopa sen configurar nada. Nun móbil real hai que cambiar isto pola
 * IP real do servidor na rede local (configurable dende Login/Axustes).
 */
object ConfiguracionServidor {
    const val URL_POR_DEFECTO_EMULADOR = "http://10.0.2.2:8000/"
}

object ApiClient {

    private var baseUrlActual: String = ConfiguracionServidor.URL_POR_DEFECTO_EMULADOR
    private var tokenActual: String? = null
    private var retrofitCacheado: Retrofit? = null

    /** Cambia o servidor de destino (ex. dende Login/Axustes). */
    fun cambiarServidor(novaBaseUrl: String) {
        val normalizada = if (novaBaseUrl.endsWith("/")) novaBaseUrl else "$novaBaseUrl/"
        if (normalizada != baseUrlActual) {
            baseUrlActual = normalizada
            retrofitCacheado = null
        }
    }

    fun servidorActual(): String = baseUrlActual

    /** Miniatura dunha foto de traballo aínda sen alumno (QR non lido). */
    fun urlImaxePendente(pendenteId: String): String? {
        val token = tokenActual ?: return null
        return "${baseUrlActual}traballos/pendente/$pendenteId/imaxe?t=$token"
    }

    /** Foto de traballo xa ligada a un alumno. */
    fun urlImaxeFicheiro(ficheiroId: Int): String? {
        val token = tokenActual ?: return null
        return "${baseUrlActual}traballos/ficheiro/$ficheiroId/imaxe?t=$token"
    }

    /** URL directa para <img>/Coil da foto dun alumno (vai por query
     * param porque as imaxes non poden levar cabeceira Authorization). */
    fun urlFotoAlumno(alumnoId: Int): String? {
        val token = tokenActual ?: return null
        return "${baseUrlActual}alumnos/$alumnoId/foto?t=$token"
    }

    /** Garda o token JWT tras un login correcto (ou o borra ao pechar
     * sesión). Engádese automaticamente a todas as chamadas seguintes. */
    fun definirToken(token: String?) {
        tokenActual = token
    }

    /** Descarga un ficheiro completo (PDF dun exame, foto...) polo seu
     * id. O endpoint valida o token por query param, non por cabeceira. */
    suspend fun descargarFicheiro(ficheiroId: Int): okhttp3.ResponseBody =
        servizo.verFicheiroCompleto(ficheiroId, tokenActual.orEmpty())

    /** Chámase automaticamente cando o servidor rexeita o token
     * (401 — normalmente caducado). Configúrao NotazasApp para volver
     * ao Login e borrar a sesión gardada. */
    var onSesionCaducada: (() -> Unit)? = null

    val servizo: NotazasApiService
        get() = obterRetrofit().create(NotazasApiService::class.java)

    private fun obterRetrofit(): Retrofit {
        return retrofitCacheado ?: construirRetrofit().also { retrofitCacheado = it }
    }

    private fun construirRetrofit(): Retrofit {
        val json = Json { ignoreUnknownKeys = true }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val autenticacion = okhttp3.Interceptor { chain ->
            val peticionOrixinal = chain.request()
            val token = tokenActual
            val peticion = if (token != null) {
                peticionOrixinal.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                peticionOrixinal
            }
            // A carga de fotos/exames fai lectura de QR e compresión
            // (e OCR, no caso dos exames) no servidor: pode tardar máis dos
            // 15 s por defecto (só para esas chamadas).
            val rutaLenta = peticion.url.encodedPath.endsWith("traballos/cargar") || peticion.url.encodedPath.endsWith("escanear")
            val cadea = if (rutaLenta) {
                chain.withReadTimeout(120, TimeUnit.SECONDS).withWriteTimeout(120, TimeUnit.SECONDS)
            } else {
                chain
            }
            val resposta = cadea.proceed(peticion)
            if (resposta.code == 401) {
                onSesionCaducada?.invoke()
            }
            resposta
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(autenticacion)
            .addInterceptor(logging)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrlActual)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
