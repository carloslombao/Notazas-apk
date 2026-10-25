package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

// Columnas do gradebook (só os campos que usa a app) e carga de fotos
// de traballos (schemas/gradebook.py e schemas/traballos.py do backend).

@Serializable
data class PalabraOut(
    val id: Int,
    val texto: String,
    val orde: Int
)

@Serializable
data class ColumnaOut(
    val id: Int,
    val asignacion_id: Int,
    val trimestre_id: Int,
    val tipo: String,
    val nome: String,
    val data: String? = null,
    val columna_orixinal_id: Int? = null,
    val pct_listening: Double? = null,
    val pct_reading: Double? = null,
    val pct_speaking: Double? = null,
    val palabras: List<PalabraOut> = emptyList()
)

/** Corpo de POST /gradebook/{asignacionId}/columnas. Para un traballo
 * a web só envía tipo, nome e data (opcional, formato AAAA-MM-DD); para
 * unha Proba Destrezas, ademáis, a lista de palabras e a ponderación
 * (as tres deben sumar 100; a app non ten formato vello); para unha
 * recuperación, columna_orixinal_id co id do exame orixinal. */
@Serializable
data class ColumnaCreate(
    val trimestre_id: Int,
    val tipo: String,
    val nome: String,
    val data: String? = null,
    val columna_orixinal_id: Int? = null,
    val palabras: List<String>? = null,
    val pct_listening: Double? = null,
    val pct_reading: Double? = null,
    val pct_speaking: Double? = null
)

@Serializable
data class FicheiroSubidoOut(
    val nome_orixinal: String,
    val qr_atopado: Boolean,
    val alumno_id: Int? = null,
    val nome_alumno: String? = null,
    val ficheiro_id: Int? = null,
    val pendente_id: String? = null
)

@Serializable
data class CargaTraballosOut(
    val resultados: List<FicheiroSubidoOut>
)

@Serializable
data class AsignacionManualIn(
    val pendente_id: String,
    val alumno_id: Int,
    val columna_id: Int? = null
)
