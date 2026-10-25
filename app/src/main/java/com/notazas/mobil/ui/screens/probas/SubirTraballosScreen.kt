package com.notazas.mobil.ui.screens.probas

import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.ui.componentes.SelectorAsignacion
import com.notazas.mobil.ui.theme.FormaPill
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val VerdeSuave = Color(0xFFDDF3E7)

@Composable
fun SubirTraballosScreen(
    onSair: () -> Unit,
    viewModel: SubirTraballosViewModel = viewModel()
) {
    val estado = viewModel.estado
    val contexto = LocalContext.current
    val animar = animarTransicions()

    val selectorFotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.engadirFotos(uris)
    }
    val abrirGaleria = {
        selectorFotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val atras = {
        when (estado.paso) {
            PasoTraballos.TRABALLO -> onSair()
            PasoTraballos.FOTOS -> viewModel.volverATraballo()
            PasoTraballos.SUBINDO -> Unit
            PasoTraballos.RESULTADO -> {
                viewModel.reiniciar()
                onSair()
            }
        }
    }
    BackHandler(onBack = atras)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (estado.paso != PasoTraballos.SUBINDO) {
                IconButton(onClick = atras) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Text(
                text = when (estado.paso) {
                    PasoTraballos.TRABALLO -> "Subir fotos de traballos"
                    PasoTraballos.FOTOS -> estado.columnaSeleccionada?.nome ?: "Fotos"
                    PasoTraballos.SUBINDO -> "Subindo fotos"
                    PasoTraballos.RESULTADO -> "Resultado"
                },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedContent(
            targetState = estado.paso,
            transitionSpec = {
                val duracion = if (animar) 150 else 0
                fadeIn(tween(duracion)) togetherWith fadeOut(tween(duracion))
            },
            modifier = Modifier.weight(1f),
            label = "paso"
        ) { paso ->
            when (paso) {
                PasoTraballos.TRABALLO -> PasoTraballo(
                    estado = estado,
                    onAsignacion = viewModel::seleccionarAsignacion,
                    onTrimestre = viewModel::seleccionarTrimestre,
                    onColumna = viewModel::seleccionarColumna,
                    onNovo = viewModel::abrirNovo,
                    onContinuar = viewModel::irAFotos
                )

                PasoTraballos.FOTOS -> PasoFotos(
                    estado = estado,
                    onQuitar = viewModel::quitarFoto,
                    onGaleria = { abrirGaleria() },
                    onSubir = { viewModel.subir(contexto) }
                )

                PasoTraballos.SUBINDO -> PasoSubindo(estado = estado, animar = animar)

                PasoTraballos.RESULTADO -> PasoResultado(
                    estado = estado,
                    animar = animar,
                    onAsignar = viewModel::abrirAsignar,
                    onRematar = {
                        viewModel.reiniciar()
                        onSair()
                    }
                )
            }
        }
    }

    if (estado.mostrarNovo) {
        NovoTraballoSheet(
            subtitulo = listOfNotNull(
                estado.asignacionSeleccionada?.let { "${it.materia} · ${it.grupo}" },
                estado.trimestreSeleccionado?.nome
            ).joinToString(" · "),
            gardando = estado.gardandoNovo,
            erro = estado.erroNovo,
            onCrear = viewModel::crearTraballo,
            onPechar = viewModel::pecharNovo
        )
    }

    estado.fotoAAsignar?.let { foto ->
        AsignarAlumnoSheet(
            resultado = foto,
            alumnos = estado.alumnos,
            asignando = estado.asignando,
            erro = estado.erroAsignar,
            onElixir = viewModel::asignar,
            onPechar = viewModel::pecharAsignar
        )
    }
}

// ---------------------------------------------------------------
// Paso 1: materia, trimestre e traballo
// ---------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasoTraballo(
    estado: SubirTraballosUiState,
    onAsignacion: (com.notazas.mobil.datos.modelos.Asignacion) -> Unit,
    onTrimestre: (com.notazas.mobil.datos.modelos.Trimestre) -> Unit,
    onColumna: (Int) -> Unit,
    onNovo: () -> Unit,
    onContinuar: () -> Unit
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
                        "Traballos · ${estado.trimestreSeleccionado?.nome ?: ""}",
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
                            "Aínda non hai traballos neste trimestre. Crea o primeiro.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> items(estado.columnas, key = { it.id }) { columna ->
                        TarxetaTraballo(
                            nome = columna.nome,
                            seleccionado = columna.id == estado.columnaSeleccionadaId,
                            onClick = { onColumna(columna.id) }
                        )
                    }
                }
                if (estado.erro != null) {
                    item { Text(estado.erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                item {
                    OutlinedButton(onClick = onNovo, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Novo traballo")
                    }
                }
            }
            Button(
                onClick = onContinuar,
                enabled = estado.columnaSeleccionadaId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp)
            ) { Text("Elixir fotos") }
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
private fun TarxetaTraballo(nome: String, seleccionado: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest),
        border = BorderStroke(if (seleccionado) 1.5.dp else 1.dp, if (seleccionado) scheme.primary else scheme.outlineVariant)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = seleccionado, onClick = null)
            Spacer(Modifier.width(12.dp))
            Text(nome, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        }
    }
}

