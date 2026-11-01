package com.notazas.mobil.datos.offline

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "CacheOffline"
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Caché local (cifrada con EncryptedFile — clave en Android Keystore)
 * dos snapshots de BoxAbalar: o que a app precisa para seguir
 * funcionando cando non hai NINGÚN servidor Notazas alcanzable (ver
 * ModoConexion.kt). Gárdase o snapshot ENTEIRO de cada asignación
 * propia e o de centro.json (versión filtrada), tal como os devolven
 * /nextcloud/cache/asignacions e /nextcloud/cache/centro mentres SI
 * hai conexión — iso faise dende Axustes ("Preparar para fóra do
 * colexio").
 */
object CacheOffline {

    private fun cartafol(contexto: Context): File =
        File(contexto.filesDir, "cache_offline").apply { mkdirs() }

    // A MasterKey (acceso ao Android Keystore) constrúe-se UNHA vez: antes facíase en cada lectura
    // e escritura de cada ficheiro.
    @Volatile private var chaveMestra: MasterKey? = null

    private fun masterKey(contexto: Context): MasterKey =
        chaveMestra ?: synchronized(this) {
            chaveMestra ?: MasterKey.Builder(contexto.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build().also { chaveMestra = it }
        }

    private fun ficheiroCifrado(contexto: Context, nome: String): EncryptedFile {
        val ficheiro = File(cartafol(contexto), nome)
        return EncryptedFile.Builder(
            contexto, ficheiro, masterKey(contexto),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    private suspend fun escribir(contexto: Context, nome: String, contido: String) = withContext(Dispatchers.IO) {
        val ficheiro = File(cartafol(contexto), nome)
        if (ficheiro.exists()) ficheiro.delete() // EncryptedFile esixe que non exista ao crear
        ficheiroCifrado(contexto, nome).openFileOutput().use { it.write(contido.toByteArray(Charsets.UTF_8)) }
    }

    private suspend fun ler(contexto: Context, nome: String): String? = withContext(Dispatchers.IO) {
        val ficheiro = File(cartafol(contexto), nome)
        if (!ficheiro.exists()) return@withContext null
        try {
            ficheiroCifrado(contexto, nome).openFileInput().use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            Log.e(TAG, "Non se puido ler '$nome' da caché offline", e)
            null
        }
    }

    // ---------- Asignacións propias ----------

    private fun nomeFicheiroAsignacion(asignacionId: Int) = "asignacion_$asignacionId.json"

    // O JSON dos snapshots pode ser grande: codifícase/decodifícase DENTRO de withContext(IO),
    // nunca no fío que chama (que a miúdo é o principal).
    suspend fun gardarAsignacion(contexto: Context, entrada: CacheAsignacionEntrada) = withContext(Dispatchers.IO) {
        escribir(contexto, nomeFicheiroAsignacion(entrada.asignacion_id), json.encodeToString(CacheAsignacionEntrada.serializer(), entrada))
    }

    suspend fun lerAsignacion(contexto: Context, asignacionId: Int): CacheAsignacionEntrada? = withContext(Dispatchers.IO) {
        ler(contexto, nomeFicheiroAsignacion(asignacionId))?.let {
            runCatching { json.decodeFromString(CacheAsignacionEntrada.serializer(), it) }.getOrNull()
        }
    }

    suspend fun listarAsignacionsCacheadas(contexto: Context): List<CacheAsignacionEntrada> = withContext(Dispatchers.IO) {
        cartafol(contexto).listFiles { f -> f.name.startsWith("asignacion_") && f.name.endsWith(".json") }
            ?.mapNotNull { f ->
                val id = f.name.removePrefix("asignacion_").removeSuffix(".json").toIntOrNull() ?: return@mapNotNull null
                lerAsignacion(contexto, id)
            }
            .orEmpty()
    }

    // ---------- Centro (alumnado + familias + materias) ----------

    private const val FICHEIRO_CENTRO = "centro.json"

    suspend fun gardarCentro(contexto: Context, resposta: CacheCentroResposta) = withContext(Dispatchers.IO) {
        escribir(contexto, FICHEIRO_CENTRO, json.encodeToString(CacheCentroResposta.serializer(), resposta))
    }

    suspend fun lerCentro(contexto: Context): CacheCentroResposta? = withContext(Dispatchers.IO) {
        ler(contexto, FICHEIRO_CENTRO)?.let {
            runCatching { json.decodeFromString(CacheCentroResposta.serializer(), it) }.getOrNull()
        }
    }

    /** Data da última vez que se preparou a caché (a do centro.json,
     * que sempre se garda xunto coas asignacións). Null se nunca se
     * preparou. */
    suspend fun ultimaPreparacion(contexto: Context): String? = lerCentro(contexto)?.snapshot?.modificado_en

    // ---------- Aspectos de actitude (para que a grella offline sexa
    // idéntica á online, cos mesmos ids) ----------

    private const val FICHEIRO_ASPECTOS = "aspectos_actitude.json"

    suspend fun gardarAspectos(contexto: Context, aspectos: List<com.notazas.mobil.datos.modelos.AspectoActitudeOut>) {
        escribir(contexto, FICHEIRO_ASPECTOS, json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(com.notazas.mobil.datos.modelos.AspectoActitudeOut.serializer()), aspectos
        ))
    }

    suspend fun lerAspectos(contexto: Context): List<com.notazas.mobil.datos.modelos.AspectoActitudeOut>? =
        ler(contexto, FICHEIRO_ASPECTOS)?.let {
            runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(com.notazas.mobil.datos.modelos.AspectoActitudeOut.serializer()), it
                )
            }.getOrNull()
        }

    // ---------- Cambios pendentes de subir a BoxAbalar ----------
    // Lista plana (sen cifrar: só son ids, non datos) dos asignacion_id
    // cun snapshot local máis novo có que hai en BoxAbalar. SharedPreferences
    // + commit() síncrono: queda no disco ao instante, sobrevive a un peche
    // en seco da app.

    private const val PREFS_PENDENTES = "cache_offline_pendentes"
    private const val CLAVE_PENDENTES = "asignacions_pendentes"

    private fun prefsPendentes(contexto: Context) = contexto.getSharedPreferences(PREFS_PENDENTES, Context.MODE_PRIVATE)

    suspend fun marcarPendente(contexto: Context, asignacionId: Int) = withContext(Dispatchers.IO) {
        val prefs = prefsPendentes(contexto)
        val actuais = (prefs.getStringSet(CLAVE_PENDENTES, null) ?: emptySet()).toMutableSet()
        actuais.add(asignacionId.toString())
        prefs.edit().putStringSet(CLAVE_PENDENTES, actuais).commit()
    }

    suspend fun limparPendente(contexto: Context, asignacionId: Int) = withContext(Dispatchers.IO) {
        val prefs = prefsPendentes(contexto)
        val actuais = (prefs.getStringSet(CLAVE_PENDENTES, null) ?: emptySet()).toMutableSet()
        actuais.remove(asignacionId.toString())
        prefs.edit().putStringSet(CLAVE_PENDENTES, actuais).commit()
    }

    suspend fun listarPendentesIds(contexto: Context): Set<Int> = withContext(Dispatchers.IO) {
        (prefsPendentes(contexto).getStringSet(CLAVE_PENDENTES, null) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }
            .toSet()
    }
}
