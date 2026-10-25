package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

// Notas de Proba Destrezas — só o formato novo (por lista de palabras),
// como as columnas que crea a app (schemas/gradebook.py: DestrezaOut,
// DestrezaPalabrasUpdate).

@Serializable
data class DestrezaOut(
    val valor: Double? = null,
    val acertos_listening: List<Int> = emptyList(),
    val acertos_reading: List<Int> = emptyList(),
    val acertos_speaking: List<Int> = emptyList()
)

@Serializable
data class DestrezaPalabrasUpdate(
    val columna_id: Int,
    val alumno_id: Int,
    val acertos_listening: List<Int>,
    val acertos_reading: List<Int>,
    val acertos_speaking: List<Int>
)
