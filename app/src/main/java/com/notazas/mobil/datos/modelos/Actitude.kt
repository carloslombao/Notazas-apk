package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

// Corpo do POST /actitude/semanal (schemas/actitude.py do backend).
// A lectura (ActitudeSemanalOut, AspectoActitudeOut) vive en PerfilAlumno.kt.

@Serializable
data class DetalleActitudeIn(
    val aspecto_id: Int,
    val valor: Int
)

@Serializable
data class ActitudeSemanalIn(
    val asignacion_id: Int,
    val alumno_id: Int,
    val trimestre_id: Int,
    val semana: Int,
    val observacions: String? = null,
    val detalles: List<DetalleActitudeIn>
)

/** Media da semana en base 10, calculada dende os detalles (mesma
 * conta có modal da web: media sobre 5 escalada a 10). Null se non
 * hai ningún aspecto avaliado. */
fun ActitudeSemanalOut.mediaSobre10(): Double? =
    if (detalles.isEmpty()) null else detalles.map { it.valor }.average() / 5.0 * 10.0
