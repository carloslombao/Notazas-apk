package com.notazas.mobil.datos.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Identidade das columnas na fusión (sección 8 do documento de sincronización): o nome NON é
 * único, así que un exame "U1" e un traballo "U1" non se poden confundir. As datas son relativas
 * a hoxe porque as lápidas de máis de 400 días descártanse.
 */
class SincronizacionFusionTest {

    private val agora = LocalDateTime.now(ZoneOffset.UTC)
    private fun t(diasAtras: Long) = agora.minusDays(diasAtras).toString()

    private val trimestre = "1º Trimestre"
    private val clave = claveAlumno("Ana", "Pérez")

    private fun snap(
        columnas: List<SnapshotColumna>,
        notas: List<SnapshotNota> = emptyList(),
        borrados: List<SnapshotBorrado> = emptyList()
    ) = SnapshotAsignacion(
        materia = "Inglés", curso_nome = "1º", ano_academico = "2026-27", grupo_letra = "A",
        alumnado = listOf(SnapshotAlumnoBase("Ana", "Pérez")),
        trimestres = mapOf(trimestre to SnapshotTrimestre(columnas = columnas, notas = notas)),
        borrados = borrados, modificado_en = t(0)
    )

    private fun exame(uid: String?, nome: String = "U1", modificado: String = t(30)) =
        SnapshotColumna(nome = nome, tipo = "exame", uid = uid, modificado_en = modificado)

    private fun traballo(uid: String?, nome: String = "U1", modificado: String = t(30)) =
        SnapshotColumna(nome = nome, tipo = "traballo", uid = uid, modificado_en = modificado)

    private fun nota(columna: String, uid: String?, valor: Double, modificado: String = t(30)) =
        SnapshotNota(alumno = clave, columna = columna, columna_uid = uid, valor = valor, modificado_en = modificado)

    private fun trimestreDe(s: SnapshotAsignacion) = s.trimestres.getValue(trimestre)

    // ---------- Lápidas ----------

    @Test
    fun lapidaPorUid_borraSoUnhaDasDuasColumnasConMesmoNome() {
        // Local: exame "U1" e traballo "U1". Remoto: o exame borrouse (lápida polo seu uid).
        val local = snap(
            columnas = listOf(exame("ex"), traballo("tr")),
            notas = listOf(nota("U1", "ex", 7.0), nota("U1", "tr", 5.0))
        )
        val remoto = snap(
            columnas = listOf(traballo("tr")),
            notas = listOf(nota("U1", "tr", 5.0)),
            borrados = listOf(SnapshotBorrado(uid = "ex", trimestre = trimestre, nome = "U1", tipo = "exame", t = t(1)))
        )

        val resultado = trimestreDe(fusionarSnapshots(local, remoto))

        assertEquals(listOf("tr"), resultado.columnas.map { it.uid })
        assertEquals(listOf("tr"), resultado.notas.map { it.columna_uid }) // a nota do exame vai co exame
        assertEquals(5.0, resultado.notas.single().valor)
    }

    @Test
    fun lapidaSenUidPeroConTipo_borraSoColumnasDESETipo() {
        val local = snap(
            columnas = listOf(exame(null), traballo(null)),
            notas = listOf(nota("U1", null, 7.0))
        )
        val remoto = snap(
            columnas = emptyList(),
            borrados = listOf(SnapshotBorrado(uid = null, trimestre = trimestre, nome = "U1", tipo = "exame", t = t(1)))
        )

        val resultado = trimestreDe(fusionarSnapshots(local, remoto))

        assertEquals(listOf("traballo"), resultado.columnas.map { it.tipo })
    }

    @Test
    fun lapidaSenUidNinTipo_nonBorraNada() {
        // Unha lápida vella sen tipo e sen uid non pode decidir cal das dúas "U1" se borrou.
        val local = snap(columnas = listOf(exame(null), traballo(null)))
        val remoto = snap(
            columnas = emptyList(),
            borrados = listOf(SnapshotBorrado(uid = null, trimestre = trimestre, nome = "U1", tipo = null, t = t(1)))
        )

        val resultado = trimestreDe(fusionarSnapshots(local, remoto))

        assertEquals(2, resultado.columnas.size)
    }

    @Test
    fun lapidaConOutroUid_nonBorraAColumna_aindaQueCoincidaONome() {
        // Se AMBAS teñen uid só importa o uid: un uid distinto nunca casa por nome.
        val local = snap(columnas = listOf(exame("ex")))
        val remoto = snap(
            columnas = emptyList(),
            borrados = listOf(SnapshotBorrado(uid = "outro", trimestre = trimestre, nome = "U1", tipo = "exame", t = t(1)))
        )

        assertEquals(listOf("ex"), trimestreDe(fusionarSnapshots(local, remoto)).columnas.map { it.uid })
    }

