package com.notazas.mobil.datos.offline

import kotlinx.serialization.Serializable

// Reflicte EXACTAMENTE a forma dos dicts que xera
// nextcloud_client.py (construir_snapshot_asignacion /
// construir_snapshot_centro, versión filtrada para o móbil en
// /nextcloud/cache/*) — calquera cambio alí ten que reflectirse aquí.

@Serializable
data class SnapshotPonderacion(
    val pct_exames: Double? = null,
    val pct_traballos: Double? = null,
    val pct_actitude: Double? = null,
    // v2: data (UTC naive, isoformat de Python) do último cambio; null = sen data.
    val modificado_en: String? = null
)

@Serializable
data class SnapshotColumna(
    val nome: String,
    val tipo: String,
    val data: String? = null,
    val columna_orixinal: String? = null,
    // v2: identidade estable da columna (sobrevive a cambios de nome) e data do último cambio.
    val uid: String? = null,
    val modificado_en: String? = null,
    // Só cando tipo=="destrezas":
    val palabras: List<String>? = null,
    val pct_listening: Double? = null,
    val pct_reading: Double? = null,
    val pct_speaking: Double? = null
)

@Serializable
data class SnapshotNota(
    val alumno: String,
    val columna: String,
    val valor: Double? = null,
    val es_recuperacion: Boolean = false,
    val criterio_recuperacion: String? = null,
    val columna_recuperacion_elixida: String? = null,
    // v2: data do último cambio da nota.
    val modificado_en: String? = null,
    // Só en notas de columnas tipo=="destrezas" (texto das palabras
    // acertadas en cada destreza, non ids: non hai ids estábeis entre
    // instalacións):
    val acertos_listening: List<String>? = null,
    val acertos_reading: List<String>? = null,
    val acertos_speaking: List<String>? = null,
    // v2: {destreza: {palabra: data do último cambio}} — marcada ou desmarcada.
    val acertos_t: Map<String, Map<String, String>>? = null
)

@Serializable
data class SnapshotActitudeSemana(
    val alumno: String,
    val semana: Int,
    val valores: Map<String, Int> = emptyMap(),
    // v2: data do último cambio de CADA aspecto (null = sen data).
    val valores_t: Map<String, String?> = emptyMap(),
    val observacions: String? = null,
    val observacions_modificado_en: String? = null
)

@Serializable
data class SnapshotXade(
    val alumno: String,
    // null = borrado á man (o servidor tamén o envía con data para propagar o borrado).
    val valor: Double? = null,
    val modificado_en: String? = null
)

@Serializable
data class SnapshotTrimestre(
    val ponderacion: SnapshotPonderacion? = null,
    val columnas: List<SnapshotColumna> = emptyList(),
    val notas: List<SnapshotNota> = emptyList(),
    val actitude: List<SnapshotActitudeSemana> = emptyList(),
    val xade: List<SnapshotXade> = emptyList()
)

@Serializable
data class SnapshotAlumnoBase(
    val nome: String,
    val apelidos: String,
    // AAAA-MM-DD. Vai no snapshot (cifrado enteiro) para os avisos de aniversario sen servidor.
    val data_nacemento: String? = null
)

/** Lápida dunha columna eliminada (v2). */
@Serializable
data class SnapshotBorrado(
    val uid: String? = null,
    val trimestre: String,
    val nome: String,
    val t: String
)

@Serializable
data class SnapshotAsignacion(
    val versao: Int? = null,
    val materia: String,
    val curso_nome: String,
    val ano_academico: String,
    val grupo_letra: String,
    val profesor_email: String? = null,
    val alumnado: List<SnapshotAlumnoBase> = emptyList(),
    val trimestres: Map<String, SnapshotTrimestre> = emptyMap(),
    val xade_final: List<SnapshotXade> = emptyList(),
    val borrados: List<SnapshotBorrado> = emptyList(),
    val modificado_en: String
)

/** Clave "Apelidos||Nome", igual có _alumno_key do servidor. */
fun claveAlumno(nome: String, apelidos: String) = "$apelidos||$nome"

/** Resposta de GET /nextcloud/cache/asignacions, unha entrada por
 * asignación propia (titular ou substituto). */
@Serializable
data class CacheAsignacionEntrada(
    val asignacion_id: Int,
    val ruta_webdav: String,
    val snapshot: SnapshotAsignacion
)

// ---------- centro.json (versión filtrada para o móbil) ----------

@Serializable
data class SnapshotTitor(
    val relacion: String,
    val nome: String,
    val email: String? = null,
    val telefono: String? = null
)

@Serializable
data class SnapshotAlumnoCentro(
    val nome: String,
    val apelidos: String,
    val qr_uid: String,
    val data_nacemento: String? = null,
    val adaptacion_curricular: Boolean = false,
    val activo: Boolean = true,
    val titores: List<SnapshotTitor> = emptyList()
)

@Serializable
data class SnapshotGrupo(val letra: String, val alumnado: List<SnapshotAlumnoCentro> = emptyList())

@Serializable
data class SnapshotCurso(val nome: String, val ano_academico: String, val grupos: List<SnapshotGrupo> = emptyList())

@Serializable
data class SnapshotMateria(val nome: String)

@Serializable
data class SnapshotCentro(
    val cursos: List<SnapshotCurso> = emptyList(),
    val materias: List<SnapshotMateria> = emptyList(),
    val modificado_en: String
)

@Serializable
data class CacheCentroResposta(val ruta_webdav: String, val snapshot: SnapshotCentro)
