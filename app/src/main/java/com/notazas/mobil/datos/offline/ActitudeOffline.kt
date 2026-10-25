package com.notazas.mobil.datos.offline

import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.DetalleActitudeOut
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.Trimestre

/**
 * Lóxica pura (sen rede, sen corrutinas) para traballar coa Actitude
 * cando non hai NINGÚN servidor accesible: le e escribe directamente
 * sobre o snapshot JSON dunha asignación (o mesmo formato có servidor
 * usa con BoxAbalar — ver nextcloud_client.py). A persistencia e o
 * PUT a BoxAbalar fanse á parte, en RepositorioOffline.kt.
 */

/** Só se usan 3 trimestres para notas (coma online); os nomes teñen
 * que casar EXACTAMENTE cos que xera bootstrap.py no servidor, xa que
 * son a chave do dict `trimestres` do snapshot. Ids negativos: nunca
 * se envían a ningures, só serven para identificar a selección na UI. */
val TRIMESTRES_OFFLINE = listOf(
    Trimestre(id = -1, nome = "1º Trimestre", orde = 1, activo = false),
    Trimestre(id = -2, nome = "2º Trimestre", orde = 2, activo = false),
    Trimestre(id = -3, nome = "3º Trimestre", orde = 3, activo = false)
)

/** Catro aspectos por defecto (bootstrap.py) — só se usan se nunca se
 * preparou a caché de aspectos (debería ser raro, a preparación
 * gárdaos sempre). */
val ASPECTOS_POR_DEFECTO = listOf(
    AspectoActitudeOut(id = -1, nome = "Participación", orde = 0),
    AspectoActitudeOut(id = -2, nome = "Comportamento", orde = 1),
    AspectoActitudeOut(id = -3, nome = "Atención", orde = 2),
    AspectoActitudeOut(id = -4, nome = "Traballo en Clase/Casa", orde = 3)
)

fun CacheAsignacionEntrada.comoAsignacion(): Asignacion = Asignacion(
    id = asignacion_id,
    materia = snapshot.materia,
    curso = snapshot.curso_nome,
    grupo = "${snapshot.curso_nome}${snapshot.grupo_letra}",
    // Sintético: non hai grupo_id real en offline, pero dous alumnos do
    // MESMO grupo (dúas materias distintas) teñen que dar o mesmo valor
    // aquí para que o selector os agrupe coma online.
    grupo_id = "${snapshot.curso_nome}${snapshot.grupo_letra}".hashCode(),
    profesor_nome = "",
    esMina = true
)

/** Id sintético estable para un alumno offline (non hai alumno_id real
 * en local — só nome+apelidos, coma no snapshot). */
fun idAlumnoOffline(nome: String, apelidos: String): Int = claveAlumno(nome, apelidos).hashCode()

fun construirAlumnosOffline(entrada: CacheAsignacionEntrada): List<FilaAlumnoGradebook> =
    entrada.snapshot.alumnado.map { a ->
        FilaAlumnoGradebook(
            alumno_id = idAlumnoOffline(a.nome, a.apelidos),
            nome_completo = "${a.apelidos}, ${a.nome}"
        )
    }

/** As avaliacións da semana elixida, para toda a clase, lidas
 * directamente do snapshot (sen petición ningunha). */
fun lerAvaliacionsOffline(
    entrada: CacheAsignacionEntrada,
    trimestreNome: String,
    semanaCodigo: Int,
    aspectos: List<AspectoActitudeOut>
): Map<Int, ActitudeSemanalOut?> {
    val aspectoIdPorNome = aspectos.associateBy { it.nome }
    val rexistros = entrada.snapshot.trimestres[trimestreNome]?.actitude.orEmpty()
        .filter { it.semana == semanaCodigo }
        .associateBy { it.alumno }

    return entrada.snapshot.alumnado.associate { a ->
        val clave = claveAlumno(a.nome, a.apelidos)
        val id = idAlumnoOffline(a.nome, a.apelidos)
        val rexistro = rexistros[clave]
        id to rexistro?.let {
            ActitudeSemanalOut(
                id = 0, alumno_id = id, trimestre_id = 0, semana = it.semana,
                media = null, observacions = it.observacions,
                detalles = it.valores.mapNotNull { (nomeAspecto, valor) ->
                    aspectoIdPorNome[nomeAspecto]?.let { aspecto -> DetalleActitudeOut(aspecto.id, valor) }
                }
            )
        }
    }
}

/** Devolve o snapshot da asignación xa coa nova avaliación semanal
 * dese alumno (substituída se xa existía) máis o obxecto para
 * amosar na UI, igual có que devolvería o servidor. */
fun aplicarGardadoActitudeOffline(
    entrada: CacheAsignacionEntrada,
    trimestreNome: String,
    alumnoNome: String,
    alumnoApelidos: String,
    semanaCodigo: Int,
    valoresPorAspectoId: Map<Int, Int>,
    observacions: String?,
    aspectos: List<AspectoActitudeOut>
): Pair<CacheAsignacionEntrada, ActitudeSemanalOut> {
    val aspectoNomePorId = aspectos.associateBy { it.id }
    val valoresPorNome = valoresPorAspectoId.mapNotNull { (id, valor) -> aspectoNomePorId[id]?.nome?.let { it to valor } }.toMap()
    val clave = claveAlumno(alumnoNome, alumnoApelidos)
    val idAlumno = idAlumnoOffline(alumnoNome, alumnoApelidos)

    val trimestreActual = entrada.snapshot.trimestres[trimestreNome] ?: SnapshotTrimestre()
    val existente = trimestreActual.actitude.firstOrNull { it.alumno == clave && it.semana == semanaCodigo }
    val ahora = horaIsoUtcEstiloPython()

    // v2: gardar SÓ O QUE CAMBIOU. Cada aspecto leva a data do seu último cambio: se un
    // aspecto non cambia conserva a súa data antiga (se non, un Gardar sen cambios
    // gañaría a calquera cambio feito noutro dispositivo). Idem coas observacións.
    val valoresT: Map<String, String?> = valoresPorNome.mapValues { (nome, valor) ->
        if (existente?.valores?.get(nome) == valor) existente?.valores_t?.get(nome) else ahora
    }
    val observacionsMudaron = (existente?.observacions.orEmpty()) != (observacions.orEmpty())

    val novaSemana = SnapshotActitudeSemana(
        alumno = clave, semana = semanaCodigo, valores = valoresPorNome, valores_t = valoresT,
        observacions = observacions,
        observacions_modificado_en = if (observacionsMudaron) ahora else existente?.observacions_modificado_en
    )
    val actitudeSenEsta = trimestreActual.actitude.filterNot { it.alumno == clave && it.semana == semanaCodigo }
    val trimestreNovo = trimestreActual.copy(actitude = actitudeSenEsta + novaSemana)
    val snapshotNovo = entrada.snapshot.copy(
        trimestres = entrada.snapshot.trimestres + (trimestreNome to trimestreNovo),
        modificado_en = horaIsoUtcEstiloPython()
    )
    val entradaNova = entrada.copy(snapshot = snapshotNovo)

    val saida = ActitudeSemanalOut(
        id = 0, alumno_id = idAlumno, trimestre_id = 0, semana = semanaCodigo,
        media = null, observacions = observacions,
        detalles = valoresPorAspectoId.map { (id, valor) -> DetalleActitudeOut(id, valor) }
    )
    return entradaNova to saida
}
