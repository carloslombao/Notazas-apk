package com.notazas.mobil.ui.screens.destrezas

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.ui.componentes.IconaSpeaking
import com.notazas.mobil.ui.componentes.SelectorAsignacion
import com.notazas.mobil.ui.theme.ActitudeAmbar
import com.notazas.mobil.ui.theme.FormaPill
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun DestrezasScreen(
    onSair: () -> Unit,
    viewModel: DestrezasViewModel = viewModel()
) {
    val estado = viewModel.estado
    val animar = animarTransicions()
    var enLista by rememberSaveable { mutableStateOf(false) }

    val atras = {
        if (enLista) enLista = false else onSair()
    }
    BackHandler(onBack = atras)

    Column(Modifier.fillMaxSize()) {
        if (estado.modoFoiraDeLina) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Fóra do colexio — traballando sen conexión, sincronizarase soa",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = atras) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = if (enLista) estado.columnaSeleccionada?.nome ?: "Proba Destrezas" else "Proba Destrezas",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedContent(
            targetState = enLista,
            transitionSpec = {
                val duracion = if (animar) 150 else 0
                fadeIn(tween(duracion)) togetherWith fadeOut(tween(duracion))
            },
            modifier = Modifier.weight(1f),
            label = "vista"
        ) { lista ->
            if (lista) {
                ListaAlumnado(estado = estado, onAlumno = viewModel::abrirFolla)
            } else {
                PasoProba(
                    estado = estado,
                    onAsignacion = viewModel::seleccionarAsignacion,
                    onTrimestre = viewModel::seleccionarTrimestre,
                    onColumna = viewModel::seleccionarColumna,
                    onNova = viewModel::abrirNova,
                    onAvaliar = { enLista = true }
                )
            }
        }
    }

    if (estado.mostrarNova) {
        NovaProbaSheet(
            subtitulo = listOfNotNull(
                estado.asignacionSeleccionada?.let { "${it.materia} · ${it.grupo}" },
                estado.trimestreSeleccionado?.nome
            ).joinToString(" · "),
            gardando = estado.gardandoNova,
            erro = estado.erroNova,
            onCrear = viewModel::crearProba,
            onPechar = viewModel::pecharNova
        )
    }

    val folla = estado.folla
    val columna = estado.columnaSeleccionada
    if (folla != null && columna != null) {
        FollaDestrezasSheet(
            folla = folla,
            columna = columna,
            nota = viewModel.notaEnDirecto(folla),
            animar = animar,
            gardando = estado.gardando,
            erro = estado.erroGardar,
            onCela = viewModel::alternarCela,
            onGardar = { viewModel.gardar(paraSeguinte = false) },
            onGardarESeguinte = { viewModel.gardar(paraSeguinte = true) },
            onPechar = viewModel::pecharFolla
        )
    }
}

// ---------------------------------------------------------------
// Paso 1: materia, trimestre e proba
// ---------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasoProba(
    estado: DestrezasUiState,
    onAsignacion: (Asignacion) -> Unit,
    onTrimestre: (Trimestre) -> Unit,
    onColumna: (Int) -> Unit,
    onNova: () -> Unit,
    onAvaliar: () -> Unit
) {
    when {
        estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        estado.asignacions.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                estado.erro ?: "Non tes ningunha materia asignada.",
                color = if (estado.erro != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }

        else -> Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SelectorAsignacion(
                        asignacions = estado.asignacions,
                        seleccionada = estado.asignacionSeleccionada,
                        onSeleccionar = onAsignacion
                    )
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        estado.trimestres.forEach { trimestre ->
                            PillTrimestre(
                                nome = trimestre.nome,
                                seleccionado = trimestre.id == estado.trimestreSeleccionado?.id,
                                onClick = { onTrimestre(trimestre) }
                            )
                        }
                    }
                }
                item {
                    Text(
                        "Probas · ${estado.trimestreSeleccionado?.nome ?: ""}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    estado.cargandoColumnas -> item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }

                    estado.columnas.isEmpty() -> item {
                        Text(
                            "Aínda non hai probas neste trimestre. Crea a primeira.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> items(estado.columnas, key = { it.id }) { columna ->
                        TarxetaProba(
                            columna = columna,
                            seleccionada = columna.id == estado.columnaSeleccionadaId,
                            onClick = { onColumna(columna.id) }
                        )
                    }
                }
                if (estado.erro != null) {
                    item { Text(estado.erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                item {
                    OutlinedButton(onClick = onNova, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Nova proba")
                    }
                }
            }
            Button(
                onClick = onAvaliar,
                enabled = estado.columnaSeleccionadaId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp)
            ) { Text("Avaliar alumnado") }
        }
    }
}

