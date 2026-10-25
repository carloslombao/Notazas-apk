package com.notazas.mobil.ui.screens.axustes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notazas.mobil.datos.actualizacion.ActualizadorApp
import kotlinx.coroutines.launch

/** Axustes > "Buscar actualizacións": comproba, descarga co progreso e abre o instalador do sistema. */
@Composable
fun SeccionActualizacion() {
    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }
    var progreso by remember { mutableStateOf<Int?>(null) }
    var mensaxe by remember { mutableStateOf<String?>(null) }
    val versionActual = remember { ActualizadorApp.versionActual(contexto) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Actualización da app", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "Versión actual: $versionActual",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Button(
                enabled = !ocupado,
                onClick = {
                    // Sen o permiso "Instalar apps descoñecidas" Android bloquearía a instalación.
                    if (!ActualizadorApp.podeInstalar(contexto)) {
                        mensaxe = "Permite a Notazas instalar apps descoñecidas e volve premer. Se o sistema non te " +
                            "deixa activalo (p. ex. Protección Avanzada de Google), a actualización non é posible."
                        contexto.startActivity(ActualizadorApp.intentPermisoInstalacion(contexto))
                        return@Button
                    }
                    ocupado = true
                    progreso = null
                    mensaxe = "Buscando actualizacións…"
                    ambito.launch {
                        when (val resultado = ActualizadorApp.buscar(contexto)) {
                            is ActualizadorApp.Resultado.SenActualizacion -> mensaxe = "Xa tes a última versión."
                            is ActualizadorApp.Resultado.Erro -> mensaxe = resultado.mensaxe
                            is ActualizadorApp.Resultado.Dispoñible -> {
                                mensaxe = "Descargando a versión ${resultado.info.latestversion}…"
                                progreso = 0
                                val erro = ActualizadorApp.descargarEInstalar(contexto, resultado.info) { progreso = it }
                                progreso = null
                                mensaxe = erro ?: "Descarga completa: confirma a instalación na pantalla do sistema."
                            }
                        }
                        ocupado = false
                    }
                }
            ) { Text("Buscar actualizacións") }

            progreso?.let {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
                Text("$it %", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            mensaxe?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
