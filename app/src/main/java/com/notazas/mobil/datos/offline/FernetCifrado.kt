package com.notazas.mobil.datos.offline

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Compatible coa saída de app/services/cifrado.py: Fernet (da
 * librería `cryptography` de Python), con clave derivada por
 * PBKDF2-HMAC-SHA256 (480 000 iteracións) a partir da
 * NOTAZAS_FIELD_KEY — o mesmo algoritmo e o mesmo salt por defecto
 * ("notazas-salt-v1") có servidor.
 *
 * OLLO — único punto de risco: se o centro cambiou o salt por
 * variable de ambiente NOTAZAS_FIELD_SALT no servidor (non vén no
 * QR), isto NON vai descifrar nada. Se iso pasa, os erros serán
 * "HMAC non coincide" en todas as lecturas.
 */
object FernetCifrado {
    private const val SALT_POR_DEFECTO = "notazas-salt-v1"
    private const val ITERACIONS = 480_000
    private const val VERSION_FERNET: Byte = 0x80.toByte()

    private fun derivarClave32(pasafrase: String): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pasafrase.toCharArray(), SALT_POR_DEFECTO.toByteArray(Charsets.UTF_8), ITERACIONS, 256)
        return factory.generateSecret(spec).encoded
    }

    /** (signingKey, encryptionKey) — Fernet reparte os 32 bytes da
     * clave derivada en dous anacos de 16.
     *
     * O PBKDF2 de 480 000 iteracións tarda ~0,5-2 s nun móbil: antes facíase en CADA cifrar/
     * descifrar (dúas veces por materia ao sincronizar, no fío principal), o que conxelaba a
     * app. Agora derívase UNHA vez por contrasinal e resérvase en memoria. */
    private val clavesDerivadas = HashMap<String, Pair<ByteArray, ByteArray>>()

    private fun claves(pasafrase: String): Pair<ByteArray, ByteArray> = synchronized(clavesDerivadas) {
        clavesDerivadas.getOrPut(pasafrase) {
            val clave32 = derivarClave32(pasafrase)
            clave32.copyOfRange(0, 16) to clave32.copyOfRange(16, 32)
        }
    }

    fun descifrar(tokenFernet: String, pasafrase: String): String {
        val (signingKey, encryptionKey) = claves(pasafrase)
        val datos = Base64.decode(tokenFernet, Base64.URL_SAFE or Base64.NO_WRAP)
        require(datos.size > 1 + 8 + 16 + 32) { "Token Fernet demasiado curto" }
        require(datos[0] == VERSION_FERNET) { "Versión de Fernet descoñecida" }

        val semHmac = datos.copyOfRange(0, datos.size - 32)
        val hmacRecibido = datos.copyOfRange(datos.size - 32, datos.size)
        val hmacCalculado = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(signingKey, "HmacSHA256"))
            doFinal(semHmac)
        }
        require(hmacCalculado.contentEquals(hmacRecibido)) {
            "O cifrado non coincide coa clave (NOTAZAS_FIELD_KEY incorrecta, ou o servidor usa un NOTAZAS_FIELD_SALT distinto do por defecto)"
        }

        val iv = datos.copyOfRange(9, 25)
        val cifrado = datos.copyOfRange(25, datos.size - 32)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptionKey, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(cifrado), Charsets.UTF_8)
    }

    fun cifrar(texto: String, pasafrase: String): String {
        val (signingKey, encryptionKey) = claves(pasafrase)
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryptionKey, "AES"), IvParameterSpec(iv))
        val cifrado = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))

        val timestamp = ByteBuffer.allocate(8).putLong(System.currentTimeMillis() / 1000).array()
        val semHmac = byteArrayOf(VERSION_FERNET) + timestamp + iv + cifrado
        val hmac = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(signingKey, "HmacSHA256"))
            doFinal(semHmac)
        }
        return Base64.encodeToString(semHmac + hmac, Base64.URL_SAFE or Base64.NO_WRAP)
    }
}
