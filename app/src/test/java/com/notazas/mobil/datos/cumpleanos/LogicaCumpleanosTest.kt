package com.notazas.mobil.datos.cumpleanos

import com.notazas.mobil.datos.modelos.CumpleanosOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regras dos avisos de aniversario. Datas de referencia (2026): venres 23/10,
 * sábado 24/10, domingo 25/10, luns 26/10; xoves 31/12; venres 1/1/2027.
 */
class LogicaCumpleanosTest {

    private fun alumno(nacemento: String, id: Int = 1) =
        CumpleanosOut(id, "Daniel", "Martínez González", "4ºA", nacemento)

    private fun textos(hoxe: String, modo: ModoAviso, vararg nacementos: String) =
        avisosDoDia(LocalDate.parse(hoxe), nacementos.mapIndexed { i, n -> alumno(n, i + 1) }, modo).map { it.texto }

    @Test
    fun mesmoDia_entreSemana_avisaHoxe() {
        // Aniversario o xoves 22/10/2026, nado en 2018 -> 8 anos.
        assertEquals(
            listOf("Daniel Martínez González (4ºA) cumpre 8 anos hoxe"),
            textos("2026-10-22", ModoAviso.MESMO_DIA, "2018-10-22")
        )
    }

    @Test
    fun diaAnterior_entreSemana_avisaMana() {
        // Aniversario o xoves 22/10: avísase o mércores 21/10.
        assertEquals(
            listOf("Daniel Martínez González (4ºA) cumpre 8 anos mañá"),
            textos("2026-10-21", ModoAviso.DIA_ANTERIOR, "2018-10-22")
        )
        // E o propio día xa non se avisa.
        assertTrue(textos("2026-10-22", ModoAviso.DIA_ANTERIOR, "2018-10-22").isEmpty())
    }

    @Test
    fun sabado_mesmoDia_avisaOVenresDicindoOSabado() {
        assertEquals(
            listOf("Daniel Martínez González (4ºA) cumpre 8 anos o sábado"),
            textos("2026-10-23", ModoAviso.MESMO_DIA, "2018-10-24")
        )
        // O sábado en si non se avisa.
        assertTrue(textos("2026-10-24", ModoAviso.MESMO_DIA, "2018-10-24").isEmpty())
    }

    @Test
    fun domingo_mesmoDia_avisaOVenresDicindoODomingo() {
        assertEquals(
            listOf("Daniel Martínez González (4ºA) cumpre 8 anos o domingo"),
            textos("2026-10-23", ModoAviso.MESMO_DIA, "2018-10-25")
        )
    }

    @Test
    fun sabado_diaAnterior_avisaOVenresDicindoMana() {
        // A véspera dun sábado é venres: "mañá" é certo.
        assertEquals(
            listOf("Daniel Martínez González (4ºA) cumpre 8 anos mañá"),
            textos("2026-10-23", ModoAviso.DIA_ANTERIOR, "2018-10-24")
        )
    }

    @Test
    fun domingo_diaAnterior_avisaOVenresDicindoODomingo() {
        // A véspera é sábado -> adiántase ao venres.
        assertEquals(
            listOf("Daniel Martínez González (4ºA) cumpre 8 anos o domingo"),
            textos("2026-10-23", ModoAviso.DIA_ANTERIOR, "2018-10-25")
        )
    }

    @Test
    fun luns_diaAnterior_avisaOVenresDicindoOLuns() {
        // A véspera dun luns é domingo -> adiántase ao venres.
        assertEquals(
            listOf("Daniel Martínez González (4ºA) cumpre 8 anos o luns"),
            textos("2026-10-23", ModoAviso.DIA_ANTERIOR, "2018-10-26")
        )
    }

    @Test
    fun luns_mesmoDia_avisaHoxe() {
        assertEquals(
            listOf("Daniel Martínez González (4ºA) cumpre 8 anos hoxe"),
            textos("2026-10-26", ModoAviso.MESMO_DIA, "2018-10-26")
        )
    }

    @Test
    fun aniversarioDeXaneiro_avisaEnDecembroDoAnoAnterior() {
        // Nado o 1/1/2016: cumpre 11 o venres 1/1/2027; a véspera é xoves 31/12/2026.
        assertEquals(
            listOf("Daniel Martínez González (4ºA) cumpre 11 anos mañá"),
            textos("2026-12-31", ModoAviso.DIA_ANTERIOR, "2016-01-01")
        )
    }

    @Test
    fun vintenove_defebreiro_nonBisesto_celebraseO28() {
        assertEquals(LocalDate.parse("2027-02-28"), cumpleanosNoAno(LocalDate.parse("2016-02-29"), 2027))
        assertEquals(LocalDate.parse("2028-02-29"), cumpleanosNoAno(LocalDate.parse("2016-02-29"), 2028))
    }

    @Test
    fun variosAlumnos_unhaNotificacionPorCadaUn() {
        val avisos = avisosDoDia(
            LocalDate.parse("2026-10-22"),
            listOf(
                CumpleanosOut(1, "Daniel", "Martínez González", "4ºA", "2018-10-22"),
                CumpleanosOut(2, "Ana", "Álvarez Pérez", "3ºB", "2017-10-22"),
                CumpleanosOut(3, "Lúa", "Gómez Rey", "4ºA", "2018-11-02")
            ),
            ModoAviso.MESMO_DIA
        )
        assertEquals(2, avisos.size)
        assertEquals(setOf(1, 2), avisos.map { it.alumnoId }.toSet())
    }

    @Test
    fun dataInvalidaOuDeHoxe_ignoranse() {
        // Data mal formada e alumno "nado hoxe" (0 anos): sen aviso, sen petar.
        assertTrue(textos("2026-10-22", ModoAviso.MESMO_DIA, "non-e-unha-data", "2026-10-22").isEmpty())
    }

    @Test
    fun proximoDisparo_hoxeSeAindaNonPasouSenonMana() {
        val config = ConfigCumpleanos(activado = true, hora = 8, minuto = 30)
        assertEquals(
            java.time.LocalDateTime.parse("2026-10-22T08:30:00"),
            PlanificadorCumpleanos.proximoDisparo(config, java.time.LocalDateTime.parse("2026-10-22T07:00:00"))
        )
        assertEquals(
            java.time.LocalDateTime.parse("2026-10-23T08:30:00"),
            PlanificadorCumpleanos.proximoDisparo(config, java.time.LocalDateTime.parse("2026-10-22T09:00:00"))
        )
    }
}
