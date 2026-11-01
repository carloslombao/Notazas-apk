package com.notazas.mobil.datos.offline

import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.PalabraOut

/**
 * Lóxica pura para a Proba Destrezas en modo "fóra do colexio":
 * crear/modificar a proba e gardar os acertos directamente sobre o
 * snapshot. Non hai ids reais de palabra en offline (só hai texto no
 * snapshot — ver nextcloud_client.py), así que se sintetizan a partir
 * do propio texto (estables mentres non haxa dúas palabras idénticas
 * na mesma proba).
 */

data class PalabraOffline(val id: Int, val texto: String, val orde: Int)

fun idPalabraOffline(texto: String): Int = texto.hashCode()

fun CacheAsignacionEntrada.columnasDestrezas(trimestreNome: String): List<SnapshotColumna> =
    snapshot.trimestres[trimestreNome]?.columnas?.filter { it.tipo == "destrezas" }.orEmpty()

fun palabrasDeColumna(columna: SnapshotColumna): List<PalabraOffline> =
    (columna.palabras ?: emptyList()).mapIndexed { i, texto -> PalabraOffline(idPalabraOffline(texto), texto, i) }

/** Para reutilizar DestrezasScreen.kt sen cambios: constrúe o MESMO
 * tipo que usa o modo en liña a partir do snapshot. */
fun SnapshotColumna.comoColumnaOut(asignacionId: Int): ColumnaOut = ColumnaOut(
    id = idColumnaOffline(this),
    asignacion_id = asignacionId,
    trimestre_id = 0,
    tipo = tipo,
    nome = nome,
    pct_listening = pct_listening,
    pct_reading = pct_reading,
    pct_speaking = pct_speaking,
    palabras = palabrasDeColumna(this).map { PalabraOut(it.id, it.texto, it.orde) }
)

/** Mesma fórmula có servidor (calc.calcular_nota_destreza_palabras). */
fun notaDestrezaPalabras(
    totalPalabras: Int, pctListening: Double?, pctReading: Double?, pctSpeaking: Double?,
    numListening: Int, numReading: Int, numSpeaking: Int
): Double? {
    if (totalPalabras == 0) return null
    val pl = pctListening ?: 0.0
    val pr = pctReading ?: 0.0
    val ps = pctSpeaking ?: 0.0
    val totalPct = pl + pr + ps
    if (totalPct <= 0.0 || (numListening == 0 && numReading == 0 && numSpeaking == 0)) return null
    val acumulado = (numListening.toDouble() / totalPalabras) * pl +
        (numReading.toDouble() / totalPalabras) * pr +
        (numSpeaking.toDouble() / totalPalabras) * ps
    return minOf(acumulado / totalPct, 1.0) * 10.0
}

fun crearProbaDestrezasOffline(
    entrada: CacheAsignacionEntrada,
    trimestreNome: String,
    nome: String,
    data: String?,
    palabras: List<String>,
    pctListening: Double,
    pctReading: Double,
    pctSpeaking: Double
): CacheAsignacionEntrada {
    val ahora = horaIsoUtcEstiloPython()
    val novaColumna = SnapshotColumna(
        nome = nome, tipo = "destrezas", data = data, palabras = palabras,
        pct_listening = pctListening, pct_reading = pctReading, pct_speaking = pctSpeaking,
        // v2: identidade estable (sobrevive a renomear) e data de creación
        uid = java.util.UUID.randomUUID().toString().replace("-", ""),
        modificado_en = ahora
    )
    val trimestreActual = entrada.snapshot.trimestres[trimestreNome] ?: SnapshotTrimestre()
    val trimestreNovo = trimestreActual.copy(columnas = trimestreActual.columnas + novaColumna)
    return entrada.copy(
        snapshot = entrada.snapshot.copy(
            trimestres = entrada.snapshot.trimestres + (trimestreNome to trimestreNovo),
            modificado_en = horaIsoUtcEstiloPython()
        )
    )
}

/** alumnoId sintético -> nota /10 nesa proba (null = sen avaliar). A proba identíficase polo seu id
 * (derivado do uid), non polo nome. */
fun lerNotasDestrezasOffline(entrada: CacheAsignacionEntrada, trimestreNome: String, columnaId: Int): Map<Int, Double?> {
    val trimestre = entrada.snapshot.trimestres[trimestreNome] ?: return emptyMap()
    val columna = trimestre.columnas.firstOrNull { it.tipo == "destrezas" && idColumnaOffline(it) == columnaId }
        ?: return emptyMap()
    return entrada.snapshot.alumnado.associate { a ->
        idAlumnoOffline(a.nome, a.apelidos) to trimestre.notaDe(claveAlumno(a.nome, a.apelidos), columna)?.valor
    }
}

