package com.notazas.mobil.ui.screens.probas

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.OpcionRecuperacion
import com.notazas.mobil.datos.modelos.ResultadoSegmentoExame
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.ui.componentes.SelectorAsignacion
import com.notazas.mobil.ui.theme.ActitudeAmbar
import com.notazas.mobil.ui.theme.FormaPill
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun SubirExameScreen(
    onSair: () -> Unit,
    viewModel: SubirExameViewModel = viewModel()
) {
    val estado = viewModel.estado
    val contexto = LocalContext.current

    val selectorPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.subirPdf(contexto, uri)
    }

    val atras = {
        when (estado.paso) {
            PasoExame.PROBA -> onSair()
            PasoExame.SUBINDO -> Unit
            PasoExame.RESULTADO -> {
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
            if (estado.paso != PasoExame.SUBINDO) {
                IconButton(onClick = atras) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Text(
                text = when (estado.paso) {
                    PasoExame.PROBA -> "Subir exame"
                    PasoExame.SUBINDO -> "Lendo o PDF…"
                    PasoExame.RESULTADO -> estado.columnaSeleccionadaNome ?: "Resultado"
                },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        when (estado.paso) {
            PasoExame.PROBA -> PasoProba(
                estado = estado,
                onAsignacion = viewModel::seleccionarAsignacion,
                onTrimestre = viewModel::seleccionarTrimestre,
                onModo = viewModel::cambiarModo,
                onExameOrixinal = viewModel::seleccionarExameOrixinal,
                onColumna = viewModel::seleccionarColumna,
                onNovo = viewModel::abrirNovo,
                onElixirPdf = { selectorPdf.launch(arrayOf("application/pdf")) }
            )

            PasoExame.SUBINDO -> PasoSubindo()

            PasoExame.RESULTADO -> PasoResultado(
                estado = estado,
                onAsignar = viewModel::abrirAsignar,
                onVerPdf = { viewModel.verPdfPendente(contexto, it) },
                onResolver = viewModel::resolverRecuperacion,
                onRematar = {
                    viewModel.reiniciar()
                    onSair()
                }
            )
        }
    }

    if (estado.mostrarNovo) {
        NovaColumnaSheet(
            esRecuperacion = estado.modo == ModoExame.RECUPERACION,
            subtitulo = listOfNotNull(
                estado.asignacionSeleccionada?.let { "${it.materia} · ${it.grupo}" },
                estado.trimestreSeleccionado?.nome
            ).joinToString(" · "),
            gardando = estado.gardandoNovo,
            erro = estado.erroNovo,
            onCrear = viewModel::crearColumna,
            onPechar = viewModel::pecharNovo
        )
    }

    estado.resultadoAAsignar?.let { resultado ->
        AsignarAlumnoExameSheet(
            resultado = resultado,
            alumnos = estado.alumnos,
            nota = estado.notaAsignar,
            asignando = estado.asignando,
            erro = estado.erroAsignar,
            onCambiarNota = viewModel::cambiarNotaAsignar,
            onElixir = viewModel::asignar,
            onVerPdf = { viewModel.verPdfPendente(contexto, resultado) },
            onPechar = viewModel::pecharAsignar
        )
    }
}

// ---------------------------------------------------------------
// Paso 1: materia, trimestre, modo (exame/recuperación) e proba
// ---------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasoProba(
    estado: SubirExameUiState,
    onAsignacion: (Asignacion) -> Unit,
    onTrimestre: (Trimestre) -> Unit,
    onModo: (ModoExame) -> Unit,
    onExameOrixinal: (Int) -> Unit,
    onColumna: (Int) -> Unit,
    onNovo: () -> Unit,
    onElixirPdf: () -> Unit
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

        else -> {
            // Pode elixirse PDF cando: modo Exame con exame elixido, ou
            // modo Recuperación cunha recuperación concreta elixida.
            val podeElixirPdf = estado.columnaSeleccionadaId != null

            Column(Modifier.fillMaxSize()) {
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            estado.trimestres.forEach { trimestre ->
                                PillSeleccionable(
                                    texto = trimestre.nome,
                                    seleccionado = trimestre.id == estado.trimestreSeleccionado?.id,
                                    onClick = { onTrimestre(trimestre) }
                                )
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(FormaPill)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(4.dp)
                        ) {
                            SegmentoModo("Exame", estado.modo == ModoExame.EXAME, Modifier.weight(1f)) { onModo(ModoExame.EXAME) }
                            SegmentoModo("Recuperación", estado.modo == ModoExame.RECUPERACION, Modifier.weight(1f)) { onModo(ModoExame.RECUPERACION) }
                        }
                    }

                    if (estado.modo == ModoExame.RECUPERACION) {
                        item {
                            Text(
                                "Recuperación de…",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (estado.columnasExame.isEmpty() && !estado.cargandoColumnas) {
                            item { Text("Aínda non hai ningún exame creado neste trimestre.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(estado.columnasExame, key = { "orix-${it.id}" }) { exame ->
                                TarxetaSeleccionable(
                                    nome = exame.nome,
                                    seleccionada = exame.id == estado.examOrixinalSeleccionadoId,
                                    onClick = { onExameOrixinal(exame.id) }
                                )
                            }
                        }
                        if (estado.examOrixinalSeleccionadoId != null) {
                            item {
                                Text(
                                    "Columna de recuperación",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (estado.columnasRecuperacion.isEmpty()) {
                                item { Text("Aínda non hai ningunha recuperación creada para este exame.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            } else {
                                items(estado.columnasRecuperacion, key = { "recup-${it.columna_id}" }) { opcion ->
                                    TarxetaSeleccionable(
                                        nome = opcion.nome,
                                        seleccionada = opcion.columna_id == estado.columnaSeleccionadaId,
                                        onClick = { onColumna(opcion.columna_id) }
                                    )
                                }
                            }
                            item {
                                OutlinedButton(onClick = onNovo, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Nova recuperación")
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                "Exames · ${estado.trimestreSeleccionado?.nome ?: ""}",
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
                            estado.columnasExame.isEmpty() -> item {
                                Text("Aínda non hai exames neste trimestre. Crea o primeiro.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> items(estado.columnasExame, key = { it.id }) { exame ->
                                TarxetaSeleccionable(
                                    nome = exame.nome,
                                    seleccionada = exame.id == estado.columnaSeleccionadaId,
                                    onClick = { onColumna(exame.id) }
                                )
                            }
                        }
                        item {
                            OutlinedButton(onClick = onNovo, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Novo exame")
                            }
                        }
                    }
                    if (estado.erro != null) {
                        item { Text(estado.erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (estado.erroSubida != null) {
                        item { Text(estado.erroSubida, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Button(
                    onClick = onElixirPdf,
                    enabled = podeElixirPdf,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Elixir PDF escaneado")
                }
            }
        }
    }
}

@Composable
private fun SegmentoModo(texto: String, seleccionado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(FormaPill)
            .background(if (seleccionado) scheme.primary else Color.Transparent)
            .selectable(selected = seleccionado, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = if (seleccionado) scheme.onPrimary else scheme.onSurfaceVariant)
    }
}

@Composable
private fun PillSeleccionable(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(FormaPill)
            .background(if (seleccionado) scheme.primary else scheme.surfaceContainer)
            .selectable(selected = seleccionado, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = if (seleccionado) scheme.onPrimary else scheme.onSurface)
    }
}

@Composable
private fun TarxetaSeleccionable(nome: String, seleccionada: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest),
        border = BorderStroke(if (seleccionada) 1.5.dp else 1.dp, if (seleccionada) scheme.primary else scheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = seleccionada, onClick = null)
            Spacer(Modifier.width(12.dp))
            Text(nome, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        }
    }
}

// ---------------------------------------------------------------
// Paso 2: subindo
// ---------------------------------------------------------------

@Composable
private fun PasoSubindo() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Lendo os códigos QR e as notas manuscritas…", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pode tardar un chisco cun PDF longo. Non pechedes a app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------
// Paso 3: resultado
// ---------------------------------------------------------------

@Composable
private fun PasoResultado(
    estado: SubirExameUiState,
    onAsignar: (ResultadoSegmentoExame) -> Unit,
    onVerPdf: (ResultadoSegmentoExame) -> Unit,
    onResolver: (RecuperacionPendente, String) -> Unit,
    onRematar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val senQr = estado.resultados.filter { it.pendente_id != null }
    val recoñecidos = estado.resultados.filter { it.pendente_id == null }
    val pendentesSenResolver = estado.recuperacionsPendentes.filter { it.alumnoId !in estado.recuperacionsResoltas }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(FormaPill).background(scheme.primary).padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Text(if (recoñecidos.size == 1) "1 recoñecida" else "${recoñecidos.size} recoñecidas", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
                    }
                    if (senQr.isNotEmpty()) {
                        Box(Modifier.clip(FormaPill).background(scheme.errorContainer).padding(horizontal = 14.dp, vertical = 7.dp)) {
                            Text("${senQr.size} sen QR", style = MaterialTheme.typography.labelLarge, color = scheme.onErrorContainer)
                        }
                    }
                }
                if (estado.paxinasSenAsignar > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${estado.paxinasSenAsignar} páxina(s) do PDF non se puideron asociar a ningún exame.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (pendentesSenResolver.isNotEmpty()) {
                item {
                    Text("Recuperacións por resolver", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                items(pendentesSenResolver, key = { it.alumnoId }) { pendente ->
                    TarxetaRecuperacionPendente(
                        pendente = pendente,
                        resolvendo = pendente.alumnoId in estado.resolvendoRecuperacion,
                        onSubstituir = { onResolver(pendente, "recuperacion") },
                        onMedia = { onResolver(pendente, "media") }
                    )
                }
                if (estado.erroResolver != null) {
                    item { Text(estado.erroResolver, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }

            items(senQr, key = { it.pendente_id ?: it.hashCode() }) { resultado ->
                TarxetaSenQr(resultado = resultado, onAsignar = { onAsignar(resultado) }, onVerPdf = { onVerPdf(resultado) })
            }

            if (recoñecidos.isNotEmpty()) {
                item {
                    Text("Recoñecidas", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                items(recoñecidos, key = { "ok-${it.alumno_id}-${it.ficheiro_id}" }) { resultado ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(resultado.nome_alumno ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Box(Modifier.clip(FormaPill).background(scheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(
                                resultado.nota_lida?.let { "%.2f".format(it) } ?: "—",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = scheme.onPrimaryContainer
                            )
                        }
                        if (resultado.xa_tina_nota) {
                            Spacer(Modifier.width(6.dp))
                            Text("(substituída)", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Button(onClick = onRematar, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp)) { Text("Rematar") }
    }
}

@Composable
private fun TarxetaSenQr(resultado: ResultadoSegmentoExame, onAsignar: () -> Unit, onVerPdf: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.errorContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("QR non recoñecido", style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onVerPdf, modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ver páxina", style = MaterialTheme.typography.labelMedium)
                }
                Button(onClick = onAsignar, modifier = Modifier.height(36.dp)) {
                    Text("Asignar alumno", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TarxetaRecuperacionPendente(
    pendente: RecuperacionPendente,
    resolvendo: Boolean,
    onSubstituir: () -> Unit,
    onMedia: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val media = (pendente.valorOrixinal + pendente.valorRecuperacion) / 2.0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerLowest).padding(14.dp)
    ) {
        Text(pendente.nome, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
        Text(
            "Orixinal %.2f · Recuperación %.2f".format(pendente.valorOrixinal, pendente.valorRecuperacion),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        if (resolvendo) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onMedia, modifier = Modifier.weight(1f).height(44.dp)) {
                    Text("Media (%.2f)".format(media), style = MaterialTheme.typography.labelMedium)
                }
                Button(onClick = onSubstituir, modifier = Modifier.weight(1f).height(44.dp)) {
                    Text("Substituír (%.2f)".format(pendente.valorRecuperacion), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// Panel: nova columna (exame ou recuperación)
// ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaColumnaSheet(
    esRecuperacion: Boolean,
    subtitulo: String,
    gardando: Boolean,
    erro: String?,
    onCrear: (nome: String, data: String?) -> Unit,
    onPechar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val estadoSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nome by rememberSaveable { mutableStateOf("") }
    var dataMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var mostrarCalendario by remember { mutableStateOf(false) }
    val data: LocalDate? = dataMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    ModalBottomSheet(onDismissRequest = onPechar, sheetState = estadoSheet, containerColor = scheme.surface) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (esRecuperacion) "Nova recuperación" else "Novo exame", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)

            OutlinedTextField(
                value = nome, onValueChange = { nome = it },
                label = { Text("Nome") },
                placeholder = { Text(if (esRecuperacion) "Ex. Recuperación Unidade 3" else "Ex. Exame Unidade 3") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = data?.format(FORMATO_DATA_EXAME).orEmpty(),
                onValueChange = {}, readOnly = true, enabled = false,
                label = { Text("Data (opcional)") },
                placeholder = { Text("dd/mm/aaaa") },
                trailingIcon = {
                    if (data != null) {
                        IconButton(onClick = { dataMillis = null }) { Icon(Icons.Filled.Close, contentDescription = "Quitar a data") }
                    } else {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = scheme.onSurface, disabledBorderColor = scheme.outline,
                    disabledLabelColor = scheme.onSurfaceVariant, disabledPlaceholderColor = scheme.onSurfaceVariant,
                    disabledTrailingIconColor = scheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Elixir a data") { mostrarCalendario = true }
            )
            if (erro != null) Text(erro, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPechar, modifier = Modifier.weight(1f).height(48.dp)) { Text("Cancelar") }
                Button(
                    onClick = { onCrear(nome, data?.toString()) },
                    enabled = !gardando, modifier = Modifier.weight(1f).height(48.dp)
                ) { Text(if (gardando) "Creando…" else "Crear") }
            }
        }
    }

    if (mostrarCalendario) {
        val estadoData = rememberDatePickerState(initialSelectedDateMillis = dataMillis)
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = { TextButton(onClick = { dataMillis = estadoData.selectedDateMillis; mostrarCalendario = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { mostrarCalendario = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estadoData) }
    }
}

private val FORMATO_DATA_EXAME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// ---------------------------------------------------------------
// Panel: asignar unha páxina sen QR a un alumno (con nota manual)
// ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AsignarAlumnoExameSheet(
    resultado: ResultadoSegmentoExame,
    alumnos: List<FilaAlumnoGradebook>,
    nota: String,
    asignando: Boolean,
    erro: String?,
    onCambiarNota: (String) -> Unit,
    onElixir: (FilaAlumnoGradebook) -> Unit,
    onVerPdf: () -> Unit,
    onPechar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val estadoSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var busca by rememberSaveable { mutableStateOf("") }
    val filtrados = remember(busca, alumnos) {
        val q = normalizarTexto(busca)
        if (q.isBlank()) alumnos else alumnos.filter { normalizarTexto(it.nome_completo).contains(q) }
    }

    ModalBottomSheet(onDismissRequest = onPechar, sheetState = estadoSheet, containerColor = scheme.surface) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Asignar páxina a un alumno", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

            OutlinedButton(onClick = onVerPdf, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ver a páxina do PDF")
            }

            OutlinedTextField(
                value = nota, onValueChange = onCambiarNota,
                label = { Text("Nota (0-10)") }, placeholder = { Text("Ex. 7,5") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = busca, onValueChange = { busca = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Buscar alumno") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            if (erro != null) Text(erro, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            if (asignando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary)

            if (filtrados.isEmpty()) {
                Text("Ningún alumno coincide.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(280.dp)) {
                    items(filtrados, key = { it.alumno_id }) { alumno ->
                        Text(
                            alumno.nome_completo, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !asignando, role = Role.Button) { onElixir(alumno) }
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun normalizarTexto(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
