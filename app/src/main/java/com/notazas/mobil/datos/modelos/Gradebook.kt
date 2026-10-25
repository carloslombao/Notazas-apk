package com.notazas.mobil.datos.modelos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Modelos "magros": só se declaran os campos que a app móbil usa hoxe.
// kotlinx.serialization (con ignoreUnknownKeys = true) ignora o resto
// do JSON que devolve o backend sen dar erro.

@Serializable
data class Asignacion(
    val id: Int,
    val materia: String,
    val curso: String,
    val grupo: String,
    val grupo_id: Int,
    val profesor_nome: String,
    @SerialName("es_miña") val esMina: Boolean
)

@Serializable
data class Trimestre(
    val id: Int,
    val nome: String,
    val orde: Int,
    val activo: Boolean
)

@Serializable
data class OpcionRecuperacion(
    val columna_id: Int,
    val nome: String,
    val valor: Double? = null,
    // PDF escaneado da recuperación (só en liña: o ficheiro vive no servidor)
    val ficheiro_id: Int? = null
)

@Serializable
data class RecuperacionInfo(
    val valor_orixinal: Double? = null,
    val criterio: String = "orixinal",
    val columna_elixida_id: Int? = null,
    val opcions: List<OpcionRecuperacion> = emptyList()
)

@Serializable
data class FilaAlumnoGradebook(
    val alumno_id: Int,
    val nome_completo: String,
    val nota_final: Double? = null,
    val actitude_media: Double? = null,
    val xade: Double? = null,
    val ten_foto: Boolean = false,
    // Chave = id da columna (como texto: as chaves JSON son sempre texto).
    // Valor efectivo (nos exames, ten en conta a recuperación elixida).
    val notas: Map<String, Double?> = emptyMap(),
    // Chave = id da columna de exame (como texto).
    val recuperacions: Map<String, RecuperacionInfo> = emptyMap()
)

@Serializable
data class PonderacionOut(
    val pct_exames: Double = 0.0,
    val pct_traballos: Double = 0.0,
    val pct_actitude: Double = 0.0
)

@Serializable
data class GradebookTrimestreOut(
    val columnas: List<ColumnaOut> = emptyList(),
    val ponderacion: PonderacionOut? = null,
    val filas: List<FilaAlumnoGradebook>
)
