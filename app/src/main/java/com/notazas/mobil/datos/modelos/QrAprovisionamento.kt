package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

// Contrato de POST /nextcloud/centro/qr-aprovisionamento/canxear
// (ver aprovisionamento_qr_movil.md). Endpoint SEN autenticación: o
// propio código do QR fai de credencial dun só uso.

@Serializable
data class CanxearQrIn(val codigo: String)

@Serializable
data class NextcloudPersoalOut(
    val url_servidor: String,
    val usuario_nextcloud: String,
    val token: String
)

@Serializable
data class CanxearQrOut(
    val url_servidor: String,
    val usuario_nextcloud: String,
    val token: String,
    val token_acceso_remoto: String,
    val notazas_field_key: String,
    val nextcloud_persoal: NextcloudPersoalOut? = null,
    val nome: String,
    val apelidos: String,
    val email: String? = null,
    val rol: String
)