/** (idsListening, idsReading, idsSpeaking) xa marcados para ese alumno nesa proba. */
fun lerAcertosOffline(
    entrada: CacheAsignacionEntrada, trimestreNome: String, columnaId: Int,
    alumnoNome: String, alumnoApelidos: String
): Triple<Set<Int>, Set<Int>, Set<Int>> {
    val baleiro = Triple(emptySet<Int>(), emptySet<Int>(), emptySet<Int>())
    val trimestre = entrada.snapshot.trimestres[trimestreNome] ?: return baleiro
    val columna = trimestre.columnas.firstOrNull { it.tipo == "destrezas" && idColumnaOffline(it) == columnaId } ?: return baleiro
    val nota = trimestre.notaDe(claveAlumno(alumnoNome, alumnoApelidos), columna) ?: return baleiro
    return Triple(
        nota.acertos_listening.orEmpty().map { idPalabraOffline(it) }.toSet(),
        nota.acertos_reading.orEmpty().map { idPalabraOffline(it) }.toSet(),
        nota.acertos_speaking.orEmpty().map { idPalabraOffline(it) }.toSet()
    )
}

/** Devolve o snapshot actualizado e a nota /10 resultante (para a UI). */
fun gardarDestrezaOffline(
    entrada: CacheAsignacionEntrada,
    trimestreNome: String,
    columnaId: Int,
    alumnoNome: String,
    alumnoApelidos: String,
    idsListening: Set<Int>,
    idsReading: Set<Int>,
    idsSpeaking: Set<Int>
): Pair<CacheAsignacionEntrada, Double?> {
    val trimestreActual = entrada.snapshot.trimestres[trimestreNome] ?: SnapshotTrimestre()
    val columna = trimestreActual.columnas.firstOrNull { it.tipo == "destrezas" && idColumnaOffline(it) == columnaId }
        ?: return entrada to null
    val textoPorId = palabrasDeColumna(columna).associate { it.id to it.texto }
    val textosListening = idsListening.mapNotNull { textoPorId[it] }
    val textosReading = idsReading.mapNotNull { textoPorId[it] }
    val textosSpeaking = idsSpeaking.mapNotNull { textoPorId[it] }
    val valor = notaDestrezaPalabras(
        (columna.palabras ?: emptyList()).size, columna.pct_listening, columna.pct_reading, columna.pct_speaking,
        textosListening.size, textosReading.size, textosSpeaking.size
    )

    val clave = claveAlumno(alumnoNome, alumnoApelidos)
    val existente = trimestreActual.notaDe(clave, columna)
    val ahora = horaIsoUtcEstiloPython()

    // v2: cada palabra leva a data do seu último cambio de estado (marcada ou desmarcada),
    // por destreza. Só se estampan as palabras que CAMBIARON; as demais conservan a súa data.
    var algoCambiou = false
    fun marcas(destreza: String, antes: List<String>?, agora: List<String>): Map<String, String> {
        val resultado = existente?.acertos_t?.get(destreza).orEmpty().toMutableMap()
        val anteriores = antes.orEmpty().toSet()
        val novos = agora.toSet()
        ((anteriores - novos) + (novos - anteriores)).forEach { texto ->
            resultado[texto] = ahora
            algoCambiou = true
        }
        return resultado
    }
    val marcasNovas = mapOf(
        "listening" to marcas("listening", existente?.acertos_listening, textosListening),
        "reading" to marcas("reading", existente?.acertos_reading, textosReading),
        "speaking" to marcas("speaking", existente?.acertos_speaking, textosSpeaking)
    )

    val novaNota = (existente ?: SnapshotNota(alumno = clave, columna = columna.nome)).copy(
        columna = columna.nome, columna_uid = columna.uid,
        valor = valor,
        acertos_listening = textosListening, acertos_reading = textosReading, acertos_speaking = textosSpeaking,
        acertos_t = marcasNovas,
        // Se non cambiou ningunha palabra non se toca a data da nota (non gaña a ninguén por premer Gardar).
        modificado_en = if (algoCambiou || existente == null) ahora else existente.modificado_en
    )
    val notasSenEsta = trimestreActual.notasSenEsta(clave, columna)
    val trimestreNovo = trimestreActual.copy(notas = notasSenEsta + novaNota)
    val entradaNova = entrada.copy(
        snapshot = entrada.snapshot.copy(
            trimestres = entrada.snapshot.trimestres + (trimestreNome to trimestreNovo),
            modificado_en = horaIsoUtcEstiloPython()
        )
    )
    return entradaNova to valor
}
