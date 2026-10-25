package com.notazas.mobil.ui.screens.caderno

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.MateriaDetalleTrimestreOut
import com.notazas.mobil.datos.modelos.NotaColumnaDetalleOut
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.util.SemanaCurso
import com.notazas.mobil.datos.util.resumoRubrica
import com.notazas.mobil.ui.componentes.IconaSpeaking
import com.notazas.mobil.ui.theme.ActitudeAmbar
import com.notazas.mobil.ui.theme.ActitudeAmbarContainer
import com.notazas.mobil.ui.theme.ExameAzul
import com.notazas.mobil.ui.theme.ExameAzulContainer
import com.notazas.mobil.ui.theme.FormaPill
import com.notazas.mobil.ui.theme.TraballoMorado
import com.notazas.mobil.ui.theme.TraballoMoradoContainer

/**
 * Panel de desglose de notas dun alumno: exames, traballos, destrezas
 * e actitude semanal, cruzando todas as materias do seu grupo e os 3
 * trimestres. Deseñado en Google Stitch e traído a Compose seguindo o
 * mockup canto foi posible.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DesgloseAlumnoSheet(estado: DesgloseAlumnoState, onPechar: () -> Unit) {
    val ui = estado.estado
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onPechar,
        sheetState = sheetState,
        containerColor = scheme.surface
    ) {
        Column(Modifier.fillMaxWidth().fillMaxSize(0.001f)) { /* forza medida inicial; ver contido real abaixo */ }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // 1. Cabeceira
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(ui.nomeAlumno, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    if (ui.grupo.isNotBlank()) {
                        Text(ui.grupo, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onPechar) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Pechar")
                }
            }
            Spacer(Modifier.height(8.dp))

            // 2. Selector de trimestre
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(scheme.surfaceContainerLow, FormaPill)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ui.trimestres.forEach { trimestre ->
                    PillTrimestreDesglose(
                        nome = trimestre.nome,
                        seleccionado = trimestre.id == ui.trimestreSeleccionado?.id,
                        onClick = { estado.seleccionarTrimestre(trimestre) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                ui.cargandoBase -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = scheme.primary)
                }

                else -> {
                    // 3. Filtro de materia
                    SelectorMaterias(ui = ui, estado = estado)
                    Spacer(Modifier.height(12.dp))

                    if (ui.cargandoTrimestre && ui.materiasDoTrimestre.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = scheme.primary)
                        }
                    } else if (ui.erro != null && ui.materiasDoTrimestre.isEmpty()) {
                        Text(ui.erro, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val materias = ui.materiasFiltradas
                        val semanasTrimestre = ui.trimestreSeleccionado?.let { t ->
                            ui.actitude.filter { it.trimestre_id == t.id }.sortedBy { it.semana }
                        }.orEmpty()

                        if (materias.isEmpty() && semanasTrimestre.isEmpty()) {
                            EstadoBaleiro()
                        } else {
                            // 4. Tarxeta de resumo
                            TarxetaResumoDesglose(materias = materias, semanas = semanasTrimestre)
                            Spacer(Modifier.height(16.dp))

                            // 5. Lista de notas por materia
                            materias.forEach { materia ->
                                GrupoMateria(materia = materia, mostrarCabeceira = materias.size > 1)
                                Spacer(Modifier.height(16.dp))
                            }

                            // 6. Actitude (unha soa vez, compartida por todas as materias)
                            if (semanasTrimestre.isNotEmpty()) {
                                SeccionActitude(
                                    trimestre = ui.trimestreSeleccionado,
                                    semanas = semanasTrimestre,
                                    aspectos = ui.aspectos,
                                    semanasCurso = ui.semanasCurso
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillTrimestreDesglose(nome: String, seleccionado: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(FormaPill)
            .background(if (seleccionado) scheme.primary else Color.Transparent)
            .selectable(selected = seleccionado, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            nome,
            style = MaterialTheme.typography.labelLarge,
            color = if (seleccionado) scheme.onPrimary else scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectorMaterias(ui: DesgloseUiState, estado: DesgloseAlumnoState) {
    val scheme = MaterialTheme.colorScheme
    val materias = ui.materiasDoTrimestre
    val seleccionadas = ui.filtroAsignacionIds ?: materias.map { it.asignacion_id }.toSet()
    val etiqueta = when {
        materias.isEmpty() -> "Sen materias"
        ui.filtroAsignacionIds == null -> "Todas as materias"
        seleccionadas.size == 1 -> materias.first { it.asignacion_id in seleccionadas }.materia
        else -> "${seleccionadas.size} materias"
    }

    Box {
        Row(
            Modifier
                .clip(FormaPill)
                .background(scheme.surfaceContainer)
                .clickable(onClickLabel = "Filtrar por materia") { estado.alternarFiltroDespregado() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(etiqueta, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp))
            Icon(
                if (ui.filtroDespregado) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(expanded = ui.filtroDespregado, onDismissRequest = { estado.alternarFiltroDespregado() }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { materias.forEach { m -> if (m.asignacion_id !in seleccionadas) estado.alternarMateria(m.asignacion_id) } }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = seleccionadas.size == materias.size,
                    onCheckedChange = { estado.let { _ ->
                        if (seleccionadas.size == materias.size) {
                            // desmarcar todas: deixa un conxunto baleiro explícito
                            materias.forEach { m -> if (m.asignacion_id in seleccionadas) estado.alternarMateria(m.asignacion_id) }
                        } else {
                            materias.forEach { m -> if (m.asignacion_id !in seleccionadas) estado.alternarMateria(m.asignacion_id) }
                        }
                    } }
                )
                Text("Todas as materias", style = MaterialTheme.typography.bodyMedium)
            }
            materias.forEach { materia ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { estado.alternarMateria(materia.asignacion_id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = materia.asignacion_id in seleccionadas,
                        onCheckedChange = { estado.alternarMateria(materia.asignacion_id) }
                    )
                    Text(materia.materia, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// 4. Tarxeta de resumo
// ---------------------------------------------------------------

@Composable
private fun TarxetaResumoDesglose(materias: List<MateriaDetalleTrimestreOut>, semanas: List<ActitudeSemanalOut>) {
    val scheme = MaterialTheme.colorScheme
    val notasFinais = materias.mapNotNull { it.nota_final }
    val notaConxunta = if (notasFinais.isEmpty()) null else notasFinais.average()
    val mediasActitude = semanas.mapNotNull { it.media }
    val actitudeMedia = if (mediasActitude.isEmpty()) null else mediasActitude.average()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    "NOTA FINAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    notaConxunta?.let { "%.2f".format(it) } ?: "—",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = scheme.primary
                )
                if (notaConxunta != null) {
                    Box(
                        Modifier
                            .clip(FormaPill)
                            .background(scheme.primaryContainer.copy(alpha = 0.25f))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(cualificador(notaConxunta), style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text("Actitude media", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        actitudeMedia?.let { "%.1f".format(it) } ?: "—",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(" / 10", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(
                    if (semanas.size == 1) "1 semana rexistrada" else "${semanas.size} semanas rexistradas",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
        if (materias.size > 1) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "Media conxunta de ${materias.joinToString(", ") { it.materia }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun cualificador(nota: Double): String = when {
    nota >= 9.0 -> "Sobresaliente"
    nota >= 7.0 -> "Notable"
    nota >= 5.0 -> "Ben"
    else -> "Insuficiente"
}

// ---------------------------------------------------------------
// 5. Lista de notas por materia
// ---------------------------------------------------------------

@Composable
private fun GrupoMateria(materia: MateriaDetalleTrimestreOut, mostrarCabeceira: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column {
        if (mostrarCabeceira) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(scheme.primaryContainer.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    materia.materia,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                materia.nota_final?.let {
                    Box(
                        Modifier
                            .clip(FormaPill)
                            .background(scheme.primary)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "%.2f".format(it),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = scheme.onPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (materia.columnas.isEmpty()) {
            Text(
                "Sen probas rexistradas nesta materia.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                materia.columnas.forEach { columna -> TarxetaColumna(columna) }
            }
        }
    }
}

@Composable
private fun TarxetaColumna(columna: NotaColumnaDetalleOut) {
    val scheme = MaterialTheme.colorScheme
    val (icona, corIconaFondo, corIcona) = when (columna.tipo) {
        "exame" -> Triple(Icons.Filled.Description, ExameAzulContainer, ExameAzul)
        "traballo" -> Triple(Icons.Filled.Folder, TraballoMoradoContainer, TraballoMorado)
        "destrezas" -> Triple(Icons.Filled.Headphones, ExameAzulContainer, ExameAzul)
        else -> Triple(Icons.Filled.Assignment, scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerLowest)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(corIconaFondo.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icona, contentDescription = null, tint = corIcona, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                columna.nome,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                columna.data?.let {
                    Text(formatarDataIso(it), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                if (columna.recuperado) {
                    if (columna.data != null) Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(FormaPill)
                            .background(TraballoMoradoContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    ) {
                        Text("Recuperado", style = MaterialTheme.typography.labelSmall, color = TraballoMorado, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            columna.valor != null -> Box(
                Modifier
                    .clip(FormaPill)
                    .background(corNotaColumna(columna.valor))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "%.2f / 10".format(columna.valor),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            else -> Box(
                Modifier
                    .clip(FormaPill)
                    .background(scheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Sen avaliar", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatarDataIso(iso: String): String {
    // iso vén como AAAA-MM-DD; amosámola como "14 de novembro".
    val partes = iso.split("-")
    if (partes.size != 3) return iso
    val meses = listOf("xaneiro", "febreiro", "marzo", "abril", "maio", "xuño", "xullo", "agosto", "setembro", "outubro", "novembro", "decembro")
    val mesIndice = partes[1].toIntOrNull()?.minus(1) ?: return iso
    val dia = partes[2].toIntOrNull() ?: return iso
    return "$dia de ${meses.getOrElse(mesIndice) { partes[1] }}"
}

@Composable
private fun corNotaColumna(nota: Double): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        nota < 5.0 -> scheme.error
        nota < 7.0 -> ActitudeAmbar
        else -> scheme.primary
    }
}

// ---------------------------------------------------------------
// 6. Actitude semanal
// ---------------------------------------------------------------

@Composable
private fun SeccionActitude(
    trimestre: Trimestre?,
    semanas: List<ActitudeSemanalOut>,
    aspectos: List<AspectoActitudeOut>,
    semanasCurso: List<SemanaCurso>
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Actitude — ${trimestre?.nome ?: ""}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .clip(FormaPill)
                    .background(scheme.surfaceContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Seguimento semanal", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            semanas.forEach { semana ->
                FilaSemanaActitude(semana, aspectos, semanasCurso)
            }
        }
    }
}

@Composable
private fun FilaSemanaActitude(semana: ActitudeSemanalOut, aspectos: List<AspectoActitudeOut>, semanasCurso: List<SemanaCurso>) {
    val scheme = MaterialTheme.colorScheme
    var despregada by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val etiqueta = semanasCurso.firstOrNull { it.codigo == semana.semana }?.etiqueta ?: "Semana ${semana.semana % 100}"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerLowest)
            .animateContentSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = semana.media != null) { despregada = !despregada }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(etiqueta, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            if (semana.media != null) {
                Box(
                    Modifier
                        .clip(FormaPill)
                        .background(scheme.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("%.1f / 10".format(semana.media), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = scheme.onPrimary)
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (despregada) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (despregada) "Agochar detalle" else "Ver detalle",
                    tint = scheme.onSurfaceVariant
                )
            } else {
                Box(
                    Modifier
                        .clip(FormaPill)
                        .background(scheme.surfaceContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Sen avaliar", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        if (despregada && semana.media != null) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                semana.detalles.forEach { detalle ->
                    val nomeAspecto = aspectos.firstOrNull { it.id == detalle.aspecto_id }?.nome ?: "Aspecto"
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nomeAspecto, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                            resumoRubrica(nomeAspecto, detalle.valor)?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(5) { i ->
                                Box(
                                    Modifier
                                        .padding(end = 4.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (i < detalle.valor) scheme.primary else scheme.outlineVariant.copy(alpha = 0.6f))
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("${detalle.valor}/5", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// Estado baleiro
// ---------------------------------------------------------------

@Composable
private fun EstadoBaleiro() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(scheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Sen notas rexistradas neste trimestre",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = scheme.onSurface
        )
    }
}
