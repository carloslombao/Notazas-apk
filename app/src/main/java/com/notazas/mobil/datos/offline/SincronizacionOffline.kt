package com.notazas.mobil.datos.offline

import android.content.Context
import android.util.Log
import com.notazas.mobil.datos.local.CredenciaisRemotas
import com.notazas.mobil.datos.rede.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TAG = "SincronizacionOffline"
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Resultado do botón "Sincronizar" para amosar na UI. */
data class ResultadoSincronizacionTotal(val mensaxe: String, val todoOk: Boolean)

/**
 * Botón "Sincronizar" de Axustes. Sempre fala con BoxAbalar DIRECTAMENTE
 * (WebDAV), tamén se o móbil está na wifi do colexio e o servidor do cole
 * é alcanzable: non depende del.
 *   1) Fusión por rexistro de TODAS as materias co que haxa en BoxAbalar
 *      (sube os cambios feitos no móbil e trae os dos outros dispositivos).
 *   2) Se se chega a un servidor Notazas, pídelle que tamén fusione BoxAbalar
 *      coa súa base de datos (POST /nextcloud/sync), e logo refréscase a
 *      caché offline co que ten o servidor. Se non se chega, non é un erro.
 */
suspend fun sincronizarTodo(contexto: Context): ResultadoSincronizacionTotal =
    withContext(Dispatchers.Default) { sincronizarTodoInterno(contexto) }

@Volatile private var ultimoRefrescoCacheMs = System.currentTimeMillis()

/**
 * Refresca a caché offline co servidor SEN repetilo en cada apertura dunha pantalla: descargar e
 * cifrar todos os snapshots é caro (rede, JSON grande, cifrado en disco). A app xa o fai ao
 * arrancar (MainActivity), polo que só se volve facer se pasaron `minutos` desde o último refresco.
 */
suspend fun refrescarCacheSeFaiFalta(contexto: Context, minutos: Long = 10) {
    val agora = System.currentTimeMillis()
    if (agora - ultimoRefrescoCacheMs < minutos * 60_000) return
    ultimoRefrescoCacheMs = agora
    withContext(Dispatchers.Default) { prepararParaForaDoColexio(contexto) }
}

