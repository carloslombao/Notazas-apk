package com.notazas.mobil.datos.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.notazas.mobil.datos.modelos.UsuarioSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SesionLocal"
private const val FICHEIRO_PREFS = "sesion_notazas_cifrada"
private const val FICHEIRO_PREFS_VELLO = "sesion_notazas" // sen cifrar — só para a migración

data class SesionGardada(val token: String, val usuario: UsuarioSelector, val servidor: String)

/**
 * Sesión persistida no móbil: para que non haxa que iniciar sesión
 * cada vez que se abre a app. Só se garda UN usuario (o móbil é
 * persoal dun profesor — non é o caso dos PCs compartidos do
 * colexio). O desbloqueo posterior faise con biometría (ver
 * ui/seguridade/Biometria.kt).
 *
 * Usa EncryptedSharedPreferences (clave en Android Keystore) — o
 * token JWT dá acceso completo á conta dese profesor, así que vai
 * cifrado en repouso, igual cós tokens de BoxAbalar en
 * CredenciaisRemotas.kt.
 *
 * Antes disto usábase SharedPreferences normal (en texto plano); a
 * primeira lectura despois de actualizar migra automaticamente
 * calquera sesión xa gardada dese ficheiro vello ao novo cifrado e
 * borra o vello, para non obrigar a ninguén a iniciar sesión de novo.
 */
object SesionLocal {
    private lateinit var appContext: Context

    fun iniciar(context: Context) {
        appContext = context.applicationContext
    }

    private object Claves {
        const val TOKEN = "token"
        const val USUARIO_ID = "usuario_id"
        const val USUARIO_NOME = "usuario_nome"
        const val USUARIO_APELIDOS = "usuario_apelidos"
        const val USUARIO_ROL = "usuario_rol"
        const val USUARIO_AVATAR_COR = "usuario_avatar_cor"
        const val SERVIDOR = "servidor"
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

    /** Copia unha sesión gardada co esquema vello (sen cifrar) ao
     * cifrado, se a hai, e borra o ficheiro vello. Idempotente: se xa
     * non queda nada no ficheiro vello, non fai nada. */
    private fun migrarDendeVelloSeFaiFalta() {
        val prefsVello = appContext.getSharedPreferences(FICHEIRO_PREFS_VELLO, Context.MODE_PRIVATE)
        val tokenVello = prefsVello.getString(Claves.TOKEN, null) ?: return

        val id = if (prefsVello.contains(Claves.USUARIO_ID)) prefsVello.getInt(Claves.USUARIO_ID, -1) else return
        val nome = prefsVello.getString(Claves.USUARIO_NOME, null) ?: return
        val apelidos = prefsVello.getString(Claves.USUARIO_APELIDOS, null) ?: return
        val rol = prefsVello.getString(Claves.USUARIO_ROL, null) ?: return
        val avatarCor = prefsVello.getString(Claves.USUARIO_AVATAR_COR, null) ?: return
        val servidor = prefsVello.getString(Claves.SERVIDOR, null) ?: return

        val ok = prefs().edit()
            .putString(Claves.TOKEN, tokenVello)
            .putInt(Claves.USUARIO_ID, id)
            .putString(Claves.USUARIO_NOME, nome)
            .putString(Claves.USUARIO_APELIDOS, apelidos)
            .putString(Claves.USUARIO_ROL, rol)
            .putString(Claves.USUARIO_AVATAR_COR, avatarCor)
            .putString(Claves.SERVIDOR, servidor)
            .commit()
        prefsVello.edit().clear().commit()
        Log.d(TAG, "migrarDendeVelloSeFaiFalta(): sesión migrada ao almacenamento cifrado, commit=$ok")
    }

    suspend fun gardar(token: String, usuario: UsuarioSelector, servidor: String) = withContext(Dispatchers.IO) {
        val ok = prefs().edit()
            .putString(Claves.TOKEN, token)
            .putInt(Claves.USUARIO_ID, usuario.id)
            .putString(Claves.USUARIO_NOME, usuario.nome)
            .putString(Claves.USUARIO_APELIDOS, usuario.apelidos)
            .putString(Claves.USUARIO_ROL, usuario.rol)
            .putString(Claves.USUARIO_AVATAR_COR, usuario.avatar_cor)
            .putString(Claves.SERVIDOR, servidor)
            .commit() // síncrono: garante que xa está no disco ao rematar
        Log.d(TAG, "gardar(): usuario=${usuario.nome}, servidor=$servidor, commit=$ok")
    }

    suspend fun limpar() = withContext(Dispatchers.IO) {
        prefs().edit().clear().commit()
        Log.d(TAG, "limpar(): sesión borrada")
    }

    suspend fun actualizarServidor(servidor: String) = withContext(Dispatchers.IO) {
        prefs().edit().putString(Claves.SERVIDOR, servidor).commit()
        Log.d(TAG, "actualizarServidor(): $servidor")
    }

    suspend fun cargar(): SesionGardada? = withContext(Dispatchers.IO) {
        migrarDendeVelloSeFaiFalta()

        val p = prefs()
        val token = p.getString(Claves.TOKEN, null)
        Log.d(TAG, "cargar(): token=${token != null}, servidor=${p.getString(Claves.SERVIDOR, null)}")
        if (token == null) return@withContext null

        val id = if (p.contains(Claves.USUARIO_ID)) p.getInt(Claves.USUARIO_ID, -1) else return@withContext null
        val nome = p.getString(Claves.USUARIO_NOME, null) ?: return@withContext null
        val apelidos = p.getString(Claves.USUARIO_APELIDOS, null) ?: return@withContext null
        val rol = p.getString(Claves.USUARIO_ROL, null) ?: return@withContext null
        val avatarCor = p.getString(Claves.USUARIO_AVATAR_COR, null) ?: return@withContext null
        val servidor = p.getString(Claves.SERVIDOR, null) ?: return@withContext null

        SesionGardada(
            token = token,
            usuario = UsuarioSelector(id, nome, apelidos, rol, avatarCor),
            servidor = servidor
        )
    }
}