// ---------------------------------------------------------------
// Paso 2: fotos da galería
// ---------------------------------------------------------------

@Composable
private fun PasoFotos(
    estado: SubirTraballosUiState,
    onQuitar: (Uri) -> Unit,
    onGaleria: () -> Unit,
    onSubir: () -> Unit
) {
    val fotos = estado.fotos
    Column(Modifier.fillMaxSize()) {
        if (fotos.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Elixe da galería as fotos dos traballos. Que se vexa o QR da mesa en cada unha.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                if (fotos.size == 1) "1 foto elixida" else "${fotos.size} fotos elixidas",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(fotos, key = { it.toString() }) { uri ->
                    MiniaturaLocal(uri = uri, onQuitar = { onQuitar(uri) })
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (estado.erroSubida != null) {
                Text(estado.erroSubida, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onGaleria, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (fotos.isEmpty()) "Elixir fotos" else "Engadir máis fotos")
            }
            Button(
                onClick = onSubir,
                enabled = fotos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Subir e detectar QR")
            }
        }
    }
}

@Composable
private fun MiniaturaLocal(uri: Uri, onQuitar: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Foto elixida",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClickLabel = "Quitar foto", role = Role.Button, onClick = onQuitar),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------
// Paso 3: subida
// ---------------------------------------------------------------

@Composable
private fun PasoSubindo(estado: SubirTraballosUiState, animar: Boolean) {
    val total = estado.totalSubida
    val progreso by animateFloatAsState(
        targetValue = if (total == 0) 0f else estado.subidas.toFloat() / total,
        animationSpec = tween(if (animar) 250 else 0),
        label = "progreso"
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Subindo e detectando QR… ${estado.subidas} de $total",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progreso },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(FormaPill),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Non pechedes a app ata que remate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------
// Paso 4: resultado
// ---------------------------------------------------------------

@Composable
private fun PasoResultado(
    estado: SubirTraballosUiState,
    animar: Boolean,
    onAsignar: (ResultadoFoto) -> Unit,
    onRematar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var mostrarLidas by rememberSaveable { mutableStateOf(false) }

    val semQr = estado.resultados.filter { !it.qrAtopado }
    val lidasPorQr = estado.resultados.filter { it.qrAtopado }
    val asignadas = estado.resultados.count { it.alumnoId != null }
    val pendentes = estado.resultados.count { it.alumnoId == null }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(FormaPill)
                            .background(scheme.primary)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            if (asignadas == 1) "1 asignada" else "$asignadas asignadas",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onPrimary
                        )
                    }
                    if (pendentes > 0) {
                        Box(
                            Modifier
                                .clip(FormaPill)
                                .background(scheme.errorContainer)
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "$pendentes sen QR",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            items(semQr, key = { it.clave }) { resultado ->
                FilaSenQr(resultado = resultado, animar = animar, onAsignar = { onAsignar(resultado) })
            }

            if (lidasPorQr.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceContainerLowest)
                            .clickable(onClickLabel = "Amosar ou agochar as recoñecidas") { mostrarLidas = !mostrarLidas }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${lidasPorQr.size} recoñecidas",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (mostrarLidas) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }
                if (mostrarLidas) {
                    items(lidasPorQr, key = { it.clave }) { resultado ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiniaturaServidor(resultado, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(resultado.nomeAlumno ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Button(
            onClick = onRematar,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(48.dp)
        ) { Text("Rematar") }
    }
}

@Composable
private fun FilaSenQr(resultado: ResultadoFoto, animar: Boolean, onAsignar: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val asignada = resultado.alumnoId != null
    val fondo by animateColorAsState(
        targetValue = if (asignada) VerdeSuave else scheme.errorContainer,
        animationSpec = tween(if (animar) 200 else 0),
        label = "fondo"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fondo)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniaturaServidor(resultado, 72.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (asignada) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        resultado.nomeAlumno ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.primary
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "QR non recoñecido",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onErrorContainer
                    )
                }
                OutlinedButton(onClick = onAsignar, modifier = Modifier.height(40.dp)) {
                    Text("Asignar alumno")
                }
            }
        }
    }
}

@Composable
private fun MiniaturaServidor(resultado: ResultadoFoto, tamano: Dp) {
    val url = if (resultado.pendenteId != null) {
        ApiClient.urlImaxePendente(resultado.pendenteId)
    } else {
        resultado.ficheiroId?.let { ApiClient.urlImaxeFicheiro(it) }
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(tamano)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    )
}

// ---------------------------------------------------------------
// Panel: novo traballo
// ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovoTraballoSheet(
    subtitulo: String,
    gardando: Boolean,
    erro: String?,
    onCrear: (nome: String, data: String?) -> Unit,
    onPechar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val estadoSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nome by rememberSaveable { mutableStateOf("") }
    // Data en milisegundos UTC (é o que devolve o DatePicker); null = sen data.
    var dataMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var mostrarCalendario by remember { mutableStateOf(false) }

    val data: LocalDate? = dataMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

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
            Text("Novo traballo", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)

            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome") },
                placeholder = { Text("Ex. Traballo unidade 3") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Campo que abre o calendario. enabled = false evita o teclado
            // e deixa que o clic chegue ao .clickable do pai.
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

            if (erro != null) {
                Text(erro, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPechar,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Cancelar") }
                Button(
                    onClick = { onCrear(nome, data?.toString()) },
                    enabled = !gardando,
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

private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// ---------------------------------------------------------------
// Panel: asignar unha foto sen QR a un alumno
// ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AsignarAlumnoSheet(
    resultado: ResultadoFoto,
    alumnos: List<FilaAlumnoGradebook>,
    asignando: Boolean,
    erro: String?,
    onElixir: (FilaAlumnoGradebook) -> Unit,
    onPechar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val estadoSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var busca by rememberSaveable { mutableStateOf("") }
    val filtrados = remember(busca, alumnos) {
        val q = normalizar(busca)
        if (q.isBlank()) alumnos else alumnos.filter { normalizar(it.nome_completo).contains(q) }
    }

    ModalBottomSheet(
        onDismissRequest = onPechar,
        sheetState = estadoSheet,
        containerColor = scheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Asignar foto a un alumno", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

            AsyncImage(
                model = resultado.pendenteId?.let { ApiClient.urlImaxePendente(it) },
                contentDescription = "Foto sen QR recoñecido",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainer)
            )

            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Buscar alumno") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (erro != null) {
                Text(erro, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (asignando) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary)
            }

            if (filtrados.isEmpty()) {
                Text("Ningún alumno coincide.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(filtrados, key = { it.alumno_id }) { alumno ->
                        Text(
                            alumno.nome_completo,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !asignando, role = Role.Button) { onElixir(alumno) }
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// Axudas
// ---------------------------------------------------------------

/** Minúsculas e sen acentos, para buscar "alvarez" e atopar "Álvarez". */
private fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()

/** Respecta o axuste do sistema "Escala de duración de animacións". */
@Composable
private fun animarTransicions(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
