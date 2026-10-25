package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

/** Resposta de POST /nextcloud/sync: o servidor fusiona BoxAbalar coa súa
 * base de datos (todas as asignacións do usuario) e devolve un resumo. */
@Serializable
data class SincronizacionServidorOut(
    val ok: Boolean = false,
    val mensaxe: String = ""
)
