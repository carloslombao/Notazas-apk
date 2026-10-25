package com.notazas.mobil.ui.screens.actitude

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.mediaSobre10
import com.notazas.mobil.datos.util.SemanaCurso
import com.notazas.mobil.datos.util.resumoRubrica
import com.notazas.mobil.ui.componentes.SelectorGrupo
import com.notazas.mobil.ui.theme.ActitudeAmbar
import com.notazas.mobil.ui.theme.ActitudeAmbarContainer
import com.notazas.mobil.ui.theme.FormaPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActitudeScreen(viewModel: ActitudeViewModel = viewModel()) {
    val estado = viewModel.estado

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
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectorGrupo(
                grupos = estado.grupos,
                seleccionado = estado.asignacionSeleccionada,
                onSeleccionar = viewModel::seleccionarAsignacion
            )
            SelectorSemana(
                semanas = estado.semanas,
                seleccionada = estado.semanaSeleccionada,
                onSeleccionar = viewModel::seleccionarSemana
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
                val avaliados = estado.avaliacions.values.count { it?.mediaSobre10() != null }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ResumoSemana(
                            avaliados = avaliados,
                            total = estado.alumnos.size,
                            cargando = estado.cargandoAvaliacions
                        )
                    }
                    if (estado.erro != null) {
                        item { Text(estado.erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    items(estado.alumnos, key = { it.alumno_id }) { alumno ->
                        val rexistrado = estado.avaliacions.containsKey(alumno.alumno_id)
                        TarxetaAlumnoActitude(
                            nome = alumno.nome_completo,
                            media = estado.avaliacions[alumno.alumno_id]?.mediaSobre10(),
                            cargando = !rexistrado && estado.cargandoAvaliacions,
                            onClick = { viewModel.abrirFolla(alumno.alumno_id) }
                        )
                    }
                }
            }
        }
    }

    estado.folla?.let { folla ->
        FollaActitudeSheet(
            folla = folla,
            aspectos = estado.aspectos,
            semana = estado.semanaSeleccionada,
            gardando = estado.gardando,
            erro = estado.erroGardar,
            onNivel = viewModel::seleccionarNivel,
            onObservacions = viewModel::cambiarObservacions,
            onGardar = { viewModel.gardar(paraSeguinte = false) },
            onGardarESeguinte = { viewModel.gardar(paraSeguinte = true) },
            onPechar = viewModel::pecharFolla
        )
    }
}

// ---------------------------------------------------------------
// Lista da clase
// ---------------------------------------------------------------

@Composable
private fun SelectorSemana(
    semanas: List<SemanaCurso>,
    seleccionada: SemanaCurso?,
    onSeleccionar: (SemanaCurso) -> Unit
) {
    var expandido by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(FormaPill)
                .clickable { expandido = true }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Semana: ${seleccionada?.etiqueta ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Cambiar de semana")
        }

        DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            semanas.forEach { semana ->
                DropdownMenuItem(
                    text = {
                        Text(
                            semana.etiqueta,
                            fontWeight = if (semana.codigo == seleccionada?.codigo) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expandido = false
                        onSeleccionar(semana)
                    }
                )
            }
        }
    }
}

