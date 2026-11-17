package com.notazas.mobil.datos.local

import android.content.Context

/**
 * Última materia (asignación) e trimestre que elixiu o profesor, compartidos entre as pantallas
 * (Caderno, Destrezas...). Antes cada pantalla elixía a súa por defecto con regras distintas, así
 * que podían estar mirando materias ou trimestres diferentes (p. ex. as destrezas dunha materia
 * visibles en Probas pero non no Caderno). Non é un dato sensible: SharedPreferences normais.
 */
object SeleccionActual {
    private const val FICHEIRO = "seleccion_actual"
    private const val CLAVE_ASIGNACION = "asignacion_id"
    private const val CLAVE_TRIMESTRE = "trimestre_orde"

    private fun prefs(contexto: Context) =
        contexto.applicationContext.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE)

    fun asignacionId(contexto: Context): Int? =
        prefs(contexto).getInt(CLAVE_ASIGNACION, -1).takeIf { it >= 0 }

    /** Orde do trimestre (1, 2 ou 3): vale igual en liña e sen conexión. */
    fun trimestreOrde(contexto: Context): Int? =
        prefs(contexto).getInt(CLAVE_TRIMESTRE, -1).takeIf { it > 0 }

    fun gardarAsignacion(contexto: Context, asignacionId: Int) {
        prefs(contexto).edit().putInt(CLAVE_ASIGNACION, asignacionId).apply()
    }

    fun gardarTrimestre(contexto: Context, orde: Int) {
        prefs(contexto).edit().putInt(CLAVE_TRIMESTRE, orde).apply()
    }
}
