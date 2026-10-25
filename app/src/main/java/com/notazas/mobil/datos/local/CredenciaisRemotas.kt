package com.notazas.mobil.datos.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "CredenciaisRemotas"
private const val FICHEIRO_PREFS = "credenciais_remotas_notazas"

/** Conta de BoxAbalar persoal dun profesor (notas da súa propia
 * asignación). Pode non existir aínda (profesor que non a configurou). */
data class NextcloudPersoalGardado(val urlServidor: String, val usuario: String, val token: String)

data class CredenciaisRemotasGardadas(
    val urlServidorCentro: String,
    val usuarioCentro: String,
    val tokenCentro: String,
    val notazasFieldKey: String,
    val nextcloudPersoal: NextcloudPersoalGardado?
)

/**
 * Credenciais do modo "fóra do colexio" (aprovisionamento por QR, ver
 * aprovisionamento_qr_movil.md): tokens de BoxAbalar (do centro e,
 * opcionalmente, persoais do profesor) e a NOTAZAS_FIELD_KEY mestra de
 * cifrado. Son segredos de maior alcance cós da sesión normal (dan
 * acceso directo aos datos do centro enteiro en BoxAbalar), así que
 * van SEMPRE cifrados — EncryptedSharedPreferences con clave en
 * Android Keystore, nunca en texto plano nin en logs.
 *
 * De momento só se escriben aquí (aprovisionamento por QR); aínda non
 * hai ningunha pantalla que as lea para sincronizar — iso é traballo
 * doutra sesión (modo "fóra do colexio" en si).
 */
object CredenciaisRemotas {
    private lateinit var appContext: Context

    fun iniciar(context: Context) {
        appContext = context.applicationContext
    }

    private object Claves {
        const val URL_SERVIDOR_CENTRO = "url_servidor_centro"
        const val USUARIO_CENTRO = "usuario_centro"
        const val TOKEN_CENTRO = "token_centro"
        const val FIELD_KEY = "notazas_field_key"
        const val PERSOAL_URL = "persoal_url"
        const val PERSOAL_USUARIO = "persoal_usuario"
        const val PERSOAL_TOKEN = "persoal_token"
    }

    private fun prefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            FICHEIRO_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun gardar(credenciais: CredenciaisRemotasGardadas) = withContext(Dispatchers.IO) {
        val edicion = prefs().edit()
            .putString(Claves.URL_SERVIDOR_CENTRO, credenciais.urlServidorCentro)
            .putString(Claves.USUARIO_CENTRO, credenciais.usuarioCentro)
            .putString(Claves.TOKEN_CENTRO, credenciais.tokenCentro)
            .putString(Claves.FIELD_KEY, credenciais.notazasFieldKey)
        val persoal = credenciais.nextcloudPersoal
        if (persoal != null) {
            edicion
                .putString(Claves.PERSOAL_URL, persoal.urlServidor)
                .putString(Claves.PERSOAL_USUARIO, persoal.usuario)
                .putString(Claves.PERSOAL_TOKEN, persoal.token)
        } else {
            edicion.remove(Claves.PERSOAL_URL).remove(Claves.PERSOAL_USUARIO).remove(Claves.PERSOAL_TOKEN)
        }
        val ok = edicion.commit()
        // Nunca loguear os valores en si — só que a operación se fixo.
        Log.d(TAG, "gardar(): commit=$ok, ten conta persoal=${persoal != null}")
    }

    suspend fun limpar() = withContext(Dispatchers.IO) {
        prefs().edit().clear().commit()
        Log.d(TAG, "limpar(): credenciais remotas borradas")
    }

    suspend fun cargar(): CredenciaisRemotasGardadas? = withContext(Dispatchers.IO) {
        val p = prefs()
        val urlCentro = p.getString(Claves.URL_SERVIDOR_CENTRO, null) ?: return@withContext null
        val usuarioCentro = p.getString(Claves.USUARIO_CENTRO, null) ?: return@withContext null
        val tokenCentro = p.getString(Claves.TOKEN_CENTRO, null) ?: return@withContext null
        val fieldKey = p.getString(Claves.FIELD_KEY, null) ?: return@withContext null

        val persoal = p.getString(Claves.PERSOAL_URL, null)?.let { url ->
            val usuarioPersoal = p.getString(Claves.PERSOAL_USUARIO, null)
            val tokenPersoal = p.getString(Claves.PERSOAL_TOKEN, null)
            if (usuarioPersoal != null && tokenPersoal != null) {
                NextcloudPersoalGardado(url, usuarioPersoal, tokenPersoal)
            } else null
        }

        CredenciaisRemotasGardadas(urlCentro, usuarioCentro, tokenCentro, fieldKey, persoal)
    }
}
