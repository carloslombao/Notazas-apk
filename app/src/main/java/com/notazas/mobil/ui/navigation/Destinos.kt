package com.notazas.mobil.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** As 5 seccións da barra de navegación inferior (deseño Notazas Core). */
sealed class Destino(val ruta: String, val etiqueta: String, val icona: ImageVector) {
    data object Caderno : Destino("caderno", "Caderno", Icons.Filled.Dashboard)
    data object Alumnado : Destino("alumnado", "Alumnado", Icons.Filled.Groups)
    data object Probas : Destino("probas", "Probas", Icons.Filled.Assignment)
    data object Actitude : Destino("actitude", "Actitude", Icons.Filled.SentimentSatisfied)
    data object Axustes : Destino("axustes", "Axustes", Icons.Filled.Settings)
}

val destinosBarraInferior = listOf(
    Destino.Caderno,
    Destino.Alumnado,
    Destino.Probas,
    Destino.Actitude,
    Destino.Axustes
)
