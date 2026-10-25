package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

// Desglose de notas dun alumno por trimestre (endpoint xa existente
// /alumnos/{id}/notas-detalle/{trimestre}, reutilizado tamén pola
// pantalla do perfil do alumno na web).

@Serializable
data class NotaColumnaDetalleOut(
    val columna_id: Int,
    val nome: String,
    val tipo: String,
    val data: String? = null,
    val valor: Double? = null,
    val recuperado: Boolean = false,
    val ficheiro_id: Int? = null
)

@Serializable
data class MateriaDetalleTrimestreOut(
    val asignacion_id: Int,
    val materia: String,
    val es_mina: Boolean = false,
    val columnas: List<NotaColumnaDetalleOut> = emptyList(),
    val actitude_media: Double? = null,
    val nota_final: Double? = null
)
