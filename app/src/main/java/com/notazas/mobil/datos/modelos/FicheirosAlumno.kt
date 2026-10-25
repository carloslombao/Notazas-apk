package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

/** Elemento de GET /traballos/alumno/{id}: todos os ficheiros do
 * alumno (fotos de traballos e PDFs de exames) co id da columna á
 * que van ligados. */
@Serializable
data class FicheiroColumnaOut(
    val id: Int,
    val tipo: String,
    val columna_id: Int? = null,
    val data_subida: String = ""
)
