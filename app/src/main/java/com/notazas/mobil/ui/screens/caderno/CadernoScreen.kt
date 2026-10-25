package com.notazas.mobil.ui.screens.caderno

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.UsuarioSelector
import com.notazas.mobil.ui.componentes.SelectorAsignacion

@Composable
fun CadernoScreen(
    usuario: UsuarioSelector,
    onAlumnoClick: (Int) -> Unit = {},
    viewModel: CadernoViewModel = viewModel()
) {
    val estado = viewModel.estado
    val scopeDesglose = rememberCoroutineScope()
    // Alumno cuxas notas (columna a columna) están abertas; null = lista da clase.
    var alumnoAberto by rememberSaveable { mutableStateOf<Int?>(null) }
    // Par (alumnoId, nome) do alumno cuxo desglose de TODAS as materias está aberto.
    var desglose by rememberSaveable { mutableStateOf<Pair<Int, String>?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (alumnoAberto != null) {
            NotasAlumnoScreen(
                estado = estado,
                viewModel = viewModel,
                alumnoId = alumnoAberto!!,
                onAlumnoCambiado = { alumnoAberto = it },
                onVoltar = { alumnoAberto = null },
                onVerTodasMaterias = { nome -> desglose = alumnoAberto!! to nome }
            )
        } else {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                SelectorAsignacion(
                    asignacions = estado.asignacions,
                    seleccionada = estado.asignacionSeleccionada,
                    onSeleccionar = viewModel::seleccionarAsignacion
                )
            }

            when {
                estado.cargando && estado.filas.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                estado.erro != null && estado.filas.isEmpty() -> {
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
                    val filasFiltradas = estado.filas.filter {
                        estado.busca.isBlank() || it.nome_completo.contains(estado.busca, ignoreCase = true)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        item {
                            if (estado.modoFoiraDeLina) {
                                Text(
                                    "Modo sen conexión${estado.datosDe?.let { " · datos do $it" }.orEmpty()} · os cambios gárdanse no móbil e sincronízanse despois",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            ResumoGrupo(filas = estado.filas)
                            Spacer(Modifier.height(16.dp))
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

                        items(filasFiltradas, key = { it.alumno_id }) { fila ->
                            TarxetaAlumno(
                                fila,
                                onClick = { if (!estado.modoFoiraDeLina) onAlumnoClick(fila.alumno_id) else alumnoAberto = fila.alumno_id },
                                onClickNota = { alumnoAberto = fila.alumno_id }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }

    desglose?.let { (alumnoId, nome) ->
        val estadoDesglose = remember(alumnoId) {
            DesgloseAlumnoState(scopeDesglose, alumnoId, nome, estado.asignacionSeleccionada?.grupo.orEmpty())
        }
        DesgloseAlumnoSheet(estado = estadoDesglose, onPechar = { desglose = null })
    }
}

@Composable
private fun ResumoGrupo(filas: List<FilaAlumnoGradebook>) {
    val avaliados = filas.count { it.nota_final != null }
    val media = filas.mapNotNull { it.nota_final }.let { if (it.isEmpty()) null else it.average() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DatoResumo("Media Grupo", media?.let { "%.1f".format(it) } ?: "—")
            DatoResumo("Avaliados", "$avaliados/${filas.size}")
        }
    }
}

@Composable
private fun DatoResumo(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            valor,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun TarxetaAlumno(fila: FilaAlumnoGradebook, onClick: () -> Unit, onClickNota: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                fila.nome_completo,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            // A actitude vén en base 10 (media semanal escalada), non sobre 5.
            fila.actitude_media?.let {
                Text(
                    "★ %.1f/10".format(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
            }

            // Clickable propio: abre as notas do alumno sen disparar tamén
            // o clic da tarxeta (que leva ao perfil completo do alumno).
            Box(
                modifier = Modifier
                    .clickable(onClickLabel = "Ver e editar as notas", onClick = onClickNota)
                    .background(corDaNota(fila.nota_final), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    fila.nota_final?.let { "%.2f".format(it) } ?: "—",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

/** Colorímetro pedagóxico: verde notas altas, vermello suspensos. */
@Composable
private fun corDaNota(valor: Double?): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        valor == null -> scheme.outline
        valor < 5.0 -> scheme.error
        valor < 7.0 -> Color(0xFF8A5300) // ámbar, coma ActitudeAmbar
        else -> scheme.primary
    }
}
