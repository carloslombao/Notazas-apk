package com.notazas.mobil.datos.offline

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * FUSIÓN por rexistro de dous snapshots dunha asignación (o LOCAL do
 * móbil e o REMOTO de BoxAbalar), para o modo "fóra do colexio" (sen
 * servidor). Replica backend/app/services/sync_asignacion.py: cada
 * nota, palabra de destrezas (por destreza), item de actitude, XADE e
 * ponderación leva a data do seu último cambio, e quédase coa máis
 * recente de cada un — NUNCA se sobrescribe un ficheiro enteiro.
 * Se alí cambia o algoritmo, hai que cambialo aquí.
 *
 * Regra única (remotoGana): ver a función. Datas: UTC "naive" de
 * Python (datetime.utcnow().isoformat()).
 */

private const val DIAS_MAX_LAPIDAS = 400L
private val DESTREZAS = listOf("listening", "reading", "speaking")
private val MINIMA: LocalDateTime = LocalDateTime.MIN

// LocalDateTime implementa Comparable<ChronoLocalDateTime<*>>, non Comparable<LocalDateTime>:
// maxOrNull()/maxByOrNull() non compilan con el, hai que pasar un Comparator.
private val ORDE_DATAS = Comparator<LocalDateTime> { a, b -> a.compareTo(b) }

// ==================== utilidades ====================

internal fun parseIso(s: String?): LocalDateTime? =
    if (s.isNullOrBlank()) null else try { LocalDateTime.parse(s) } catch (e: Exception) { null }

private fun maximaIso(datas: List<String?>): String? =
    datas.filterNotNull().mapNotNull { s -> parseIso(s)?.let { it to s } }
        .maxWithOrNull(Comparator { a, b -> a.first.compareTo(b.first) })?.second

private fun tenValor(v: Any?): Boolean = when (v) {
    is List<*> -> tenValor(v.firstOrNull())
    is String -> v.isNotBlank()
    else -> v != null
}

/** Decide se o valor REMOTO substitúe o LOCAL. `local`/`remoto` poden
 * ser valores soltos ou listas (tuplas). */
internal fun remotoGana(local: Any?, tLocal: String?, remoto: Any?, tRemoto: String?): Boolean {
    if (local == remoto) return false
    val tl = parseIso(tLocal)
    val tr = parseIso(tRemoto)
    return when {
        tl != null && tr != null -> tr.isAfter(tl) // empate: local
        tr != null -> true
        tl != null -> false
        !tenValor(local) -> true
        !tenValor(remoto) -> false
        else -> true // conflito sen datas: gana BoxAbalar
    }
}

/** Igual ca remotoGana pero para o estado marcada/non dunha palabra:
 * sen datas, "ter valor" = estar marcada en verde. */
private fun palabraMarcadaGanaRemoto(localMarcada: Boolean, tLocal: String?, remotaMarcada: Boolean, tRemoto: String?): Boolean {
    if (localMarcada == remotaMarcada) return false
    val tl = parseIso(tLocal)
    val tr = parseIso(tRemoto)
    return when {
        tl != null && tr != null -> tr.isAfter(tl)
        tr != null -> true
        tl != null -> false
        else -> remotaMarcada
    }
}

// ==================== punto de entrada ====================

fun fusionarSnapshots(local: SnapshotAsignacion, remoto: SnapshotAsignacion, agora: String = horaIsoUtcEstiloPython()): SnapshotAsignacion {
    val lapidas = fusionarLapidas(local.borrados, remoto.borrados)

    // A data de nacemento ven do servidor (o móbil nunca a edita): se BoxAbalar ten unha, gana
    // (tamén se o servidor a corrixiu); se non, consváse a local.
    val remotoPorClave = remoto.alumnado.associateBy { claveAlumno(it.nome, it.apelidos) }
    val clavesVistas = local.alumnado.map { claveAlumno(it.nome, it.apelidos) }.toMutableSet()
    val alumnado = local.alumnado.map { a ->
        val dataRemota = remotoPorClave[claveAlumno(a.nome, a.apelidos)]?.data_nacemento?.takeIf { it.isNotBlank() }
        if (dataRemota != null) a.copy(data_nacemento = dataRemota) else a
    } + remoto.alumnado.filter { clavesVistas.add(claveAlumno(it.nome, it.apelidos)) }

    val nomesTrimestre = (local.trimestres.keys + remoto.trimestres.keys).toList()
    val trimestres = nomesTrimestre.associateWith { nome ->
        fusionarTrimestre(nome, local.trimestres[nome] ?: SnapshotTrimestre(), remoto.trimestres[nome] ?: SnapshotTrimestre(), lapidas)
    }

    return local.copy(
        versao = 2,
        profesor_email = local.profesor_email ?: remoto.profesor_email,
        alumnado = alumnado,
        trimestres = trimestres,
        xade_final = fusionarXade(local.xade_final, remoto.xade_final),
        borrados = lapidas,
        modificado_en = agora
    )
}

