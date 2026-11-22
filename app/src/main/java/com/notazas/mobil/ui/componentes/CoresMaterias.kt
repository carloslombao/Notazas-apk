package com.notazas.mobil.ui.componentes

import androidx.compose.ui.graphics.Color
import java.text.Normalizer

/**
 * Cor FIXA de cada materia, para recoñecela dun vistazo no Caderno (chips, cabeceiras, tarxetas).
 * Sae do nome da materia, así que é a mesma en todas as pantallas e en todos os móbiles: non hai
 * que configurar nada. As materias habituais teñen a súa cor propia (non se repiten entre elas);
 * as materias con outro nome reciben unha da paleta segundo o seu nome.
 */
private val AZUL = Color(0xFF1E88E5)
private val VERMELLO = Color(0xFFE53935)
private val LARANXA = Color(0xFFFB8C00)
private val VERDE = Color(0xFF43A047)
private val MARRON = Color(0xFF6D4C41)
private val VIOLETA = Color(0xFF8E24AA)
private val CIAN = Color(0xFF00ACC1)
private val ROSA = Color(0xFFD81B60)
private val INDIGO = Color(0xFF3949AB)
private val GRIS_AZULADO = Color(0xFF546E7A)
private val LIMA = Color(0xFF7CB342)
private val AMBAR_ESCURO = Color(0xFFB26A00)

private val PALETA_RESERVA = listOf(LIMA, AMBAR_ESCURO, INDIGO, CIAN, ROSA, VIOLETA, VERDE, AZUL)

private fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

fun corMateria(materia: String): Color {
    val n = normalizar(materia)
    return when {
        "galeg" in n -> AZUL
        "castel" in n || "espanol" in n -> VERMELLO
        "matem" in n -> LARANXA
        "natura" in n -> VERDE
        "social" in n -> MARRON
        "ingl" in n || "estranxeira" in n -> VIOLETA
        "fisic" in n -> CIAN
        "plastic" in n || "visual" in n || "artistic" in n -> ROSA
        "music" in n -> INDIGO
        "relix" in n || "valores" in n || "proxecto" in n -> GRIS_AZULADO
        else -> PALETA_RESERVA[(n.hashCode() and 0x7fffffff) % PALETA_RESERVA.size]
    }
}

/** Abreviatura curta (3 letras) da materia para os chips da lista. */
fun abreviaturaMateria(materia: String): String {
    val n = normalizar(materia)
    return when {
        "galeg" in n -> "GAL"
        "castel" in n || "espanol" in n -> "CAS"
        "matem" in n -> "MAT"
        "natura" in n -> "CNA"
        "social" in n -> "CSO"
        "ingl" in n || "estranxeira" in n -> "ING"
        "fisic" in n -> "EF"
        "plastic" in n || "visual" in n || "artistic" in n -> "PLA"
        "music" in n -> "MUS"
        "relix" in n || "valores" in n -> "REL"
        else -> n.filter { it.isLetter() }.take(3).uppercase().ifEmpty { "?" }
    }
}
