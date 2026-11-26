package com.notazas.mobil.ui.screens.caderno

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notazas.mobil.datos.modelos.UsuarioSelector
import com.notazas.mobil.ui.componentes.abreviaturaMateria

/**
 * Caderno por GRUPO: o único filtro é o curso/grupo ("1ºA"). Cada alumno amosa chips de cor coa
 * nota final de cada unha das súas materias; ao tocalo ábrese a súa ficha con todas as notas,
 * probas e traballos (ver NotasAlumnoScreen).
 */
@Composable
fun CadernoScreen(
    usuario: UsuarioSelector,
    onAlumnoClick: (Int) -> Unit = {},
    viewModel: CadernoViewModel = viewModel()
) {
    val estado = viewModel.estado
    // Alumno cuxa ficha está aberta; null = lista do grupo.
    var alumnoAberto by rememberSaveable { mutableStateOf<Int?>(null) }

    // Ao entrar: segue o grupo e o trimestre elixidos noutras pantallas e, sen conexión, volve ler a
    // caché do disco (pode ter cambiado: Actitude, Destrezas, refresco de arranque).
    LaunchedEffect(Unit) { viewModel.sincronizarSeleccionGardada() }

    Column(Modifier.fillMaxSize()) {
        val aberto = alumnoAberto
        if (aberto != null) {
            val verFicha: (() -> Unit)? = if (!estado.modoFoiraDeLina) ({ onAlumnoClick(aberto) }) else null
            NotasAlumnoScreen(
                estado = estado,
                viewModel = viewModel,
                alumnoId = aberto,
                onAlumnoCambiado = { alumnoAberto = it },
                onVoltar = { alumnoAberto = null },
                onVerFicha = verFicha
            )
        } else {
            ListaGrupo(estado, viewModel, onAbrir = { alumnoAberto = it })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListaGrupo(estado: CadernoUiState, viewModel: CadernoViewModel, onAbrir: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (estado.grupos.isNotEmpty()) {
                SelectorGrupo(estado.grupos, estado.grupoSeleccionadoId, viewModel::seleccionarGrupo)
            }
            if (estado.trimestres.isNotEmpty()) {
                SelectorTrimestre(estado.trimestres, estado.trimestreSeleccionado, viewModel::seleccionarTrimestre)
            }
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

            estado.grupos.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Aínda non tes ningunha materia.", style = MaterialTheme.typography.bodyLarge)
                }
            }

            else -> {
                val alumnosFiltrados = estado.alumnos.filter {
                    estado.busca.isBlank() || it.nomeCompleto.contains(estado.busca, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (estado.modoFoiraDeLina) {
                        item {
                            Text(
                                "Modo sen conexión${estado.datosDe?.let { " · datos do $it" }.orEmpty()} · os cambios gárdanse no móbil e sincronízanse despois",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item { ResumoMaterias(estado.materias, estado::corDe) }

                    item {
                        OutlinedTextField(
                            value = estado.busca,
                            onValueChange = viewModel::cambiarBusca,
                            placeholder = { Text("Buscar alumno...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    items(alumnosFiltrados, key = { it.alumnoId }) { alumno ->
                        TarxetaAlumno(alumno, estado.materias, estado::corDe, onClick = { onAbrir(alumno.alumnoId) })
                    }
                }
            }
        }
    }
}

/** O único filtro do Caderno: o grupo ("1ºA", "2ºB"...). */
@Composable
private fun SelectorGrupo(grupos: List<GrupoCaderno>, seleccionadoId: Int?, onSeleccionar: (GrupoCaderno) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        grupos.forEach { grupo ->
            val activo = grupo.id == seleccionadoId
            val fondo by animateColorAsState(
                if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "fondoGrupo"
            )
            Text(
                grupo.nome,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fondo)
                    .clickable { onSeleccionar(grupo) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

/** Materias do grupo, cada unha da súa cor (lenda do código de cores), coa media da clase. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResumoMaterias(materias: List<MateriaCaderno>, corDe: (String) -> Color) {
    if (materias.isEmpty()) return
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "MATERIAS · MEDIA DA CLASE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                materias.forEach { materia ->
                    val cor = corDe(materia.asignacion.materia)
                    val media = materia.filas.mapNotNull { it.nota_final }.let { if (it.isEmpty()) null else it.average() }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(cor))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${materia.asignacion.materia} · ${media?.let { "%.1f".format(it) } ?: "—"}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

/** Alumno do grupo cun chip de cor por materia coa súa nota final. Tocar abre a súa ficha. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarxetaAlumno(alumno: AlumnoCaderno, materias: List<MateriaCaderno>, corDe: (String) -> Color, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clickable(onClickLabel = "Ver as notas do alumno", onClick = onClick)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(alumno.nomeCompleto, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                materias.forEach { materia ->
                    val fila = materia.filas.firstOrNull { it.alumno_id == alumno.alumnoId } ?: return@forEach
                    ChipNotaMateria(abreviaturaMateria(materia.asignacion.materia), fila.nota_final, corDe(materia.asignacion.materia))
                }
            }
        }
    }
}

/** Chip da cor da materia: abreviatura + nota final (en vermello se está suspensa). */
@Composable
private fun ChipNotaMateria(abreviatura: String, nota: Double?, cor: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(cor.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(6.dp))
        Text(abreviatura, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cor)
        Spacer(Modifier.width(6.dp))
        Text(
            nota?.let { "%.2f".format(it) } ?: "—",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = corNota(nota, MaterialTheme.colorScheme)
        )
    }
}
