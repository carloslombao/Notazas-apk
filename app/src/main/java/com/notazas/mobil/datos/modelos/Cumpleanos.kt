package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

/** Alumno cunha data de nacemento (GET /alumnos/cumpleanos), para os avisos
 * de aniversario. A data vai en formato AAAA-MM-DD. Gárdase cifrada só no
 * móbil: nunca se mete nos snapshots de BoxAbalar. */
@Serializable
data class CumpleanosOut(
    val alumno_id: Int,
    val nome: String,
    val apelidos: String,
    val grupo: String,
    val data_nacemento: String
)
