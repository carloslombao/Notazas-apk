package com.notazas.mobil.datos.offline

import android.content.Context
import android.util.Log
import com.notazas.mobil.datos.local.CredenciaisRemotas
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "RepositorioOffline"
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Garda localmente a asignación xa modificada e márcaa como "pendente
 * de subir". NON intenta subila agora — WebDAV non aguanta ben moitas
 * escrituras seguidas (unha por cada nota gardada), así que a subida
 * real faise á parte, de vez, con sincronizarPendentes(): ao premer
 * "Sincronizar" en Axustes, ao pechar sesión, ou automaticamente ao
 * abrir a app se quedara algo sen subir dunha vez anterior.
 */
suspend fun gardarLocalPendente(contexto: Context, entrada: CacheAsignacionEntrada) {
    CacheOffline.gardarAsignacion(contexto, entrada)
    CacheOffline.marcarPendente(contexto, entrada.asignacion_id)
}

/** Resultado de sincronizar TODAS as asignacións pendentes dunha vez. */
data class ResultadoSincronizacion(
    val totalPendentes: Int,
    val sincronizadas: Int,
    val erros: List<String>
) {
    val todoOk: Boolean get() = erros.isEmpty()
    val mensaxe: String
        get() = when {
            totalPendentes == 0 -> "Nada que sincronizar — xa estaba todo ao día."
            todoOk -> "Sincronizadas $sincronizadas materia(s) con BoxAbalar."
            else -> "Sincronizadas $sincronizadas de $totalPendentes. Fallou: ${erros.joinToString("; ")}"
        }
}

/** Intenta subir a BoxAbalar TODAS as asignacións marcadas como
 * pendentes. As que fallen quedan marcadas para a próxima vez (non se
 * perde nada, só queda sen subir un pouco máis). */
/** Sincroniza con BoxAbalar só as asignacións con cambios sen subir. */
suspend fun sincronizarPendentes(contexto: Context): ResultadoSincronizacion =
    sincronizarAsignacions(contexto, soPendentes = true)

/** Sincroniza con BoxAbalar TODAS as asignacións da caché (tamén as que non
 * teñen cambios locais, para traer os cambios feitos desde outros
 * dispositivos). Fala directamente con BoxAbalar por WebDAV: funciona igual
 * con datos móbiles, con wifi allea ou coa wifi do colexio (non depende de
 * que o servidor do cole sexa alcanzable). */
suspend fun sincronizarTodasConBoxAbalar(contexto: Context): ResultadoSincronizacion =
    sincronizarAsignacions(contexto, soPendentes = false)

/** As que fallen quedan marcadas para a próxima vez (non se perde nada). */
private suspend fun sincronizarAsignacions(contexto: Context, soPendentes: Boolean): ResultadoSincronizacion {
    val pendentesIds = if (soPendentes) {
        CacheOffline.listarPendentesIds(contexto)
    } else {
        CacheOffline.listarAsignacionsCacheadas(contexto).map { it.asignacion_id }
    }
    if (pendentesIds.isEmpty()) return ResultadoSincronizacion(0, 0, emptyList())

    val credenciais = CredenciaisRemotas.cargar()
    val persoal = credenciais?.nextcloudPersoal
    if (credenciais == null || persoal == null) {
        return ResultadoSincronizacion(
            totalPendentes = pendentesIds.size, sincronizadas = 0,
            erros = listOf("Non hai BoxAbalar persoal configurado — configúrao na web e volve escanear o QR")
        )
    }

    var ok = 0
    val erros = mutableListOf<String>()
    for (asignacionId in pendentesIds) {
        val entrada = CacheOffline.lerAsignacion(contexto, asignacionId)
        if (entrada == null) {
            CacheOffline.limparPendente(contexto, asignacionId) // nada que subir xa
            continue
        }
        try {
            val cliente = WebDavClient(persoal.urlServidor, persoal.usuario, persoal.token)
            val fusionado = fusionarEnBoxAbalar(cliente, entrada, credenciais.notazasFieldKey)
            // Se mentres sincronizábamos se gardou algo novo no móbil, non se pisa: queda
            // pendente e na próxima volta fúsionase de novo.
            val actual = CacheOffline.lerAsignacion(contexto, asignacionId)
            if (actual != null && actual.snapshot.modificado_en == entrada.snapshot.modificado_en) {
                CacheOffline.gardarAsignacion(contexto, entrada.copy(snapshot = fusionado))
                CacheOffline.limparPendente(contexto, asignacionId)
            }
            Log.i(TAG, "Fusionado e subido a BoxAbalar: ${entrada.ruta_webdav}")
            ok++
        } catch (e: Exception) {
            Log.w(TAG, "Non se puido sincronizar a asignación $asignacionId", e)
            erros.add("${entrada.snapshot.materia} ${entrada.snapshot.grupo_letra}: ${e.message ?: e.javaClass.simpleName}")
        }
    }
    return ResultadoSincronizacion(pendentesIds.size, ok, erros)
}

/** Le o ficheiro de BoxAbalar, fúsionao co snapshot local rexistro a
 * rexistro e sube o resultado con If-Match (se alguén subiu mentres
 * tanto, 412: volve ler e fusionar, máx. 3 intentos). Se o remoto non
 * se pode ler ou descifrar, ABORTA: non se sobrescribe nunca un ficheiro
 * que non se entende. Devolve o snapshot fusionado que quedou subido. */
private suspend fun fusionarEnBoxAbalar(cliente: WebDavClient, entrada: CacheAsignacionEntrada, clave: String): SnapshotAsignacion {
    repeat(3) {
        val remotoFicheiro = cliente.lerConEtag(entrada.ruta_webdav)
        var remoto: SnapshotAsignacion? = null
        val fusionado = if (remotoFicheiro == null) {
            entrada.snapshot.copy(versao = 2) // non existe en BoxAbalar: sube directamente
        } else {
            remoto = try {
                json.decodeFromString(SnapshotAsignacion.serializer(), FernetCifrado.descifrar(remotoFicheiro.contido, clave))
            } catch (e: Exception) {
                throw java.io.IOException("Non se puido descifrar/entender o ficheiro de BoxAbalar; non se sobrescribe (${e.message})", e)
            }
            fusionarSnapshots(entrada.snapshot, remoto)
        }

        // Nada novo que dicirlle a BoxAbalar (só cambia a marca de hora): non se sube, para non
        // invalidar sen necesidade o ETag que teñen outros dispositivos.
        if (remoto != null && fusionado.copy(modificado_en = remoto.modificado_en) == remoto) return fusionado

        val contido = FernetCifrado.cifrar(json.encodeToString(SnapshotAsignacion.serializer(), fusionado), clave)
        val codigo = cliente.escribirSe(entrada.ruta_webdav, contido, remotoFicheiro?.etag)
        if (codigo != 412) return fusionado
        Log.i(TAG, "BoxAbalar cambiou durante a sincronización (412), reintentando")
    }
    throw java.io.IOException("BoxAbalar seguiu cambiando durante a sincronización (3 intentos); proba de novo")
}

/** Hora actual en HH:mm:ss (hora local do móbil), só para mensaxes na UI. */
fun horaActualCurta(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
