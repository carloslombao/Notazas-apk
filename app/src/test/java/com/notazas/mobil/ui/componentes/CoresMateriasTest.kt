package com.notazas.mobil.ui.componentes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Cores e abreviaturas das materias (código de cores do Caderno). */
class CoresMateriasTest {

    private val materiasHabituais = listOf(
        "Lingua Galega e Literatura",
        "Lingua Castelá e Literatura",
        "Matemáticas",
        "Ciencias Naturais",
        "Ciencias Sociais",
        "Educación Física",
        "Inglés",
        "Educación Plástica e Visual",
        "Música",
        "Relixión / Proxecto Competencial"
    )

    @Test
    fun materiasHabituais_teñenCoresDistintas() {
        val cores = materiasHabituais.map { corMateria(it) }
        assertEquals("Hai materias habituais coa mesma cor", cores.size, cores.toSet().size)
    }

    @Test
    fun aCorEstaVelDoNomeSenImportarAcentosNinMaiusculas() {
        assertEquals(corMateria("Matemáticas"), corMateria("MATEMATICAS"))
        assertEquals(corMateria("Educación Física"), corMateria("educacion fisica"))
    }

    @Test
    fun linguaEstranxeiraIngles_daAMesmaCorQueIngles() {
        assertEquals(corMateria("Inglés"), corMateria("Lingua Estranxeira - Inglés"))
        assertEquals("ING", abreviaturaMateria("Lingua Estranxeira - Inglés"))
    }

    @Test
    fun materiaDesconecida_sempreTenAMesmaCorEUnhaAbreviatura() {
        assertEquals(corMateria("Informática"), corMateria("Informática"))
        assertEquals("INF", abreviaturaMateria("Informática"))
        assertNotEquals("", abreviaturaMateria("123"))
    }
}
