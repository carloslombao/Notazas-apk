package com.notazas.mobil.datos.cumpleanos

import com.notazas.mobil.datos.modelos.CumpleanosOut
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year

/**
 * Lóxica PURA (sen Android) dos avisos de aniversario do alumnado.
 *
 * Regras:
 *  - MESMO_DIA: avísase o día do aniversario ("cumpre 8 anos hoxe").
 *  - DIA_ANTERIOR: avísase a véspera ("cumpre 8 anos mañá").
 *  - Se o día do aviso cae en SÁBADO ou DOMINGO, adiántase ao VENRES
 *    anterior (nun só aviso) e o texto di o día da semana: "cumpre 8 anos
 *    o sábado". Iso cobre tamén os aniversarios de luns co aviso a
 *    véspera ("cumpre 8 anos o luns", avisado o venres).
 *  - 29 de febreiro: nos anos non bisestos celébrase o 28 de febreiro.
 *  - Unha notificación por alumno.
 */
enum class ModoAviso { MESMO_DIA, DIA_ANTERIOR }

data class AvisoCumpleanos(
    val alumnoId: Int,
    val titulo: String,
    val texto: String,
    /** Data do aniversario real (non a do aviso). */
    val cumpleanos: LocalDate
)

private val DIAS_SEMANA = mapOf(
    DayOfWeek.MONDAY to "luns",
    DayOfWeek.TUESDAY to "martes",
    DayOfWeek.WEDNESDAY to "mércores",
    DayOfWeek.THURSDAY to "xoves",
    DayOfWeek.FRIDAY to "venres",
    DayOfWeek.SATURDAY to "sábado",
    DayOfWeek.SUNDAY to "domingo"
)

fun parseDataNacemento(texto: String): LocalDate? =
    try { LocalDate.parse(texto.take(10)) } catch (e: Exception) { null }

/** Aniversario dun alumno no ano indicado (29/02 en ano non bisesto -> 28/02). */
fun cumpleanosNoAno(nacemento: LocalDate, ano: Int): LocalDate =
    if (nacemento.monthValue == 2 && nacemento.dayOfMonth == 29 && !Year.isLeap(ano.toLong())) {
        LocalDate.of(ano, 2, 28)
    } else {
        LocalDate.of(ano, nacemento.monthValue, nacemento.dayOfMonth)
    }

/** Día no que hai que avisar dun aniversario, segundo o modo. */
fun diaDoAviso(cumpleanos: LocalDate, modo: ModoAviso): LocalDate {
    val dia = if (modo == ModoAviso.MESMO_DIA) cumpleanos else cumpleanos.minusDays(1)
    return when (dia.dayOfWeek) {
        DayOfWeek.SATURDAY -> dia.minusDays(1)
        DayOfWeek.SUNDAY -> dia.minusDays(2)
        else -> dia
    }
}

/** Texto do momento: "hoxe" ou "mañá" cando o aviso cae no seu día natural (o do
 * aniversario ou a véspera); "o sábado", "o domingo", "o luns"... cando se adiantou
 * ao venres por caer en fin de semana. */
fun fraseDoDia(diaAviso: LocalDate, cumpleanos: LocalDate, modo: ModoAviso): String {
    val diaNatural = if (modo == ModoAviso.MESMO_DIA) cumpleanos else cumpleanos.minusDays(1)
    if (diaAviso == diaNatural) {
        return if (modo == ModoAviso.MESMO_DIA) "hoxe" else "mañá"
    }
    return "o ${DIAS_SEMANA.getValue(cumpleanos.dayOfWeek)}"
}

/** Avisos que hai que lanzar HOXE (un por alumno), ordenados por apelidos. */
fun avisosDoDia(hoxe: LocalDate, alumnos: List<CumpleanosOut>, modo: ModoAviso): List<AvisoCumpleanos> =
    alumnos.mapNotNull { alumno ->
        val nacemento = parseDataNacemento(alumno.data_nacemento) ?: return@mapNotNull null
        // Un aviso de hoxe pode ser dun aniversario deste ano ou (en decembro, ou aviso
        // adiantado ao venres) dun xaneiro do ano seguinte.
        val cumpleanos = listOf(hoxe.year, hoxe.year + 1)
            .map { cumpleanosNoAno(nacemento, it) }
            .firstOrNull { diaDoAviso(it, modo) == hoxe }
            ?: return@mapNotNull null

        val anos = cumpleanos.year - nacemento.year
        if (anos <= 0) return@mapNotNull null

        AvisoCumpleanos(
            alumnoId = alumno.alumno_id,
            titulo = "🎂 Aniversario",
            texto = "${alumno.nome} ${alumno.apelidos} (${alumno.grupo}) cumpre " +
                "${if (anos == 1) "1 ano" else "$anos anos"} ${fraseDoDia(hoxe, cumpleanos, modo)}",
            cumpleanos = cumpleanos
        )
    }.sortedBy { it.texto }