    @Test
    fun columnaUsadaDespoisDaLapida_sobrevive() {
        val local = snap(
            columnas = listOf(exame("ex")),
            notas = listOf(nota("U1", "ex", 9.0, modificado = t(0))) // editada despois do borrado
        )
        val remoto = snap(
            columnas = emptyList(),
            borrados = listOf(SnapshotBorrado(uid = "ex", trimestre = trimestre, nome = "U1", tipo = "exame", t = t(2)))
        )

        assertEquals(listOf("ex"), trimestreDe(fusionarSnapshots(local, remoto)).columnas.map { it.uid })
    }

    // ---------- Emparellamento ----------

    @Test
    fun emparellaPorNomeETipo_eAdoptaOUidRemoto_sinMesturarNotas() {
        // Local sen uids (snapshot vello): exame "U1" e traballo "U1". Remoto con uids e notas por uid.
        val local = snap(columnas = listOf(exame(null), traballo(null)))
        val remoto = snap(
            columnas = listOf(exame("ex"), traballo("tr")),
            notas = listOf(nota("U1", "ex", 8.0), nota("U1", "tr", 6.0))
        )

        val resultado = trimestreDe(fusionarSnapshots(local, remoto))

        assertEquals(listOf("ex", "tr"), resultado.columnas.map { it.uid })
        assertEquals(listOf("exame", "traballo"), resultado.columnas.map { it.tipo })
        assertEquals(8.0, resultado.notas.first { it.columna_uid == "ex" }.valor)
        assertEquals(6.0, resultado.notas.first { it.columna_uid == "tr" }.valor)
    }

    @Test
    fun columnasComUidDistinto_nonSeEmparellanPorNome() {
        val local = snap(columnas = listOf(exame("l1")))
        val remoto = snap(columnas = listOf(exame("r1")))

        val uids = trimestreDe(fusionarSnapshots(local, remoto)).columnas.map { it.uid }

        assertEquals(setOf("l1", "r1"), uids.toSet())
    }

    @Test
    fun notasVanAColumnaCorrectaPorUid_aindaConNomeIgual() {
        val local = snap(
            columnas = listOf(exame("ex"), traballo("tr")),
            notas = listOf(nota("U1", "ex", 7.0, modificado = t(5)), nota("U1", "tr", 5.0, modificado = t(5)))
        )
        // O remoto trae unha versión máis nova só da nota do traballo.
        val remoto = snap(
            columnas = listOf(exame("ex"), traballo("tr")),
            notas = listOf(nota("U1", "tr", 9.0, modificado = t(1)))
        )

        val resultado = trimestreDe(fusionarSnapshots(local, remoto))

        assertEquals(7.0, resultado.notas.first { it.columna_uid == "ex" }.valor)
        assertEquals(9.0, resultado.notas.first { it.columna_uid == "tr" }.valor)
    }

    // ---------- Recuperacións ----------

    @Test
    fun recuperacionElixidaPorUid_conservaOUidNaSaida() {
        val recup = SnapshotColumna(
            nome = "Recup U1", tipo = "recuperacion", uid = "rc",
            columna_orixinal = "U1", columna_orixinal_uid = "ex", modificado_en = t(30)
        )
        val local = snap(columnas = listOf(exame("ex"), traballo("tr"), recup))
        val remoto = snap(
            columnas = listOf(exame("ex"), traballo("tr"), recup),
            notas = listOf(
                nota("U1", "ex", 4.0).copy(
                    criterio_recuperacion = "recuperacion",
                    columna_recuperacion_elixida = "Recup U1", columna_recuperacion_elixida_uid = "rc"
                )
            )
        )

        val resultado = trimestreDe(fusionarSnapshots(local, remoto))
        val notaExame = resultado.notas.single { it.columna_uid == "ex" }

        assertEquals("rc", notaExame.columna_recuperacion_elixida_uid)
        assertEquals("Recup U1", notaExame.columna_recuperacion_elixida)
        assertEquals("ex", resultado.columnas.single { it.uid == "rc" }.columna_orixinal_uid)
    }

    // ---------- Chaves e ids ----------

    @Test
    fun idsOffline_distintosParaColumnasConMesmoNome() {
        assertTrue(idColumnaOffline(exame("ex")) != idColumnaOffline(traballo("tr")))
        // Sen uid, o tipo tamén entra na chave.
        assertTrue(idColumnaOffline(exame(null)) != idColumnaOffline(traballo(null)))
    }

    @Test
    fun lapidaDeColumna_gardaOTipoEOUid() {
        val lapida = lapidaDeColumna(trimestre, exame("ex"))
        assertEquals("exame", lapida.tipo)
        assertEquals("ex", lapida.uid)
    }
}
