package com.notazas.mobil.datos.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Semana do curso escolar. O `codigo` é o mesmo que xera a web
 * (anoInicio*100 + número de semana, ex. 202601), para que as
 * avaliacións gardadas dende a app e dende a web sexan intercambiables.
 */
data class SemanaCurso(
    val codigo: Int,
    val inicio: LocalDate,
    val etiqueta: String
)

private val MESES_CURTOS = listOf(
    "Xan", "Feb", "Mar", "Abr", "Mai", "Xuñ", "Xul", "Ago", "Set", "Out", "Nov", "Dec"
)

/** Semanas (luns-venres) dende o primeiro luns de setembro ata o 30
 * de xuño. O curso arranca en setembro: de xaneiro a agosto pertence
 * ao curso que empezou o ano anterior. Mesma lóxica ca web. */
fun xerarSemanasCurso(hoxe: LocalDate = LocalDate.now()): List<SemanaCurso> {
    val anoInicio = if (hoxe.monthValue >= 9) hoxe.year else hoxe.year - 1
    val primeiroLuns = LocalDate.of(anoInicio, 9, 1)
        .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    val finXuno = LocalDate.of(anoInicio + 1, 6, 30)

    val semanas = mutableListOf<SemanaCurso>()
    var luns = primeiroLuns
    var indice = 1
    while (!luns.isAfter(finXuno)) {
        semanas += SemanaCurso(
            codigo = anoInicio * 100 + indice,
            inicio = luns,
            etiqueta = etiquetaSemana(luns, luns.plusDays(4))
        )
        luns = luns.plusWeeks(1)
        indice++
    }
    return semanas
}

/** Semana que contén "hoxe"; se é antes de empezar o curso, a
 * primeira; se é despois de acabar, a última. */
fun List<SemanaCurso>.semanaActual(hoxe: LocalDate = LocalDate.now()): SemanaCurso? {
    if (isEmpty()) return null
    if (hoxe.isBefore(first().inicio)) return first()
    return firstOrNull { !hoxe.isBefore(it.inicio) && !hoxe.isAfter(it.inicio.plusDays(6)) } ?: last()
}

private fun etiquetaSemana(luns: LocalDate, venres: LocalDate): String {
    val mesLuns = MESES_CURTOS[luns.monthValue - 1]
    val mesVenres = MESES_CURTOS[venres.monthValue - 1]
    return if (luns.monthValue == venres.monthValue) {
        "${luns.dayOfMonth}-${venres.dayOfMonth} $mesLuns"
    } else {
        "${luns.dayOfMonth} $mesLuns - ${venres.dayOfMonth} $mesVenres"
    }
}