// ==================== lápidas ====================

private fun lapidaDe(lapidas: List<SnapshotBorrado>, trimestre: String, uid: String?, nome: String): SnapshotBorrado? =
    lapidas.filter { (uid != null && it.uid == uid) || (it.trimestre == trimestre && it.nome == nome) }
        .maxWithOrNull(Comparator { a, b -> (parseIso(a.t) ?: MINIMA).compareTo(parseIso(b.t) ?: MINIMA) })

private fun fusionarLapidas(locais: List<SnapshotBorrado>, remotas: List<SnapshotBorrado>): List<SnapshotBorrado> {
    val limite = LocalDateTime.now(ZoneOffset.UTC).minusDays(DIAS_MAX_LAPIDAS)
    val resultado = mutableListOf<SnapshotBorrado>()
    for (b in locais + remotas) {
        val t = parseIso(b.t) ?: continue
        if (t.isBefore(limite)) continue
        val i = resultado.indexOfFirst { (b.uid != null && it.uid == b.uid) || (it.trimestre == b.trimestre && it.nome == b.nome) }
        if (i < 0) resultado.add(b)
        else if (t.isAfter(parseIso(resultado[i].t) ?: MINIMA)) resultado[i] = b
    }
    return resultado
}

// ==================== trimestre ====================

private fun ultimaActividade(columna: SnapshotColumna, notas: List<SnapshotNota>): LocalDateTime =
    (listOf(parseIso(columna.modificado_en)) + notas.filter { it.columna == columna.nome }.map { parseIso(it.modificado_en) })
        .filterNotNull().maxWithOrNull(ORDE_DATAS) ?: MINIMA

