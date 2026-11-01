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
 * IDENTIDADE DAS COLUMNAS (sección 8 do documento de sincronización): o nome NON
 * é único (pode haber un exame "U1" e un traballo "U1" no mesmo trimestre), así
 * que as columnas identifícanse polo `uid`, nunca por un mapa `nome -> columna`:
 *  - Emparellamento local/remoto: primeiro por `uid`; as que quedan, por NOME E TIPO
 *    (só se algún dos dous lados non ten uid) e adóptase o uid remoto.
 *  - As notas buscan a súa columna por `columna_uid`; só sen uid (snapshot vello), polo nome.
 *  - Unha lápida casa cunha columna só polo `uid` se ambas o teñen; sen uid, por
 *    trimestre + nome + TIPO (e só se a lápida ten tipo). Se non casa, non se borra nada.
 *
 * Regra única (remotoGana): ver a función. Datas: UTC "naive" de Python
 * (datetime.utcnow().isoformat()).
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
    // (tamén se o servidor a corrixiu); se non, consérvase a local.
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

/** Lápida de borrado dunha columna que PODE casar con `uid`/`nome`/`tipo`:
 *  - Se AMBAS teñen uid: só se compara o uid (nunca o nome).
 *  - Se falta o uid nun dos lados: trimestre + nome + tipo, e só se a lápida ten tipo. */
internal fun lapidaCasaColumna(lapida: SnapshotBorrado, trimestre: String, uid: String?, nome: String, tipo: String): Boolean =
    if (!lapida.uid.isNullOrEmpty() && !uid.isNullOrEmpty()) lapida.uid == uid
    else lapida.tipo != null && lapida.trimestre == trimestre && lapida.nome == nome && lapida.tipo == tipo

/** Dúas lápidas refírense á mesma columna (mesma regra: uid se ambas o teñen; senón nome + tipo). */
private fun mesmaLapida(a: SnapshotBorrado, b: SnapshotBorrado): Boolean =
    if (!a.uid.isNullOrEmpty() && !b.uid.isNullOrEmpty()) a.uid == b.uid
    else a.tipo != null && a.tipo == b.tipo && a.trimestre == b.trimestre && a.nome == b.nome

private fun lapidaDe(lapidas: List<SnapshotBorrado>, trimestre: String, uid: String?, nome: String, tipo: String): SnapshotBorrado? =
    lapidas.filter { lapidaCasaColumna(it, trimestre, uid, nome, tipo) }
        .maxWithOrNull(Comparator { a, b -> (parseIso(a.t) ?: MINIMA).compareTo(parseIso(b.t) ?: MINIMA) })

private fun fusionarLapidas(locais: List<SnapshotBorrado>, remotas: List<SnapshotBorrado>): List<SnapshotBorrado> {
    val limite = LocalDateTime.now(ZoneOffset.UTC).minusDays(DIAS_MAX_LAPIDAS)
    val resultado = mutableListOf<SnapshotBorrado>()
    for (b in locais + remotas) {
        val t = parseIso(b.t) ?: continue
        if (t.isBefore(limite)) continue
        val i = resultado.indexOfFirst { mesmaLapida(it, b) }
        if (i < 0) resultado.add(b)
        else if (t.isAfter(parseIso(resultado[i].t) ?: MINIMA)) resultado[i] = b
    }
    return resultado
}

/** Lápida para gardar ao eliminar unha columna (o `tipo` é obrigatorio desde a v2.1). */
fun lapidaDeColumna(trimestre: String, columna: SnapshotColumna, t: String = horaIsoUtcEstiloPython()): SnapshotBorrado =
    SnapshotBorrado(uid = columna.uid, trimestre = trimestre, nome = columna.nome, tipo = columna.tipo, t = t)

// ==================== trimestre ====================

/** Última actividade de cada columna (por posición): o máximo entre a súa `modificado_en` e a das súas notas. */
private fun actividadePorColumna(columnas: List<SnapshotColumna>, notas: List<SnapshotNota>): List<LocalDateTime> {
    val actividade = MutableList(columnas.size) { i -> parseIso(columnas[i].modificado_en) ?: MINIMA }
    for (n in notas) {
        val i = n.indiceColumna(columnas)
        val t = parseIso(n.modificado_en)
        if (i >= 0 && t != null && t.isAfter(actividade[i])) actividade[i] = t
    }
    return actividade
}

