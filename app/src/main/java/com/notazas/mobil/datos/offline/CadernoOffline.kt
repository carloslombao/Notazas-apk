package com.notazas.mobil.datos.offline

import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.DetalleActitudeOut
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.OpcionRecuperacion
import com.notazas.mobil.datos.modelos.PonderacionOut
import com.notazas.mobil.datos.modelos.RecuperacionInfo
import com.notazas.mobil.datos.util.ordenadosPorNome
import kotlin.math.round

/**
 * Lóxica pura do Caderno en modo "fóra do colexio": constrúe, a partir
 * do snapshot dunha asignación, o MESMO que devolve o servidor en
 * GET /gradebook/{asignacion}/{trimestre} (columnas, ponderación e
 * unha fila por alumno coas notas e a NOTA FINAL calculada), e permite
 * gardar notas e criterios de recuperación directamente sobre o
 * snapshot. Replica calculo_notas.py do backend — se alí cambia a
 * fórmula, hai que cambiala aquí.
 *
 * Ids sintéticos: columna = hash da súa CHAVE (uid; ver ColumnasOffline.kt — o nome
 * non é único), alumno = idAlumnoOffline().
 */

data class CadernoOfflineDatos(
    val columnas: List<ColumnaOut>,
    val ponderacion: PonderacionOut?,
    val filas: List<FilaAlumnoGradebook>
)

/** "2026-10-18T09:27:15.134" (UTC, do servidor) -> "18/10 11:27" na hora do móvil. */
fun etiquetaDatosOffline(iso: String?): String? {
    if (iso.isNullOrBlank() || iso.length < 19) return null
    return try {
        val entrada = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val saida = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
        saida.format(entrada.parse(iso.take(19))!!)
    } catch (e: Exception) {
        null
    }
}

private val TIPOS_VISIBLES = setOf("exame", "traballo", "destrezas")

private fun redondear2(v: Double): Double = round(v * 100.0) / 100.0

/** A recuperación `r` é a elixida pola nota (por uid; sen uid, polo nome). */
private fun eAElixida(nota: SnapshotNota, r: SnapshotColumna): Boolean =
    if (!nota.columna_recuperacion_elixida_uid.isNullOrEmpty()) r.uid == nota.columna_recuperacion_elixida_uid
    else r.nome == nota.columna_recuperacion_elixida

/** Valor efectivo dun exame para un alumno, tendo en conta a
 * recuperación elixida (mesma lóxica ca _valor_efectivo_exame). */
private fun valorEfectivoExame(
    notaOrixinal: SnapshotNota?,
    recuperacions: List<SnapshotColumna>,
    notaDe: (SnapshotColumna) -> SnapshotNota?
): Double? {
    val valorOrix = notaOrixinal?.valor
    val criterio = notaOrixinal?.criterio_recuperacion
    if (recuperacions.isEmpty() || notaOrixinal == null || criterio.isNullOrEmpty() || criterio == "orixinal") return valorOrix

    val elixida = recuperacions.firstOrNull { eAElixida(notaOrixinal, it) } ?: return valorOrix
    val valorRecup = notaDe(elixida)?.valor ?: return valorOrix
    return when (criterio) {
        "recuperacion" -> valorRecup
        "media" -> if (valorOrix == null) valorRecup else redondear2((valorOrix + valorRecup) / 2)
        else -> valorOrix
    }
}

/** Media semanal (base 10) coma calcular_media_semana_actitude. */
private fun mediaSemanaActitude(valores: Collection<Int>): Double? {
    if (valores.isEmpty()) return null
    return redondear2((valores.sum().toDouble() / valores.size / 5.0) * 10.0)
}

