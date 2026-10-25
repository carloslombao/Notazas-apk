package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

@Serializable
data class TitorOut(
    val relacion: String,
    val nome: String,
    val email: String? = null,
    val telefono: String? = null
)

@Serializable
data class FamiliaOut(
    val nai: TitorOut? = null,
    val pai: TitorOut? = null
)

@Serializable
data class MateriaEvolucionOut(
    val materia: String,
    val asignacion_id: Int,
    val por_trimestre: Map<String, Double?> = emptyMap()
)

@Serializable
data class DetalleActitudeOut(
    val aspecto_id: Int,
    val valor: Int
)

@Serializable
data class ActitudeSemanalOut(
    val id: Int,
    val alumno_id: Int,
    val trimestre_id: Int,
    val semana: Int,
    val media: Double? = null,
    val observacions: String? = null,
    val detalles: List<DetalleActitudeOut> = emptyList()
)

@Serializable
data class AspectoActitudeOut(
    val id: Int,
    val nome: String,
    val orde: Int = 0
)

@Serializable
data class FicheiroAlumnoOut(
    val id: Int,
    val tipo: String,
    val data_subida: String
)

@Serializable
data class PerfilAlumnoOut(
    val id: Int,
    val nome: String,
    val apelidos: String,
    val data_nacemento: String? = null,
    val grupo: String,
    val familia: FamiliaOut,
    val evolucion: List<MateriaEvolucionOut>,
    val ficheiros: List<FicheiroAlumnoOut> = emptyList(),
    val ten_foto: Boolean = false
)