@Composable
private fun PillTrimestre(nome: String, seleccionado: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(FormaPill)
            .background(if (seleccionado) scheme.primary else scheme.surfaceContainer)
            .selectable(selected = seleccionado, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            nome,
            style = MaterialTheme.typography.labelLarge,
            color = if (seleccionado) scheme.onPrimary else scheme.onSurface
        )
    }
}

@Composable
private fun TarxetaProba(columna: ColumnaOut, seleccionada: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val n = columna.palabras.size
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest),
        border = BorderStroke(if (seleccionada) 1.5.dp else 1.dp, if (seleccionada) scheme.primary else scheme.outlineVariant)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = seleccionada, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(columna.nome, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(
                    if (n == 1) "1 palabra" else "$n palabras",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------
// Paso 3: lista do alumnado
// ---------------------------------------------------------------

@Composable
private fun ListaAlumnado(estado: DestrezasUiState, onAlumno: (Int) -> Unit) {
    val avaliados = estado.notas.values.count { it != null }

    Column(Modifier.fillMaxSize()) {
        if (estado.cargandoFolla) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Avaliados $avaliados/${estado.alumnos.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (estado.cargandoNotas) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (estado.erro != null) {
                item { Text(estado.erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            items(estado.alumnos, key = { it.alumno_id }) { alumno ->
                val rexistrado = estado.notas.containsKey(alumno.alumno_id)
                TarxetaAlumnoDestrezas(
                    nome = alumno.nome_completo,
                    nota = estado.notas[alumno.alumno_id],
                    cargando = !rexistrado && estado.cargandoNotas,
                    onClick = { onAlumno(alumno.alumno_id) }
                )
            }
        }
    }
}

@Composable
private fun TarxetaAlumnoDestrezas(nome: String, nota: Double?, cargando: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(nome, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            when {
                nota != null -> Box(
                    Modifier
                        .background(corNota(nota), FormaPill)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${formatar(nota)}/10",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                cargando -> Text("…", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)

                else -> Box(
                    Modifier
                        .background(Color.Transparent, FormaPill)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Sen avaliar", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// Panel de avaliación: táboa de celas palabra x destreza
// ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollaDestrezasSheet(
    folla: FollaDestrezas,
    columna: ColumnaOut,
    nota: Double?,
    animar: Boolean,
    gardando: Boolean,
    erro: String?,
    onCela: (Destreza, Int) -> Unit,
    onGardar: () -> Unit,
    onGardarESeguinte: () -> Unit,
    onPechar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val estadoSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haiSeguinte = folla.posicion < folla.total
    val palabras = columna.palabras.sortedBy { it.orde }

    ModalBottomSheet(
        onDismissRequest = onPechar,
        sheetState = estadoSheet,
        containerColor = scheme.surface
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(folla.nome, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "${columna.nome} · alumno ${folla.posicion} de ${folla.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // Cabeceira fixa: as tres destrezas coa súa ponderación.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    CabeceiraDestreza("Listening", Icons.Filled.Headphones, columna.pct_listening)
                    CabeceiraDestreza("Reading", Icons.Filled.MenuBook, columna.pct_reading)
                    CabeceiraDestreza("Speaking", null, columna.pct_speaking)
                }
                HorizontalDivider(color = scheme.outlineVariant)
            }

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                palabras.forEach { palabra ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            palabra.texto,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Destreza.entries.forEach { destreza ->
                            CelaAcerto(
                                acertada = (destreza to palabra.id) in folla.verdes,
                                animar = animar,
                                descricion = "${palabra.texto}, ${nomeDestreza(destreza)}",
                                onClick = { onCela(destreza, palabra.id) }
                            )
                        }
                    }
                }
            }

            // Pé fixo: total en directo + botóns sempre visibles.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(color = scheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${formatar(nota)} / 10",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (nota != null) corNota(nota) else scheme.outline
                    )
                }
                if (erro != null) {
                    Text(erro, color = scheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (haiSeguinte) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onGardar,
                            enabled = !gardando,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Gardar") }
                        Button(
                            onClick = onGardarESeguinte,
                            enabled = !gardando,
                            modifier = Modifier.weight(1.4f).height(48.dp)
                        ) { Text(if (gardando) "Gardando…" else "Gardar e seguinte") }
                    }
                } else {
                    Button(
                        onClick = onGardar,
                        enabled = !gardando,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(if (gardando) "Gardando…" else "Gardar") }
                }
            }
        }
    }
}

@Composable
private fun CabeceiraDestreza(nome: String, icona: ImageVector?, pct: Double?) {
    Column(
        Modifier
            .width(64.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$nome ${pct?.let { "%.0f".format(it) } ?: ""}%"
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icona != null) {
            Icon(icona, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        } else {
            IconaSpeaking(Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${pct?.let { "%.0f".format(it) } ?: "—"}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Cela táctil (48 dp): vermello = fallo (por defecto), verde = acerto. */
@Composable
private fun CelaAcerto(acertada: Boolean, animar: Boolean, descricion: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fondo by animateColorAsState(
        targetValue = if (acertada) scheme.primary else scheme.errorContainer,
        animationSpec = tween(if (animar) 150 else 0),
        label = "cela"
    )
    Box(
        Modifier
            .width(64.dp)
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(44.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(fondo)
                .selectable(selected = acertada, role = Role.Checkbox, onClick = onClick)
                .semantics { contentDescription = descricion },
            contentAlignment = Alignment.Center
        ) {
            if (acertada) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun nomeDestreza(destreza: Destreza) = when (destreza) {
    Destreza.LISTENING -> "Listening"
    Destreza.READING -> "Reading"
    Destreza.SPEAKING -> "Speaking"
}

// ---------------------------------------------------------------
// Panel: nova proba
// ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaProbaSheet(
    subtitulo: String,
    gardando: Boolean,
    erro: String?,
    onCrear: (String, String?, List<String>, Double, Double, Double) -> Unit,
    onPechar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val estadoSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nome by rememberSaveable { mutableStateOf("") }
    var textoPalabras by rememberSaveable { mutableStateOf("") }
    var pctL by rememberSaveable { mutableStateOf("60") }
    var pctR by rememberSaveable { mutableStateOf("10") }
    var pctS by rememberSaveable { mutableStateOf("30") }
    var dataMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var mostrarCalendario by remember { mutableStateOf(false) }

    val data: LocalDate? = dataMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val palabras = textoPalabras.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val l = pctL.toDoubleOrNull()
    val r = pctR.toDoubleOrNull()
    val s = pctS.toDoubleOrNull()
    val suma = if (l != null && r != null && s != null) l + r + s else null
    val sumaCorrecta = suma != null && kotlin.math.abs(suma - 100.0) < 0.01

    ModalBottomSheet(
        onDismissRequest = onPechar,
        sheetState = estadoSheet,
        containerColor = scheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nova proba de destrezas", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)

            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome") },
                placeholder = { Text("Ex. Unit 4 vocabulary") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = data?.format(FORMATO_DATA).orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Data (opcional)") },
                placeholder = { Text("dd/mm/aaaa") },
                trailingIcon = {
                    if (data != null) {
                        IconButton(onClick = { dataMillis = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar a data")
                        }
                    } else {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = scheme.onSurface,
                    disabledBorderColor = scheme.outline,
                    disabledLabelColor = scheme.onSurfaceVariant,
                    disabledPlaceholderColor = scheme.onSurfaceVariant,
                    disabledTrailingIconColor = scheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Elixir a data") { mostrarCalendario = true }
            )

            OutlinedTextField(
                value = textoPalabras,
                onValueChange = { textoPalabras = it },
                label = { Text("Palabras (unha por liña) · ${palabras.size}") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Ponderación (debe sumar 100)", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoPct("Listening", pctL, { pctL = it }, Modifier.weight(1f))
                CampoPct("Reading", pctR, { pctR = it }, Modifier.weight(1f))
                CampoPct("Speaking", pctS, { pctS = it }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sumaCorrecta) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Suma 100", style = MaterialTheme.typography.bodySmall, color = scheme.primary)
                } else {
                    Text(
                        "Agora suma ${suma?.let { "%.0f".format(it) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error
                    )
                }
            }

            if (erro != null) {
                Text(erro, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPechar, modifier = Modifier.weight(1f).height(48.dp)) { Text("Cancelar") }
                Button(
                    onClick = { onCrear(nome, data?.toString(), palabras, l ?: 0.0, r ?: 0.0, s ?: 0.0) },
                    enabled = !gardando && sumaCorrecta && palabras.isNotEmpty() && nome.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text(if (gardando) "Creando…" else "Crear") }
            }
        }
    }

    if (mostrarCalendario) {
        val estadoData = rememberDatePickerState(initialSelectedDateMillis = dataMillis)
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    dataMillis = estadoData.selectedDateMillis
                    mostrarCalendario = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCalendario = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = estadoData)
        }
    }
}

@Composable
private fun CampoPct(etiqueta: String, valor: String, onCambio: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = valor,
        onValueChange = { novo -> if (novo.length <= 3 && novo.all { it.isDigit() }) onCambio(novo) },
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// ---------------------------------------------------------------
// Axudas
// ---------------------------------------------------------------

/** Colorímetro pedagóxico sobre base 10 (como no Caderno). */
@Composable
private fun corNota(nota: Double): Color = when {
    nota < 5.0 -> MaterialTheme.colorScheme.error
    nota < 7.0 -> ActitudeAmbar
    else -> MaterialTheme.colorScheme.primary
}

private fun formatar(nota: Double?): String = nota?.let { "%.2f".format(it) } ?: "—"

/** Respecta o axuste do sistema "Escala de duración de animacións". */
@Composable
internal fun animarTransicions(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