fun construirCadernoOffline(entrada: CacheAsignacionEntrada, trimestreNome: String): CadernoOfflineDatos {
    val trimestre = entrada.snapshot.trimestres[trimestreNome] ?: SnapshotTrimestre()
    val todasColumnas = trimestre.columnas
    val columnasSnap = todasColumnas.filter { it.tipo in TIPOS_VISIBLES }
    val recuperacionsSnap = todasColumnas.filter { it.tipo == "recuperacion" }

    // (alumno, chave da columna) -> nota. A columna da nota resólvese polo uid (sen uid, polo
    // nome): así un exame "U1" e un traballo "U1" non pisan as notas un do outro.
    val notasPor = HashMap<Pair<String, String>, SnapshotNota>()
    trimestre.notas.forEach { n ->
        val i = n.indiceColumna(todasColumnas)
        if (i >= 0) notasPor[n.alumno to todasColumnas[i].chave()] = n
    }

    val ponderacion = trimestre.ponderacion?.let {
        PonderacionOut(it.pct_exames ?: 0.0, it.pct_traballos ?: 0.0, it.pct_actitude ?: 0.0)
    }
    val tenTraballos = columnasSnap.any { it.tipo == "traballo" }
    val xadePor = trimestre.xade.associate { it.alumno to it.valor }

    val filas = entrada.snapshot.alumnado.map { a ->
        val clave = claveAlumno(a.nome, a.apelidos)
        val notas = mutableMapOf<String, Double?>()
        val recuperacions = mutableMapOf<String, RecuperacionInfo>()

        columnasSnap.forEach { col ->
            val id = idColumnaOffline(col)
            val notaOrix = notasPor[clave to col.chave()]
            if (col.tipo == "exame") {
                // Recuperacións deste exame: polo uid orixinal (sen uid, polo nome entre os exames).
                val recups = recuperacionsSnap.filter { r ->
                    val ix = indiceExameOrixinal(todasColumnas, r.columna_orixinal_uid, r.columna_orixinal)
                    ix >= 0 && todasColumnas[ix].chave() == col.chave()
                }
                notas[id.toString()] = valorEfectivoExame(notaOrix, recups) { notasPor[clave to it.chave()] }
                if (recups.isNotEmpty()) {
                    recuperacions[id.toString()] = RecuperacionInfo(
                        valor_orixinal = notaOrix?.valor,
                        criterio = notaOrix?.criterio_recuperacion?.takeIf { it.isNotEmpty() } ?: "orixinal",
                        columna_elixida_id = notaOrix?.let { n -> recups.firstOrNull { eAElixida(n, it) } }?.let { idColumnaOffline(it) },
                        opcions = recups.map { r ->
                            OpcionRecuperacion(idColumnaOffline(r), r.nome, notasPor[clave to r.chave()]?.valor)
                        }
                    )
                }
            } else {
                notas[id.toString()] = notaOrix?.valor
            }
        }

        val mediaExames = columnasSnap.filter { it.tipo == "exame" || it.tipo == "destrezas" }
            .mapNotNull { notas[idColumnaOffline(it).toString()] }
            .let { if (it.isEmpty()) null else it.average() }
        val mediaTraballos = if (tenTraballos) {
            columnasSnap.filter { it.tipo == "traballo" }
                .mapNotNull { notas[idColumnaOffline(it).toString()] }
                .let { if (it.isEmpty()) null else it.average() }
        } else null
        val mediaActitude = trimestre.actitude.filter { it.alumno == clave }
            .mapNotNull { mediaSemanaActitude(it.valores.values) }
            .let { if (it.isEmpty()) null else it.average() }

        var notaFinal: Double? = null
        if (ponderacion != null) {
            val pctExames = if (tenTraballos) ponderacion.pct_exames else ponderacion.pct_exames + ponderacion.pct_traballos
            val pctTraballos = if (tenTraballos) ponderacion.pct_traballos else 0.0
            var acumulado = 0.0
            var totalPct = 0.0
            listOf(mediaExames to pctExames, mediaTraballos to pctTraballos, mediaActitude to ponderacion.pct_actitude)
                .forEach { (media, pct) ->
                    if (media != null && pct > 0) {
                        acumulado += media * pct
                        totalPct += pct
                    }
                }
            if (totalPct > 0) notaFinal = redondear2(acumulado / totalPct)
        }

        FilaAlumnoGradebook(
            alumno_id = idAlumnoOffline(a.nome, a.apelidos),
            nome_completo = "${a.apelidos}, ${a.nome}",
            nota_final = notaFinal,
            actitude_media = mediaActitude,
            xade = xadePor[clave],
            notas = notas,
            recuperacions = recuperacions
        )
    }.ordenadosPorNome { it.nome_completo }

    // comoColumnaOut inclúe as palabras das destrezas (necesarias para editar
    // o desglose); a data engádese á parte.
    val columnas = columnasSnap.map { it.comoColumnaOut(entrada.asignacion_id).copy(data = it.data) }
    return CadernoOfflineDatos(columnas, ponderacion, filas)
}

/** Semanas de actitude dun alumno nun trimestre, lidas do snapshot, co
 * mesmo formato que devolve o servidor (a media calcúlase despois con
 * mediaSobre10()). */
