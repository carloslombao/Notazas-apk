package com.notazas.mobil.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notazas.mobil.datos.modelos.Asignacion

/** Chip despregable para escoller SÓ o curso/grupo (sen materia).
 * Úsase onde o dato non depende da materia (a actitude é do profesor
 * e do alumno). Recibe unha asignación representante por grupo. */
@Composable
fun SelectorGrupo(
    grupos: List<Asignacion>,
    seleccionado: Asignacion?,
    onSeleccionar: (Asignacion) -> Unit
) {
    var expandido by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clickable { expandido = true }
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = seleccionado?.grupo ?: "Escolle un curso",
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            grupos.forEach { grupo ->
                DropdownMenuItem(
                    text = { Text(grupo.grupo) },
                    onClick = {
                        onSeleccionar(grupo)
                        expandido = false
                    }
                )
            }
        }
    }
}
