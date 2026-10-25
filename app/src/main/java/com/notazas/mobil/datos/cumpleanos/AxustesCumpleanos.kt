package com.notazas.mobil.datos.cumpleanos

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.notazas.mobil.datos.modelos.CumpleanosOut
import com.notazas.mobil.datos.offline.CacheOffline
import com.notazas.mobil.datos.offline.idAlumnoOffline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val TAG = "Cumpleanos"

/** Preferencias do profesor para os avisos de aniversario. */
data class ConfigCumpleanos(
    val activado: Boolean = false,
    val modo: ModoAviso = ModoAviso.MESMO_DIA,
    val hora: Int = 8,
    val minuto: Int = 0
)

/** Configuración dos avisos (non é un dato sensible: SharedPreferences normais). */
object AxustesCumpleanos {
    private const val FICHEIRO = "axustes_cumpleanos"

    fun ler(contexto: Context): ConfigCumpleanos {
        val p = contexto.applicationContext.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE)
        return ConfigCumpleanos(
            activado = p.getBoolean("activado", false),
            modo = runCatching { ModoAviso.valueOf(p.getString("modo", null) ?: "") }.getOrDefault(ModoAviso.MESMO_DIA),
            hora = p.getInt("hora", 8).coerceIn(0, 23),
            minuto = p.getInt("minuto", 0).coerceIn(0, 59)
        )
    }

    fun gardar(contexto: Context, config: ConfigCumpleanos) {
        contexto.applicationContext.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE).edit {
            putBoolean("activado", config.activado)
            putString("modo", config.modo.name)
            putInt("hora", config.hora)
            putInt("minuto", config.minuto)
        }
    }
}

/**
 * Datas de nacemento do alumnado dos meus grupos, GARDADAS CIFRADAS NO
 * MÓBIL (EncryptedSharedPreferences, clave no Android Keystore). Son datos
 * de menores. Saen dos snapshots que xa se sincronizan con BoxAbalar (que
 * van cifrados enteiros e se gardan cifrados no móbil), así que NON dependen
 * do servidor: ver actualizarDesdeCache(). Os avisos len só desta lista,
 * polo que funcionan sen rede.
 */
object AlmacenCumpleanos {
    private const val FICHEIRO = "cumpleanos_notazas"
    private const val CLAVE_LISTA = "lista"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializador = ListSerializer(CumpleanosOut.serializer())

    private fun prefs(contexto: Context) = EncryptedSharedPreferences.create(
        contexto.applicationContext,
        FICHEIRO,
        MasterKey.Builder(contexto.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun cargar(contexto: Context): List<CumpleanosOut> = withContext(Dispatchers.IO) {
        try {
            val texto = prefs(contexto).getString(CLAVE_LISTA, null) ?: return@withContext emptyList()
            json.decodeFromString(serializador, texto)
        } catch (e: Exception) {
            Log.w(TAG, "Non se puido ler a lista de aniversarios", e)
            emptyList()
        }
    }

    suspend fun gardar(contexto: Context, lista: List<CumpleanosOut>) = withContext(Dispatchers.IO) {
        prefs(contexto).edit().putString(CLAVE_LISTA, json.encodeToString(serializador, lista)).commit()
    }

    /** Borra as datas do móbil (ao pechar sesión: non quedan datos de menores). */
    suspend fun borrar(contexto: Context) = withContext(Dispatchers.IO) {
        try {
            prefs(contexto).edit().remove(CLAVE_LISTA).commit()
        } catch (e: Exception) {
            Log.w(TAG, "Non se puido borrar a lista de aniversarios", e)
        }
    }

    /**
     * Reconstrúe a lista a partir dos snapshots de BoxAbalar que xa están gardados no
     * móbil (a caché offline, que se mantén ao día sincronizando con BoxAbalar): NON usa
     * o servidor. Un alumno que estea en varias materias do mesmo grupo sae unha soa vez.
     * Se a caché está baleira (aínda non se sincronizou nunca) non se toca a lista actual.
     * Devolve cantos alumnos teñen data de nacemento.
     */
    suspend fun actualizarDesdeCache(contexto: Context): Int {
        val entradas = CacheOffline.listarAsignacionsCacheadas(contexto)
        if (entradas.isEmpty()) return cargar(contexto).size

        val lista = entradas.flatMap { entrada ->
            val grupo = "${entrada.snapshot.curso_nome}${entrada.snapshot.grupo_letra}"
            entrada.snapshot.alumnado.mapNotNull { a ->
                val data = a.data_nacemento?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CumpleanosOut(idAlumnoOffline(a.nome, a.apelidos), a.nome, a.apelidos, grupo, data)
            }
        }.distinctBy { Triple(it.nome, it.apelidos, it.grupo) }

        gardar(contexto, lista)
        return lista.size
    }
}