fun actitudeAlumnoOffline(
    entrada: CacheAsignacionEntrada, trimestreNome: String, alumno: SnapshotAlumnoBase,
    aspectos: List<AspectoActitudeOut>
): List<ActitudeSemanalOut> {
    val clave = claveAlumno(alumno.nome, alumno.apelidos)
    val idAlumno = idAlumnoOffline(alumno.nome, alumno.apelidos)
    val aspectoPorNome = aspectos.associateBy { it.nome }
    return entrada.snapshot.trimestres[trimestreNome]?.actitude.orEmpty()
        .filter { it.alumno == clave }
        .sortedBy { it.semana }
        .map { s ->
            ActitudeSemanalOut(
                id = 0, alumno_id = idAlumno, trimestre_id = 0, semana = s.semana,
                media = null, observacions = s.observacions,
                detalles = s.valores.mapNotNull { (nome, valor) ->
                    aspectoPorNome[nome]?.let { DetalleActitudeOut(it.id, valor) }
                }
            )
        }
}

fun alumnoOfflinePorId(entrada: CacheAsignacionEntrada, id: Int): SnapshotAlumnoBase? =
    entrada.snapshot.alumnado.firstOrNull { idAlumnoOffline(it.nome, it.apelidos) == id }

private fun CacheAsignacionEntrada.conTrimestre(trimestreNome: String, novo: SnapshotTrimestre) = copy(
    snapshot = snapshot.copy(
        trimestres = snapshot.trimestres + (trimestreNome to novo),
        modificado_en = horaIsoUtcEstiloPython()
    )
)

/** Garda (ou borra, con valor null) a nota dun alumno nunha columna, conservando o
 * criterio de recuperación que xa tivese. A nota pertence á columna POLO SEU UID (non
 * polo nome) e queda gravada con `columna_uid`. */
fun gardarNotaOffline(
    entrada: CacheAsignacionEntrada, trimestreNome: String, columna: SnapshotColumna,
    alumnoNome: String, alumnoApelidos: String, valor: Double?
): CacheAsignacionEntrada {
    val trimestre = entrada.snapshot.trimestres[trimestreNome] ?: SnapshotTrimestre()
    val clave = claveAlumno(alumnoNome, alumnoApelidos)
    val existente = trimestre.notaDe(clave, columna)
    val resto = trimestre.notasSenEsta(clave, columna)
    val ahora = horaIsoUtcEstiloPython()

    val nova = when {
        // Cada edición leva a súa data (v2): é o que permite fusionar nota a nota.
        // Borrar unha nota existente conserva a fila con valor null e data: se se
        // quitase a fila, a nota remota volvería a aparecer ao fusionar.
        existente != null -> existente.copy(
            valor = valor, modificado_en = ahora, columna = columna.nome, columna_uid = columna.uid
        )
        valor != null -> SnapshotNota(
            alumno = clave, columna = columna.nome, columna_uid = columna.uid, valor = valor, modificado_en = ahora
        )
        else -> return entrada // nada que borrar e nada que gardar
    }
    return entrada.conTrimestre(trimestreNome, trimestre.copy(notas = resto + nova))
}

/** Garda o criterio de recuperación dun exame ("orixinal",
 * "recuperacion" ou "media") para un alumno. `elixida` é a columna de recuperación
 * escollida (null para "orixinal"); gárdase o seu nome E o seu uid. */
fun gardarCriterioRecuperacionOffline(
    entrada: CacheAsignacionEntrada, trimestreNome: String, exame: SnapshotColumna,
    alumnoNome: String, alumnoApelidos: String, criterio: String, elixida: SnapshotColumna?
): CacheAsignacionEntrada {
    val trimestre = entrada.snapshot.trimestres[trimestreNome] ?: SnapshotTrimestre()
    val clave = claveAlumno(alumnoNome, alumnoApelidos)
    val existente = trimestre.notaDe(clave, exame)
        ?: SnapshotNota(alumno = clave, columna = exame.nome, columna_uid = exame.uid, valor = null)
    val elixidaFinal = if (criterio == "orixinal") null else elixida
    val actualizada = existente.copy(
        columna = exame.nome, columna_uid = exame.uid,
        criterio_recuperacion = criterio,
        columna_recuperacion_elixida = elixidaFinal?.nome,
        columna_recuperacion_elixida_uid = elixidaFinal?.uid,
        modificado_en = horaIsoUtcEstiloPython()
    )
    return entrada.conTrimestre(trimestreNome, trimestre.copy(notas = trimestre.notasSenEsta(clave, exame) + actualizada))
}
