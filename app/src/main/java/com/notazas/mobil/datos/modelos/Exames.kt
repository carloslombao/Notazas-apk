package com.notazas.mobil.datos.modelos

import kotlinx.serialization.Serializable

// Carga do PDF cos exames escaneados (schemas/exames.py) e edición
// da nota/criterio de recuperación dunha nota solta (schemas/gradebook.py).

@Serializable
data class ResultadoSegmentoExame(
    val alumno_id: Int? = null,
    val nome_alumno: String? = null,
    val nota_lida: Double? = null,
    val incerto: Boolean = false,
    val ficheiro_id: Int? = null,
    val xa_tina_nota: Boolean = false,
    val pendente_id: String? = null
)

@Serializable
data class EscaneoExameOut(
    val resultados: List<ResultadoSegmentoExame>,
    val paxinas_sen_asignar: Int = 0
)

@Serializable
data class AsignacionManualExameIn(
    val pendente_id: String,
    val alumno_id: Int,
    val nota: Double? = null
)

@Serializable
data class NotaSimpleOut(val valor: Double? = null)

/** Corpo de PUT /gradebook/notas. OLLO: o backend sobrescribe sempre
 * `valor`, así que para só cambiar o criterio de recuperación dunha
 * nota xa existente hai que reenviar tamén o seu valor actual (ler
 * primeiro con NotaSimpleOut). */
@Serializable
data class NotaUpdateIn(
    val columna_id: Int,
    val alumno_id: Int,
    val valor: Double? = null,
    val es_recuperacion: Boolean = false,
    val nota_orixinal_id: Int? = null,
    val criterio_recuperacion: String? = null,
    val columna_recuperacion_elixida_id: Int? = null
)

@Serializable
data class NotaOut(
    val id: Int,
    val columna_id: Int,
    val alumno_id: Int,
    val valor: Double? = null,
    val es_recuperacion: Boolean = false
)