private fun fusionarTrimestre(
    nomeTrimestre: String, l: SnapshotTrimestre, r: SnapshotTrimestre, lapidas: List<SnapshotBorrado>
): SnapshotTrimestre {
    // ---- Columnas locais borradas (noutra instalación) despois do seu último uso ----
    val colsLocais = l.columnas.filter { c ->
        val lapida = lapidaDe(lapidas, nomeTrimestre, c.uid, c.nome)
        !(lapida != null && ultimaActividade(c, l.notas) < (parseIso(lapida.t) ?: MINIMA))
    }
    val nomesVivos = colsLocais.map { it.nome }.toSet()
    val notasLocais = l.notas.filter { it.columna in nomesVivos }

    // ---- Columnas ----
    val finais = colsLocais.toMutableList()
    // Os índices 0..colsLocais.size-1 de `finais` seguen sendo as columnas locais (só se
    // actualizan no sitio); as columnas novas remotas engadínse ao final.
    val nomeLocalAFinal = mutableMapOf<String, String>()   // nome local orixinal -> nome final
    val remotoAFinal = mutableMapOf<String, String>()      // nome remoto -> nome final
    val ganaronRemotas = mutableSetOf<String>()            // nomes remotos cuxos metadatos gañaron
    val pctCambiadas = mutableSetOf<String>()              // nomes finais con Listening/Reading/Speaking cambiadas

    val actividadeRemota = r.columnas.associate { c ->
        c.nome to ultimaActividade(c, r.notas)
    }

    // Emparellamento: 1) por uid, 2) por nome entre as non emparelladas
    val emparellamento = mutableMapOf<String, Int>() // nome remoto -> índice en `finais`
    val usadas = mutableSetOf<Int>()
    for (c in r.columnas) {
        if (c.uid.isNullOrEmpty()) continue
        val i = finais.indexOfFirst { it.uid == c.uid }.takeIf { it >= 0 && it !in usadas } ?: continue
        emparellamento[c.nome] = i; usadas.add(i)
    }
    for (c in r.columnas) {
        if (c.nome in emparellamento) continue
        val i = finais.indexOfFirst { it.nome == c.nome }.takeIf { it >= 0 && it !in usadas } ?: continue
        if (!c.uid.isNullOrEmpty()) finais[i] = finais[i].copy(uid = c.uid) // adopta a identidade remota
        emparellamento[c.nome] = i; usadas.add(i)
    }

    fun meta(c: SnapshotColumna, nome: String, data: String?): List<Any?> =
        if (c.tipo == "destrezas") listOf(nome, data, c.pct_listening, c.pct_reading, c.pct_speaking) else listOf(nome, data)

    for (c in r.columnas) {
        val i = emparellamento[c.nome]
        if (i == null) {
            val lapida = lapidaDe(lapidas, nomeTrimestre, c.uid, c.nome)
            if (lapida != null && (actividadeRemota[c.nome] ?: MINIMA) < (parseIso(lapida.t) ?: MINIMA)) continue // borrada despois do último uso
            finais.add(c.copy(uid = c.uid ?: UUID.randomUUID().toString().replace("-", "")))
            remotoAFinal[c.nome] = c.nome
            ganaronRemotas.add(c.nome)
            continue
        }

        var col = finais[i]
        val metaLocal = meta(col, col.nome, col.data)
        val metaRemota = meta(c, c.nome, c.data)
        if (remotoGana(metaLocal, col.modificado_en, metaRemota, c.modificado_en)) {
            val pctAntigas = listOf(col.pct_listening, col.pct_reading, col.pct_speaking)
            var nova = col.copy(nome = c.nome, data = c.data, modificado_en = c.modificado_en)
            if (c.tipo == "destrezas" && c.pct_listening != null) {
                nova = nova.copy(pct_listening = c.pct_listening, pct_reading = c.pct_reading, pct_speaking = c.pct_speaking)
                if (pctAntigas != listOf(nova.pct_listening, nova.pct_reading, nova.pct_speaking)) pctCambiadas.add(nova.nome)
            }
            col = nova
            ganaronRemotas.add(c.nome)
        }
        col = col.copy(tipo = c.tipo)

        // Lista de palabras: só se engaden as que faltan (non se edita)
        val palabrasRemotas = c.palabras
        if (c.tipo == "destrezas" && !palabrasRemotas.isNullOrEmpty()) {
            val soLocais = col.palabras.orEmpty().filter { it !in palabrasRemotas }
            col = col.copy(palabras = palabrasRemotas + soLocais)
        }

        finais[i] = col
        remotoAFinal[c.nome] = col.nome
    }

    // Nome local orixinal -> nome final (despois de posibles renomeamentos)
    val nomeFinalPorIndiceLocal = colsLocais.indices.associate { idx -> colsLocais[idx].nome to finais[idx].nome }
    nomeLocalAFinal.clear()
    nomeLocalAFinal.putAll(nomeFinalPorIndiceLocal)

    // 2ª pasada: referencias columna_orixinal (recuperacións)
    for (idx in finais.indices) {
        val col = finais[idx]
        var orixinal = col.columna_orixinal?.let { nomeLocalAFinal[it] ?: it }
        val remotaCorrespondente = r.columnas.firstOrNull { it.nome in ganaronRemotas && remotoAFinal[it.nome] == col.nome }
        if (remotaCorrespondente?.columna_orixinal != null) {
            orixinal = remotoAFinal[remotaCorrespondente.columna_orixinal] ?: orixinal
        }
        if (orixinal != col.columna_orixinal) finais[idx] = col.copy(columna_orixinal = orixinal)
    }

    val columnaPorNome = finais.associateBy { it.nome }

    // ---- Notas ----
    val notas = LinkedHashMap<Pair<String, String>, SnapshotNota>()
    for (n in notasLocais) {
        val nomeFinal = nomeLocalAFinal[n.columna] ?: n.columna
        val elixida = n.columna_recuperacion_elixida?.let { nomeLocalAFinal[it] ?: it }
        notas[n.alumno to nomeFinal] = n.copy(columna = nomeFinal, columna_recuperacion_elixida = elixida)
    }

    for (n in r.notas) {
        val nomeFinal = remotoAFinal[n.columna] ?: continue
        val columna = columnaPorNome[nomeFinal] ?: continue
        val chave = n.alumno to nomeFinal
        val existente = notas[chave]
        val conAcertos = n.acertos_listening != null || n.acertos_reading != null || n.acertos_speaking != null
        notas[chave] = if (columna.tipo == "destrezas" && !columna.palabras.isNullOrEmpty() && conAcertos) {
            fusionarNotaDestreza(columna, existente, n)
        } else {
            fusionarNotaSimple(nomeFinal, existente, n, remotoAFinal)
        }
    }

    // Se a ponderación Listening/Reading/Speaking cambiou (gañou a remota),
    // recalcular TODAS as notas desa columna, tamén as que non veñen no snapshot.
    for (nomeCol in pctCambiadas) {
        val columna = columnaPorNome[nomeCol] ?: continue
        for ((chave, nota) in notas.toList()) {
            if (chave.second != nomeCol) continue
            if (nota.acertos_listening == null && nota.acertos_reading == null && nota.acertos_speaking == null) continue
            notas[chave] = nota.copy(
                valor = valorDestreza(columna, nota.acertos_listening.orEmpty(), nota.acertos_reading.orEmpty(), nota.acertos_speaking.orEmpty()),
                modificado_en = columna.modificado_en
            )
        }
    }

    return SnapshotTrimestre(
        ponderacion = fusionarPonderacion(l.ponderacion, r.ponderacion),
        columnas = finais,
        notas = notas.values.toList(),
        actitude = fusionarActitude(l.actitude, r.actitude),
        xade = fusionarXade(l.xade, r.xade)
    )
}

