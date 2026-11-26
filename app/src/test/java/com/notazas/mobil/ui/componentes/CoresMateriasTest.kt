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
        "Ciencias da Natureza",
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
    fun cienciasSociaisECienciasDaNatureza_teñenCoresDistintas() {
        // Bug real: "sociais" non contén "social" e "natureza" non contén "natura", así que ambas
        // caían na cor de reserva e saían iguais.
        assertNotEquals(corMateria("Ciencias Sociais"), corMateria("Ciencias da Natureza"))
        assertEquals(corMateria("Ciencias Naturais"), corMateria("Ciencias da Natureza"))
        assertEquals("CNA", abreviaturaMateria("Ciencias da Natureza"))
        assertEquals("CSO", abreviaturaMateria("Ciencias Sociais"))
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

    // ---------- Cores dun grupo: nunca repetidas ----------

    @Test
    fun coresDoGrupo_nuncaSeRepiten_conMateriasHabituais() {
        val cores = coresDasMaterias(materiasHabituais).values
        assertEquals(materiasHabituais.size, cores.toSet().size)
    }

    @Test
    fun coresDoGrupo_nuncaSeRepiten_aindaConMateriasDesconecidas() {
        // Moitas materias con nomes sen palabra clave: as colisións por hash resólvense dentro do grupo.
        val nomes = listOf("Informática", "Francés", "Tecnoloxía", "Robótica", "Xadrez", "Teatro", "Alemán", "Dereito")
        val cores = coresDasMaterias(nomes).values
        assertEquals(nomes.size, cores.toSet().size)
    }

    @Test
    fun coresDoGrupo_asHabituaisConservanASuaCorPropia() {
        // Unha materia desconecida nunca lle quita a cor a unha habitual.
        val cores = coresDasMaterias(listOf("Inglés", "Informática", "Matemáticas", "Francés", "Teatro"))
        assertEquals(corMateria("Inglés"), cores.getValue("Inglés"))
        assertEquals(corMateria("Matemáticas"), cores.getValue("Matemáticas"))
    }

    @Test
    fun coresDoGrupo_naoDependenDaOrdeDeEntrada() {
        val a = coresDasMaterias(listOf("Inglés", "Francés", "Informática", "Música"))
        val b = coresDasMaterias(listOf("Música", "Informática", "Francés", "Inglés"))
        assertEquals(a, b)
    }
}
