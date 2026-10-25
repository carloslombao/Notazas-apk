package com.notazas.mobil.ui.screens.alumnado

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.notazas.mobil.ui.componentes.SelectorAsignacion

@Composable
fun AlumnadoScreen(onAlumnoClick: (Int) -> Unit = {}, viewModel: AlumnadoViewModel = viewModel()) {
    val estado = viewModel.estado
    val contexto = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SelectorAsignacion(
                asignacions = estado.asignacions,
                seleccionada = estado.asignacionSeleccionada,
                onSeleccionar = viewModel::seleccionarAsignacion
            )
        }

        when {
            estado.cargando && estado.alumnos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            estado.erro != null && estado.alumnos.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(estado.erro, color = MaterialTheme.colorScheme.error)
                }
            }

            estado.asignacionSeleccionada == null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Aínda non tes ningunha asignación.", style = MaterialTheme.typography.bodyLarge)
                }
            }

            else -> {
                val filtrados = estado.alumnos.filter {
                    estado.busca.isBlank() || it.nome_completo.contains(estado.busca, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    item {
                        if (estado.modoFoiraDeLina) {
                            Text(
                                "Modo sen conexión${estado.datosDe?.let { " · datos do $it" }.orEmpty()} · a ficha completa do alumno só está co servidor",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        OutlinedTextField(
                            value = estado.busca,
                            onValueChange = viewModel::cambiarBusca,
                            placeholder = { Text("Buscar alumno...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    items(filtrados, key = { it.alumno_id }) { alumno ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (estado.modoFoiraDeLina) {
                                        Toast.makeText(contexto, "A ficha do alumno só está dispoñible con conexión ao servidor.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onAlumnoClick(alumno.alumno_id)
                                    }
                                }
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(alumno.nome_completo, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}
