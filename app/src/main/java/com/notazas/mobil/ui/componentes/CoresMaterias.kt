package com.notazas.mobil.ui.componentes

import androidx.compose.ui.graphics.Color
import java.text.Normalizer

/**
 * Cor FIXA de cada materia, para recoñecela dun vistazo no Caderno (chips, cabeceiras, tarxetas).
 * Sae do nome da materia, así que é a mesma en todas as pantallas e en todos os móbiles: non hai
 * que configurar nada. As materias habituais teñen a súa cor propia (non se repiten entre elas);
 * as materias con outro nome reciben unha da paleta segundo o seu nome.
 *
 * Dentro dun GRUPO (as materias que se amosan xuntas) nunca hai dúas iguais: ver [coresDasMaterias].
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

/** Toda a paleta, na orde na que se reparten as cores libres cando hai que evitar repeticións. */
private val PALETA = listOf(
    AZUL, VERMELLO, LARANXA, VERDE, MARRON, VIOLETA, CIAN, ROSA, INDIGO, GRIS_AZULADO, LIMA, AMBAR_ESCURO
)

private val PALETA_RESERVA = listOf(LIMA, AMBAR_ESCURO, INDIGO, CIAN, ROSA, VIOLETA, VERDE, AZUL)

private fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

/** Cor das materias habituais (polo nome, sen acentos nin maiúsculas); null se non é ningunha delas.
 * Os fragmentos son raíces: "natur" vale para Natureza/Naturais e "soci" para Sociais. */
private fun corConecida(normalizado: String): Color? = when {
    "galeg" in normalizado -> AZUL
    "castel" in normalizado || "espanol" in normalizado -> VERMELLO
    "matem" in normalizado -> LARANXA
    "natur" in normalizado -> VERDE
    "soci" in normalizado -> MARRON
    "ingl" in normalizado || "estranxeira" in normalizado -> VIOLETA
    "fisic" in normalizado -> CIAN
    "plastic" in normalizado || "visual" in normalizado || "artistic" in normalizado -> ROSA
    "music" in normalizado -> INDIGO
    "relix" in normalizado || "valores" in normalizado || "proxecto" in normalizado -> GRIS_AZULADO
    else -> null
}

private fun corPorNome(normalizado: String): Color =
    PALETA_RESERVA[(normalizado.hashCode() and 0x7fffffff) % PALETA_RESERVA.size]

/** Cor "natural" dunha materia soa (a mesma en toda a app). */
fun corMateria(materia: String): Color {
    val n = normalizar(materia)
    return corConecida(n) ?: corPorNome(n)
}

/**
 * Cores dun CONXUNTO de materias que se amosan xuntas (as dun grupo): cada unha leva a súa
 * [corMateria], pero se dúas coinciden a segunda pasa á primeira cor da paleta aínda libre. Así
 * nunca hai dúas materias coa mesma cor no mesmo grupo (ata 12). As materias habituais teñen
 * prioridade: se unha materia con outro nome coincide con elas, é esta a que cambia.
 */
fun coresDasMaterias(materias: Collection<String>): Map<String, Color> {
    val ordenadas = materias.distinct().sortedBy { normalizar(it) }
    val usadas = HashSet<Color>()
    val resultado = LinkedHashMap<String, Color>()

    fun repartir(nome: String, cor: Color) {
        val libre = if (cor in usadas) PALETA.firstOrNull { it !in usadas } ?: cor else cor
        usadas.add(libre)
        resultado[nome] = libre
    }

    // 1º as materias habituais, despois as demais.
    val (habituais, outras) = ordenadas.partition { corConecida(normalizar(it)) != null }
    habituais.forEach { repartir(it, corMateria(it)) }
    outras.forEach { repartir(it, corMateria(it)) }
    return resultado
}

/** Abreviatura curta (3 letras) da materia para os chips da lista. */
fun abreviaturaMateria(materia: String): String {
    val n = normalizar(materia)
    return when {
        "galeg" in n -> "GAL"
        "castel" in n || "espanol" in n -> "CAS"
        "matem" in n -> "MAT"
        "natur" in n -> "CNA"
        "soci" in n -> "CSO"
        "ingl" in n || "estranxeira" in n -> "ING"
        "fisic" in n -> "EF"
        "plastic" in n || "visual" in n || "artistic" in n -> "PLA"
        "music" in n -> "MUS"
        "relix" in n || "valores" in n -> "REL"
        else -> n.filter { it.isLetter() }.take(3).uppercase().ifEmpty { "?" }
    }
}