private fun fusionarTrimestre(
    nomeTrimestre: String, l: SnapshotTrimestre, r: SnapshotTrimestre, lapidas: List<SnapshotBorrado>
): SnapshotTrimestre {
    // ---- 1. Columnas locais vivas: bórranse só as que casan cunha lápida (uid, ou nome+tipo sen uid)
    //         e non tiveron actividade despois dela. ----
    val actividadeLocal = actividadePorColumna(l.columnas, l.notas)
    val vivasOrixe = l.columnas.indices.filter { i ->
        val c = l.columnas[i]
        val lapida = lapidaDe(lapidas, nomeTrimestre, c.uid, c.nome, c.tipo)
        !(lapida != null && actividadeLocal[i] < (parseIso(lapida.t) ?: MINIMA))
    }
    val novoIndiceLocal = vivasOrixe.withIndex().associate { (nova, orixe) -> orixe to nova }

    // `finais`: os índices 0 until nLocais seguen sendo as columnas locais (actualízanse no sitio);
    // as columnas novas que veñen do remoto engádense ao final.
    val finais = vivasOrixe.map { l.columnas[it] }.toMutableList()
    val nLocais = finais.size

    // ---- 2. Emparellar columnas locais e remotas: uid; despois nome + tipo (adoptando o uid remoto). ----
    val emparellamento = HashMap<Int, Int>() // índice remoto -> índice en `finais`
    val usadas = HashSet<Int>()
    r.columnas.forEachIndexed { ri, c ->
        if (c.uid.isNullOrEmpty()) return@forEachIndexed
        val fi = finais.indexOfFirst { it.uid == c.uid }
        if (fi >= 0 && usadas.add(fi)) emparellamento[ri] = fi
    }
    r.columnas.forEachIndexed { ri, c ->
        if (ri in emparellamento) return@forEachIndexed
        // Nunca só polo nome: tamén o tipo. E só se alguén non ten uid: dúas columnas con uid
        // distinto son columnas distintas aínda que se chamen igual.
        val fi = (0 until nLocais).firstOrNull { i ->
            i !in usadas && finais[i].nome == c.nome && finais[i].tipo == c.tipo &&
                (finais[i].uid.isNullOrEmpty() || c.uid.isNullOrEmpty())
        } ?: return@forEachIndexed
        if (!c.uid.isNullOrEmpty()) finais[fi] = finais[fi].copy(uid = c.uid) // adopta a identidade remota
        usadas.add(fi)
        emparellamento[ri] = fi
    }

    // ---- 3. Metadatos das columnas emparelladas e columnas novas do remoto ----
    val actividadeRemota = actividadePorColumna(r.columnas, r.notas)
    val ganaronRemotas = HashSet<Int>()  // índices remotos cuxos metadatos gañaron
    val pctCambiadas = HashSet<Int>()    // índices finais con Listening/Reading/Speaking cambiadas

    fun meta(c: SnapshotColumna): List<Any?> =
        if (c.tipo == "destrezas") listOf(c.nome, c.data, c.pct_listening, c.pct_reading, c.pct_speaking) else listOf(c.nome, c.data)

    r.columnas.forEachIndexed { ri, c ->
        val fi = emparellamento[ri]
        if (fi == null) {
            // Columna só remota: non se crea se unha lápida (por uid ou nome+tipo) é posterior á súa actividade.
            val lapida = lapidaDe(lapidas, nomeTrimestre, c.uid, c.nome, c.tipo)
            if (lapida != null && actividadeRemota[ri] < (parseIso(lapida.t) ?: MINIMA)) return@forEachIndexed
            finais.add(c.copy(uid = c.uid?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString().replace("-", "")))
            emparellamento[ri] = finais.size - 1
            ganaronRemotas.add(ri)
            return@forEachIndexed
        }

        var col = finais[fi]
        if (remotoGana(meta(col), col.modificado_en, meta(c), c.modificado_en)) {
            val pctAntigas = listOf(col.pct_listening, col.pct_reading, col.pct_speaking)
            var nova = col.copy(nome = c.nome, data = c.data, modificado_en = c.modificado_en)
            if (c.tipo == "destrezas" && c.pct_listening != null) {
                nova = nova.copy(pct_listening = c.pct_listening, pct_reading = c.pct_reading, pct_speaking = c.pct_speaking)
                if (pctAntigas != listOf(nova.pct_listening, nova.pct_reading, nova.pct_speaking)) pctCambiadas.add(fi)
            }
            col = nova
            ganaronRemotas.add(ri)
        }
        col = col.copy(tipo = c.tipo)

        // Lista de palabras: só se engaden as que faltan (non se edita).
        val palabrasRemotas = c.palabras
        if (c.tipo == "destrezas" && !palabrasRemotas.isNullOrEmpty()) {
            val soLocais = col.palabras.orEmpty().filter { it !in palabrasRemotas }
            col = col.copy(palabras = palabrasRemotas + soLocais)
        }
        finais[fi] = col
    }

    // ---- 4. Referencias columna_orixinal das recuperacións: polo uid (sen uid, polo nome entre os exames) ----
    val remotaDeFinal = emparellamento.entries.associate { it.value to it.key } // índice final -> índice remoto
    for (fi in finais.indices) {
        val col = finais[fi]
        var orixinal = -1
        if (fi < nLocais) {
            val colLocal = l.columnas[vivasOrixe[fi]]
            val li = indiceExameOrixinal(l.columnas, colLocal.columna_orixinal_uid, colLocal.columna_orixinal)
            orixinal = if (li >= 0) novoIndiceLocal[li] ?: -1 else -1
        }
        val ri = remotaDeFinal[fi]
        if (ri != null && ri in ganaronRemotas) {
            val rc = r.columnas[ri]
            val rIdx = indiceExameOrixinal(r.columnas, rc.columna_orixinal_uid, rc.columna_orixinal)
            val fIdx = if (rIdx >= 0) emparellamento[rIdx] else null
            if (fIdx != null) orixinal = fIdx
        }
        if (col.tipo == "recuperacion" && orixinal >= 0) {
            finais[fi] = col.copy(columna_orixinal = finais[orixinal].nome, columna_orixinal_uid = finais[orixinal].uid)
        }
    }

    // ---- 5. Notas: a columna de cada unha resólvese polo uid (sen uid, polo nome); a chave é (alumno, índice final) ----
    val notas = LinkedHashMap<Pair<String, Int>, SnapshotNota>()
    val elixidas = HashMap<Pair<String, Int>, Int?>() // índice final da recuperación elixida (null = ningunha)

    l.notas.forEach { n ->
        val li = n.indiceColumna(l.columnas)
        if (li < 0) return@forEach
        val fi = novoIndiceLocal[li] ?: return@forEach // columna borrada por unha lápida que casou
        val chave = n.alumno to fi
        notas[chave] = n
        val ei = indiceRecuperacionElixida(l.columnas, n.columna_recuperacion_elixida_uid, n.columna_recuperacion_elixida)
        elixidas[chave] = if (ei >= 0) novoIndiceLocal[ei] else null
    }

    r.notas.forEach { n ->
        val ri = n.indiceColumna(r.columnas)
        if (ri < 0) return@forEach
        val fi = emparellamento[ri] ?: return@forEach // columna non creada (lápida posterior)
        val columna = finais[fi]
        val chave = n.alumno to fi
        val existente = notas[chave]
        val conAcertos = n.acertos_listening != null || n.acertos_reading != null || n.acertos_speaking != null

        if (columna.tipo == "destrezas" && !columna.palabras.isNullOrEmpty() && conAcertos) {
            notas[chave] = fusionarNotaDestreza(columna, existente, n)
            elixidas.putIfAbsent(chave, null)
        } else {
            val ei = indiceRecuperacionElixida(r.columnas, n.columna_recuperacion_elixida_uid, n.columna_recuperacion_elixida)
            val elixidaRemota = if (ei >= 0) emparellamento[ei] else null
            val (nova, elixida) = fusionarNotaSimple(existente, elixidas[chave], n, elixidaRemota)
            notas[chave] = nova
            elixidas[chave] = elixida
        }
    }

    // Se a ponderación Listening/Reading/Speaking cambiou (gañou a remota), recalcular TODAS as
    // notas desa columna, tamén as que non veñen no snapshot.
    for (fi in pctCambiadas) {
        val columna = finais[fi]
        for ((chave, nota) in notas.toList()) {
            if (chave.second != fi) continue
            if (nota.acertos_listening == null && nota.acertos_reading == null && nota.acertos_speaking == null) continue
            notas[chave] = nota.copy(
                valor = valorDestreza(columna, nota.acertos_listening.orEmpty(), nota.acertos_reading.orEmpty(), nota.acertos_speaking.orEmpty()),
                modificado_en = columna.modificado_en
            )
        }
    }

    // Saída: cada nota leva o nome E o uid da súa columna (e da recuperación elixida) finais.
    val notasFinais = notas.map { (chave, nota) ->
        val columna = finais[chave.second]
        val elixida = elixidas[chave]?.let { finais.getOrNull(it) }
        nota.copy(
            columna = columna.nome, columna_uid = columna.uid,
            columna_recuperacion_elixida = elixida?.nome, columna_recuperacion_elixida_uid = elixida?.uid
        )
    }

    return SnapshotTrimestre(
        ponderacion = fusionarPonderacion(l.ponderacion, r.ponderacion),
        columnas = finais,
        notas = notasFinais,
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

/** Fusiona unha nota simple. `elixidaLocal`/`elixidaRemota` son os ÍNDICES FINAIS da recuperación
 * elixida (non nomes). Devolve a nota e o índice da recuperación elixida que queda. */
private fun fusionarNotaSimple(
    existente: SnapshotNota?, elixidaLocal: Int?, n: SnapshotNota, elixidaRemota: Int?
): Pair<SnapshotNota, Int?> {
    if (existente == null) return n to elixidaRemota
    val local = listOf(existente.valor, existente.es_recuperacion, existente.criterio_recuperacion, elixidaLocal)
    val remota = listOf(n.valor, n.es_recuperacion, n.criterio_recuperacion, elixidaRemota)
    return if (remotoGana(local, existente.modificado_en, remota, n.modificado_en)) {
        existente.copy(
            valor = n.valor, es_recuperacion = n.es_recuperacion, criterio_recuperacion = n.criterio_recuperacion,
            modificado_en = n.modificado_en
        ) to elixidaRemota
    } else existente to elixidaLocal
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