private suspend fun sincronizarTodoInterno(contexto: Context): ResultadoSincronizacionTotal {
    val partes = mutableListOf<String>()
    var todoOk = true

    val boxAbalar = try {
        sincronizarTodasConBoxAbalar(contexto)
    } catch (e: Exception) {
        Log.w(TAG, "Erro sincronizando con BoxAbalar", e)
        ResultadoSincronizacion(1, 0, listOf(e.message ?: e.javaClass.simpleName))
    }
    partes.add("BoxAbalar: ${boxAbalar.mensaxe}")
    if (!boxAbalar.todoOk) todoOk = false

    val servidor = try {
        ApiClient.servizo.sincronizarServidor()
    } catch (e: Exception) {
        Log.i(TAG, "Sen servidor Notazas accesible (ou sen sesión): só BoxAbalar", e)
        null
    }
    if (servidor != null) {
        partes.add("Servidor: ${servidor.mensaxe}")
        if (!servidor.ok) todoOk = false
        // O servidor xa fusionou: tráese a caché completa (non pisa o que quede pendente).
        prepararParaForaDoColexio(contexto)
    } else {
        partes.add("Servidor: non accesible, só se sincronizou con BoxAbalar.")
    }
    // Avisos de aniversario: as datas saen dos snapshots de BoxAbalar que acaban de sincronizarse
    // (non do servidor), polo que se refrescan sempre, haxa ou non servidor.
    try {
        if (com.notazas.mobil.datos.cumpleanos.AxustesCumpleanos.ler(contexto).activado) {
            com.notazas.mobil.datos.cumpleanos.AlmacenCumpleanos.actualizarDesdeCache(contexto)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Non se puido refrescar a lista de aniversarios", e)
    }
    return ResultadoSincronizacionTotal(partes.joinToString("\n"), todoOk)
}

/** Resultado de "Preparar para fóra do colexio", para amosar na UI. */
sealed class ResultadoPreparacion {
    data class Ok(val numAsignacions: Int) : ResultadoPreparacion()
    data class Erro(val mensaxe: String) : ResultadoPreparacion()
}

/**
 * Chámase SÓ estando en liña (coa API normal do colexio ou da casa):
 * pide os snapshots xa feitos polo servidor (/nextcloud/cache/...) e
 * gárdaos en local cifrados, para que ActitudeViewModel/DestrezasViewModel
 * poidan seguir lendo e escribindo cando despois non haxa NINGÚN servidor
 * accesible. É a vía MÁIS COMPLETA (le a base de datos do servidor
 * directamente), pero só funciona cando se chega a ese servidor.
 */
suspend fun prepararParaForaDoColexio(contexto: Context): ResultadoPreparacion {
    return try {
        val asignacions = ApiClient.servizo.verCacheAsignacions()
        val centro = ApiClient.servizo.verCacheCentro()
        val aspectos = ApiClient.servizo.listarAspectosActitude()

        // Non se pisan as asignacións con cambios locais aínda sen subir a
        // BoxAbalar: se non, perderíase ese traballo feito sen conexión.
        val pendentes = CacheOffline.listarPendentesIds(contexto)
        asignacions.filter { it.asignacion_id !in pendentes }.forEach { CacheOffline.gardarAsignacion(contexto, it) }
        CacheOffline.gardarCentro(contexto, centro)
        CacheOffline.gardarAspectos(contexto, aspectos)

        ResultadoPreparacion.Ok(asignacions.size)
    } catch (e: Exception) {
        ResultadoPreparacion.Erro(
            "Non se puido preparar: ${e.message ?: "erro descoñecido"}. Tes que estar conectado a un servidor Notazas (cole ou casa) e ter configurado o teu BoxAbalar persoal."
        )
    }
}

/**
 * Vía alternativa cando NON hai ningún servidor local accesible (nin
 * cole nin casa): le os snapshots directamente de BoxAbalar e
 * actualiza a caché local se hai unha versión máis nova — por
 * exemplo, porque o PC fixo cambios e os subiu (botón "Sincronizar
 * agora" ou ao pechar sesión) despois da última vez que o móbil
 * estivo en rede local.
 *
 * NUNCA sobrescribe unha asignación que teña cambios locais sen
 * subir aínda (está na lista de "pendentes"): iso perdería ese
 * traballo. Nese caso, esa asignación en concreto quédase coa súa
 * versión local ata que se sincronice.
 */
suspend fun actualizarCacheOfflineDirectoDeBoxAbalar(contexto: Context): Boolean {
    val credenciais = CredenciaisRemotas.cargar() ?: return false
    val persoal = credenciais.nextcloudPersoal
    var algunhaActualizada = false

    if (persoal != null) {
        val pendentes = CacheOffline.listarPendentesIds(contexto)
        val cliente = WebDavClient(persoal.urlServidor, persoal.usuario, persoal.token)
        for (entrada in CacheOffline.listarAsignacionsCacheadas(contexto)) {
            if (entrada.asignacion_id in pendentes) continue // non pisar cambios locais sen subir
            try {
                val remoto = cliente.ler(entrada.ruta_webdav) ?: continue
                val snapshotRemoto = json.decodeFromString(
                    SnapshotAsignacion.serializer(),
                    FernetCifrado.descifrar(remoto, credenciais.notazasFieldKey)
                )
                if (snapshotRemoto.modificado_en > entrada.snapshot.modificado_en) {
                    CacheOffline.gardarAsignacion(contexto, entrada.copy(snapshot = snapshotRemoto))
                    algunhaActualizada = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Non se puido refrescar '${entrada.ruta_webdav}' directo de BoxAbalar", e)
            }
        }
    }

    val centroActual = CacheOffline.lerCentro(contexto)
    if (centroActual != null) {
        try {
            val clienteCentro = WebDavClient(credenciais.urlServidorCentro, credenciais.usuarioCentro, credenciais.tokenCentro)
            val remoto = clienteCentro.ler(centroActual.ruta_webdav)
            if (remoto != null) {
                val snapshotRemoto = json.decodeFromString(
                    SnapshotCentro.serializer(),
                    FernetCifrado.descifrar(remoto, credenciais.notazasFieldKey)
                )
                if (snapshotRemoto.modificado_en > centroActual.snapshot.modificado_en) {
                    CacheOffline.gardarCentro(contexto, centroActual.copy(snapshot = snapshotRemoto))
                    algunhaActualizada = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Non se puido refrescar centro.json directo de BoxAbalar", e)
        }
    }
    return algunhaActualizada
}

/** Punto de entrada único: proba primeiro a v\u00eda completa (servidor
 * local, se se chega a el) e, se non, a directa contra BoxAbalar. */
suspend fun actualizarCacheOffline(contexto: Context) {
    val resultado = prepararParaForaDoColexio(contexto)
    if (resultado is ResultadoPreparacion.Erro) {
        try {
            actualizarCacheOfflineDirectoDeBoxAbalar(contexto)
        } catch (e: Exception) {
            Log.w(TAG, "Non se puido actualizar a cach\u00e9 offline directo de BoxAbalar", e)
        }
    }
}