private fun fusionarPonderacion(l: SnapshotPonderacion?, r: SnapshotPonderacion?): SnapshotPonderacion? {
    if (r == null) return l
    if (l == null) return r
    val local = listOf(l.pct_exames, l.pct_traballos, l.pct_actitude)
    val remota = listOf(r.pct_exames, r.pct_traballos, r.pct_actitude)
    return if (remotoGana(local, l.modificado_en, remota, r.modificado_en)) r else l
}

// ==================== notas ====================

private fun fusionarNotaSimple(
    nomeColumna: String, existente: SnapshotNota?, n: SnapshotNota, remotoAFinal: Map<String, String>
): SnapshotNota {
    val elixidaFinal = n.columna_recuperacion_elixida?.let { remotoAFinal[it] }
    val remota = listOf(n.valor, n.es_recuperacion, n.criterio_recuperacion, elixidaFinal)
    if (existente == null) {
        return n.copy(columna = nomeColumna, columna_recuperacion_elixida = elixidaFinal)
    }
    val local = listOf(existente.valor, existente.es_recuperacion, existente.criterio_recuperacion, existente.columna_recuperacion_elixida)
    return if (remotoGana(local, existente.modificado_en, remota, n.modificado_en)) {
        existente.copy(
            valor = n.valor, es_recuperacion = n.es_recuperacion, criterio_recuperacion = n.criterio_recuperacion,
            columna_recuperacion_elixida = elixidaFinal, modificado_en = n.modificado_en
        )
    } else existente
}

private fun valorDestreza(columna: SnapshotColumna, listening: List<String>, reading: List<String>, speaking: List<String>): Double? =
    notaDestrezaPalabras(
        columna.palabras.orEmpty().size, columna.pct_listening, columna.pct_reading, columna.pct_speaking,
        listening.size, reading.size, speaking.size
    )

/** Nota de Proba Destrezas con lista de palabras: fusión PALABRA A
 * PALABRA e por destreza (cada unha coa súa data); a nota recalcúlase
 * sempre a partir das palabras e das ponderacións da columna. */