@Composable
private fun ResumoSemana(avaliados: Int, total: Int, cargando: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Avaliados $avaliados/$total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (cargando) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TarxetaAlumnoActitude(
    nome: String,
    media: Double?,
    cargando: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
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
                media != null -> Box(
                    Modifier
                        .background(corMedia(media), FormaPill)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${formatarMedia(media)}/10",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                cargando -> Text(
                    "…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Box(
                    Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FormaPill)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Sen avaliar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// Panel de rexistro (bottom sheet)
// ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollaActitudeSheet(
    folla: FollaActitude,
    aspectos: List<AspectoActitudeOut>,
    semana: SemanaCurso?,
    gardando: Boolean,
    erro: String?,
    onNivel: (aspectoId: Int, nivel: Int) -> Unit,
    onObservacions: (String) -> Unit,
    onGardar: () -> Unit,
    onGardarESeguinte: () -> Unit,
    onPechar: () -> Unit
) {
    val estadoSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val animar = animacionsActivadas()
    val haiSeguinte = folla.posicion < folla.total

    ModalBottomSheet(
        onDismissRequest = onPechar,
        sheetState = estadoSheet,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            // Só cando cambia de alumno hai transición; os cambios de
            // nivel/observacións dentro do mesmo alumno non animan a folla.
            AnimatedContent(
                targetState = folla,
                contentKey = { it.alumnoId },
                transitionSpec = {
                    val duracion = if (animar) 220 else 0
                    (fadeIn(tween(duracion, delayMillis = if (animar) 60 else 0)) +
                        slideInHorizontally(tween(duracion)) { it / 10 }) togetherWith
                        fadeOut(tween(if (animar) 100 else 0))
                },
                modifier = Modifier.weight(1f, fill = false),
                label = "alumno"
            ) { f ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            f.nome,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Semana ${semana?.etiqueta ?: "—"} · alumno ${f.posicion} de ${f.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    aspectos.forEach { aspecto ->
                        AspectoNiveis(
                            aspecto = aspecto,
                            valor = f.valores[aspecto.id],
                            animar = animar,
                            onNivel = { nivel -> onNivel(aspecto.id, nivel) }
                        )
                    }

                    OutlinedTextField(
                        value = f.observacions,
                        onValueChange = onObservacions,
                        label = { Text("Observacións") },
                        placeholder = { Text("Detalles sobre a actitude desta semana...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    val media = folla.media10
                    Text(
                        "${formatarMedia(media)} / 10",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (media != null) corMedia(media) else MaterialTheme.colorScheme.outline
                    )
                }

                if (erro != null) {
                    Text(erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
private fun AspectoNiveis(
    aspecto: AspectoActitudeOut,
    valor: Int?,
    animar: Boolean,
    onNivel: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            aspecto.nome,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (nivel in 1..5) {
                ChipNivel(
                    nivel = nivel,
                    seleccionado = valor == nivel,
                    animar = animar,
                    onClick = { onNivel(nivel) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Texto do nivel elixido; reserva altura mínima para que a
        // folla non salte ao cambiar entre textos dunha ou dúas liñas.
        AnimatedContent(
            targetState = valor,
            transitionSpec = {
                fadeIn(tween(if (animar) 150 else 0)) togetherWith fadeOut(tween(if (animar) 100 else 0))
            },
            label = "texto-nivel"
        ) { v ->
            Text(
                text = when (v) {
                    null -> "Toca un nivel do 1 ao 5"
                    else -> resumoRubrica(aspecto.nome, v) ?: "Nivel $v de 5"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (v == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
            )
        }
    }
}

@Composable
private fun ChipNivel(
    nivel: Int,
    seleccionado: Boolean,
    animar: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val (fondoSel, textoSel) = coresNivel(nivel)
    val duracion = if (animar) 150 else 0

    val fondo by animateColorAsState(
        if (seleccionado) fondoSel else scheme.surfaceContainerLowest,
        tween(duracion), label = "fondo"
    )
    val texto by animateColorAsState(
        if (seleccionado) textoSel else scheme.onSurfaceVariant,
        tween(duracion), label = "texto"
    )
    val borde by animateColorAsState(
        if (seleccionado) fondoSel else scheme.outlineVariant,
        tween(duracion), label = "borde"
    )
    val forma = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(forma)
            .background(fondo)
            .border(1.dp, borde, forma)
            .selectable(selected = seleccionado, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$nivel",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = texto
        )
    }
}

// ---------------------------------------------------------------
// Cores e axudas
// ---------------------------------------------------------------

/** (fondo, texto) do chip seleccionado — mesmo criterio ca web:
 * 5 verde, 4 verde suave, 3 ámbar, 2 vermello suave, 1 vermello. */
@Composable
private fun coresNivel(nivel: Int): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (nivel) {
        5 -> scheme.primary to scheme.onPrimary
        4 -> scheme.primaryContainer to scheme.onPrimaryContainer
        3 -> ActitudeAmbarContainer to ActitudeAmbar
        2 -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.error to scheme.onError
    }
}

/** Colorímetro pedagóxico sobre base 10 (como no Caderno). */
@Composable
private fun corMedia(media: Double): Color = when {
    media < 5.0 -> MaterialTheme.colorScheme.error
    media < 7.0 -> ActitudeAmbar
    else -> MaterialTheme.colorScheme.primary
}

private fun formatarMedia(media: Double?): String =
    media?.let { "%.1f".format(it) } ?: "—"

/** Respecta o axuste do sistema "Escala de duración de animacións":
 * se está desactivada (0x), os chips e o texto aparecen sen transición. */
@Composable
private fun animacionsActivadas(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
