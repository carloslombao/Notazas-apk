package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

// Reflicte exactamente app/schemas/auth.py do backend.

@Serializable
data class UsuarioSelector(
    val id: Int,
    val nome: String,
    val apelidos: String,
    val rol: String,
    val avatar_cor: String
)

@Serializable
data class LoginRequest(
    val usuario_id: Int,
    val contrasinal: String
)

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String = "bearer",
    val usuario: UsuarioSelector
)