private fun fusionarNotaDestreza(columna: SnapshotColumna, existente: SnapshotNota?, n: SnapshotNota): SnapshotNota {
    val palabras = columna.palabras.orEmpty()
    val base = existente ?: SnapshotNota(alumno = n.alumno, columna = columna.nome, modificado_en = n.modificado_en)
    val datasVistas = mutableListOf(base.modificado_en, n.modificado_en)

    val listasNovas = mutableMapOf<String, List<String>>()
    val marcasNovas = mutableMapOf<String, Map<String, String>>()

    for (destreza in DESTREZAS) {
        val locais = when (destreza) { "listening" -> base.acertos_listening; "reading" -> base.acertos_reading; else -> base.acertos_speaking }.orEmpty().toSet()
        val remotas = when (destreza) { "listening" -> n.acertos_listening; "reading" -> n.acertos_reading; else -> n.acertos_speaking }.orEmpty().toSet()
        val tLocais = base.acertos_t?.get(destreza).orEmpty().toMutableMap()
        val tRemotas = n.acertos_t?.get(destreza).orEmpty()
        val novos = locais.toMutableSet()

        for (texto in palabras) {
            val localMarcada = texto in locais
            val remotaMarcada = texto in remotas
            val tl = tLocais[texto]
            val tr = tRemotas[texto]
            if (localMarcada == remotaMarcada) {
                val trParsed = parseIso(tr)
                if (trParsed != null && (parseIso(tl) == null || trParsed.isAfter(parseIso(tl)))) {
                    tLocais[texto] = tr!! // mesmo estado: conserva a data máis recente
                    datasVistas.add(tr)
                }
                continue
            }
            if (palabraMarcadaGanaRemoto(localMarcada, tl, remotaMarcada, tr)) {
                if (remotaMarcada) novos.add(texto) else novos.remove(texto)
                if (!tr.isNullOrEmpty()) {
                    tLocais[texto] = tr
                    datasVistas.add(tr)
                } else {
                    tLocais.remove(texto)
                }
            }
        }
        listasNovas[destreza] = palabras.filter { it in novos } // na orde da proba
        marcasNovas[destreza] = tLocais
    }

    val l = listasNovas.getValue("listening")
    val rd = listasNovas.getValue("reading")
    val s = listasNovas.getValue("speaking")
    val valor = when {
        l.isNotEmpty() || rd.isNotEmpty() || s.isNotEmpty() -> valorDestreza(columna, l, rd, s)
        existente?.valor == null && n.valor != null -> n.valor // snapshot sen acertos pero con nota: non perdela
        else -> null
    }

    return base.copy(
        valor = valor,
        acertos_listening = l, acertos_reading = rd, acertos_speaking = s,
        acertos_t = marcasNovas,
        modificado_en = maximaIso(datasVistas) ?: base.modificado_en
    )
}

// ==================== actitude ====================

private fun fusionarActitude(locais: List<SnapshotActitudeSemana>, remotas: List<SnapshotActitudeSemana>): List<SnapshotActitudeSemana> {
    val mapa = LinkedHashMap<Pair<String, Int>, SnapshotActitudeSemana>()
    locais.forEach { mapa[it.alumno to it.semana] = it }

    for (r in remotas) {
        val chave = r.alumno to r.semana
        val l = mapa[chave] ?: SnapshotActitudeSemana(alumno = r.alumno, semana = r.semana)

        val valores = l.valores.toMutableMap()
        val valoresT = l.valores_t.toMutableMap()
        for ((aspecto, valorR) in r.valores) {
            val tR = r.valores_t[aspecto]
            if (aspecto !in valores) {
                valores[aspecto] = valorR
                valoresT[aspecto] = tR
            } else if (remotoGana(valores[aspecto], valoresT[aspecto], valorR, tR)) {
                valores[aspecto] = valorR
                valoresT[aspecto] = tR
            }
        }

        val obsLocal = l.observacions?.takeIf { it.isNotEmpty() }
        val obsRemota = r.observacions?.takeIf { it.isNotEmpty() }
        var observacions = l.observacions
        var tObs = l.observacions_modificado_en
        if (remotoGana(obsLocal, l.observacions_modificado_en, obsRemota, r.observacions_modificado_en)) {
            observacions = obsRemota
            tObs = r.observacions_modificado_en
        }

        mapa[chave] = l.copy(valores = valores, valores_t = valoresT, observacions = observacions, observacions_modificado_en = tObs)
    }
    return mapa.values.toList()
}

// ==================== XADE ====================

private fun fusionarXade(locais: List<SnapshotXade>, remotos: List<SnapshotXade>): List<SnapshotXade> {
    val mapa = LinkedHashMap<String, SnapshotXade>()
    locais.forEach { mapa[it.alumno] = it }
    for (r in remotos) {
        val l = mapa[r.alumno]
        if (l == null || remotoGana(l.valor, l.modificado_en, r.valor, r.modificado_en)) mapa[r.alumno] = r
    }
    return mapa.values.toList()
}
