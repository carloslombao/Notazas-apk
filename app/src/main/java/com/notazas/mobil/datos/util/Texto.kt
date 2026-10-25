package com.notazas.mobil.datos.util

import java.text.Collator
import java.util.Locale

/**
 * Comparador alfabético que trata as vogais con til coma a vogal
 * simple para efectos de orde (Álvarez preto de Alonso/Amado), igual
 * có "clave_alfabetica()" do backend — Kotlin/Java por defecto ordena
 * por código Unicode, onde as maiúsculas con til quedan despois da Z.
 */
val comparadorAlfabeticoGalego: Comparator<String> by lazy {
    val collator = Collator.getInstance(Locale("gl", "ES"))
    collator.strength = Collator.PRIMARY
    Comparator { a, b -> collator.compare(a, b) }
}

fun <T> Iterable<T>.ordenadosPorNome(selector: (T) -> String): List<T> =
    sortedWith(compareBy(comparadorAlfabeticoGalego, selector))
