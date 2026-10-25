package com.notazas.mobil.datos.actualizacion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.example.autoupdater.AutoUpdaterManager
import com.example.autoupdater.UpdateFeatures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// AutoUpdaterManager e UpdateFeatures non están en `app/src`: viven dentro da librería (paquete
// com.example.autoupdater), que Gradle descarga de JitPack (External Libraries > AutoUpdater:1.0.1).

/**
 * Actualización da app por APK usando AutoUpdater (CSAbhiOnline).
 *
 *  - O JSON remoto ({"latest_version","changelog","apk_url"}) e o APK publícanos o workflow de
 *    GitHub Actions (.github/workflows/build-apk.yml) como assets do último release.
 *  - A librería compara `latest_version` co `versionName` instalado POR IGUALDADE: se difiren,
 *    ofrece actualizar (por iso o CI xera a versión e o JSON a partir do mesmo valor).
 *  - Android NON permite instalar sen confirmación: ao rematar a descarga a librería abre o
 *    instalador do sistema e o usuario toca "Instalar".
 */
object ActualizadorApp {

    /** Repositorio PÚBLICO de GitHub. Se o repo non se chama "Notazas-apk", cambia o nome aquí. */
    const val URL_JSON = "https://github.com/carloslombao/Notazas-apk/releases/latest/download/update.json"

    private const val TAG = "ActualizadorApp"

    sealed interface Resultado {
        object SenActualizacion : Resultado
        data class Dispoñible(val info: UpdateFeatures) : Resultado
        data class Erro(val mensaxe: String) : Resultado
    }

    /** versionName instalado (o que se amosa no botón e o que compara a librería). */
    fun versionActual(contexto: Context): String =
        contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName ?: "?"

    /** Android 8+: cada app necesita permiso propio para instalar APK (orixes descoñecidas). */
    fun podeInstalar(contexto: Context): Boolean =
        contexto.packageManager.canRequestPackageInstalls()

    /** Pantalla do sistema "Instalar apps descoñecidas" desta app. */
    fun intentPermisoInstalacion(contexto: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${contexto.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Le o JSON remoto fóra do fío principal. Nunca lanza: devolve [Resultado.Erro]. */
    suspend fun buscar(contexto: Context): Resultado = withContext(Dispatchers.IO) {
        try {
            val nova = AutoUpdaterManager(contexto).checkForUpdate(JSONfileURL = URL_JSON)
            if (nova == null) Resultado.SenActualizacion else Resultado.Dispoñible(nova)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Fallou a comprobación de actualizacións", e)
            Resultado.Erro("Non se puido comprobar (sen conexión, ou o repositorio non é público).")
        }
    }

    /**
     * Descarga o APK (progreso 0..100) e a librería lanza o instalador do sistema ao rematar.
     * Devolve null se foi ben, ou a mensaxe de erro.
     */
    suspend fun descargarEInstalar(contexto: Context, info: UpdateFeatures, onProgreso: (Int) -> Unit): String? =
        withContext(Dispatchers.IO) {
            try {
                // O README esixe un apkName sen símbolos.
                val nome = "Notazas" + info.latestversion.filter { it.isLetterOrDigit() }
                AutoUpdaterManager(contexto).downloadapk(contexto, info.apk_url, nome) { onProgreso(it) }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fallou a descarga ou a instalación", e)
                "A descarga fallou. Inténtao de novo máis tarde."
            }
        }
}
