package com.notazas.mobil.ui.seguridade

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val AUTENTICADORES =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

fun biometriaDispoñible(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS
}

fun mostrarPromptBiometrico(
    activity: FragmentActivity,
    onExito: () -> Unit,
    onErro: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onExito()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onErro(errString.toString())
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Notazas")
        .setSubtitle("Confirma a túa identidade para continuar")
        .setAllowedAuthenticators(AUTENTICADORES)
        .build()
    prompt.authenticate(promptInfo)
}
