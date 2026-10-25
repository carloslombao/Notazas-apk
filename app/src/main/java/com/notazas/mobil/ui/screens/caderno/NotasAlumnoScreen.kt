package com.notazas.mobil.ui.screens.caderno

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.OpcionRecuperacion
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.modelos.mediaSobre10
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.datos.util.xerarSemanasCurso
import com.notazas.mobil.ui.screens.destrezas.FollaDestrezasSheet
import com.notazas.mobil.ui.screens.destrezas.animarTransicions
import com.notazas.mobil.ui.screens.perfilalumno.GraficoActitude
import com.notazas.mobil.ui.theme.ActitudeAmbar
import com.notazas.mobil.ui.theme.ActitudeAmbarContainer
import com.notazas.mobil.ui.theme.ExameAzul
import com.notazas.mobil.ui.theme.TraballoMorado
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- Formato ----------

private val localeGl = Locale("gl", "ES")

/** 7,5 / 7,25 / 8 → "7,5" / "7,25" / "8,0" (coma decimal). */
internal fun formatNota(v: Double?): String {
    if (v == null) return "—"
    val unha = (v * 10).roundToInt() / 10.0
    return if (abs(unha - v) < 0.0001) String.format(localeGl, "%.1f", v) else String.format(localeGl, "%.2f", v)
}

private fun formatPct(v: Double): String =
    if (abs(v - v.roundToInt()) < 0.01) "${v.roundToInt()}" else String.format(localeGl, "%.1f", v)

private fun etiquetaNota(v: Double?): String? = when {
    v == null -> null
    v < 5.0 -> "Insuficiente"
    v < 6.0 -> "Suficiente"
    v < 7.0 -> "Ben"
    v < 9.0 -> "Notable"
    else -> "Sobresaliente"
}

private val MESES = listOf(
    "xaneiro", "febreiro", "marzo", "abril", "maio", "xuño",
    "xullo", "agosto", "setembro", "outubro", "novembro", "decembro"
)

/** "2026-11-14" → "14 de novembro". */
private fun formatData(iso: String?): String? {
    val partes = iso?.take(10)?.split('-') ?: return null
    if (partes.size != 3) return null
    val mes = partes[1].toIntOrNull()?.let { MESES.getOrNull(it - 1) } ?: return null
    val dia = partes[2].toIntOrNull() ?: return null
    return "$dia de $mes"
}

private fun corNota(valor: Double?, scheme: androidx.compose.material3.ColorScheme): Color = when {
    valor == null -> scheme.outline
    valor < 5.0 -> scheme.error
    valor < 7.0 -> ActitudeAmbar
    else -> scheme.primary
}

/** Valor que se edita nunha columna: para un exame con recuperación é a
 * nota ORIXINAL (non a efectiva). */
private fun valorEditable(fila: FilaAlumnoGradebook, columna: ColumnaOut): Double? {
    val clave = columna.id.toString()
    val info = fila.recuperacions[clave]
    return if (columna.tipo == "exame" && info != null) info.valor_orixinal else fila.notas[clave]
}

private fun mediaColumnas(fila: FilaAlumnoGradebook, columnas: List<ColumnaOut>, tipos: Set<String>): Double? =
    columnas.filter { it.tipo in tipos }
        .mapNotNull { fila.notas[it.id.toString()] }
        .let { if (it.isEmpty()) null else it.average() }

private data class EdicionNota(
    val columnaId: Int,
    val titulo: String,
    val alumnoId: Int,
    val esRecuperacion: Boolean,
    val exameId: Int? = null
)

// ---------- Pantalla ----------

/** Notas dun alumno, columna a columna, na materia e trimestre
 * seleccionados: resumo da nota final, exames (PDF e criterio de
 * recuperación), traballos (fotos), proba de destrezas (palabras
 * acertadas), edición das notas cun teclado numérico propio e, ao
 * final, a actitude do trimestre. Serve igual en liña ca fóra do
 * colexio, agás os ficheiros (PDF e fotos), que só existen no servidor. */
@Composable
fun NotasAlumnoScreen(
    estado: CadernoUiState,
    viewModel: CadernoViewModel,
    alumnoId: Int,
    onAlumnoCambiado: (Int) -> Unit,
    onVoltar: () -> Unit,
    onVerTodasMaterias: (nome: String) -> Unit
) {
    BackHandler(onBack = onVoltar)
    val contexto = LocalContext.current
    var edicion by remember { mutableStateOf<EdicionNota?>(null) }
    var fotosAbertas by remember { mutableStateOf<List<Int>?>(null) }

    // PDFs, fotos e actitude do alumno (cada vez que cambia alumno, materia ou trimestre).
    LaunchedEffect(alumnoId, estado.asignacionSeleccionada?.id, estado.trimestreSeleccionado?.id, estado.modoFoiraDeLina) {
        viewModel.cargarDetalleAlumno(alumnoId)
    }
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show()
            viewModel.limparAviso()
        }
    }
    // "Gardar e seguinte" no desglose das destrezas: o alumno da cabeceira segue ao do panel.
    LaunchedEffect(estado.folla?.alumnoId) {
        val id = estado.folla?.alumnoId
        if (id != null && id != alumnoId) onAlumnoCambiado(id)
    }

    val fila = estado.filas.firstOrNull { it.alumno_id == alumnoId }
    if (fila == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Non se atopou o alumno nesta materia.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val detalle = estado.detalleAlumno?.takeIf { it.alumnoId == alumnoId }
    val columnas = estado.columnas
    val exames = columnas.filter { it.tipo == "exame" }
    val traballos = columnas.filter { it.tipo == "traballo" }
    val destrezas = columnas.filter { it.tipo == "destrezas" }

    fun abrirPdf(ficheiro: Int?, semPdf: String) {
        when {
            estado.modoFoiraDeLina -> viewModel.mostrarAviso("O PDF do exame só se pode ver co servidor do colexio.")
            detalle == null || detalle.cargando -> viewModel.mostrarAviso("Cargando os ficheiros do alumno…")
            ficheiro == null -> viewModel.mostrarAviso(semPdf)
            else -> viewModel.abrirPdfExame(ficheiro) { intent ->
                try {
                    contexto.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    viewModel.mostrarAviso("Non hai ningunha app para abrir PDFs.")
                }
            }
        }
    }

    fun abrirExame(col: ColumnaOut) = abrirPdf(detalle?.pdfExame?.get(col.id), "Este exame non ten PDF subido.")

    fun abrirFotos(col: ColumnaOut) {
        val fotos = detalle?.fotos?.get(col.id).orEmpty()
        when {
            estado.modoFoiraDeLina -> viewModel.mostrarAviso("As fotos só se poden ver co servidor do colexio.")
            detalle == null || detalle.cargando -> viewModel.mostrarAviso("Cargando os ficheiros do alumno…")
            fotos.isEmpty() -> viewModel.mostrarAviso("Este traballo non ten fotos.")
            else -> fotosAbertas = fotos
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVoltar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
                Column {
                    Text("Caderno", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        estado.asignacionSeleccionada?.let { "${it.materia} · ${it.grupo}" }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (estado.modoFoiraDeLina) {
            item { BannerSenConexion(estado.datosDe) }
        }

        item { CabeceiraAlumno(fila, estado, onVerTodasMaterias = { onVerTodasMaterias(fila.nome_completo) }) }

        item { SelectorTrimestre(estado.trimestres, estado.trimestreSeleccionado, viewModel::seleccionarTrimestre) }

        if (estado.erroGardar != null && edicion == null && estado.folla == null) {
            item {
                Text(
                    estado.erroGardar,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { viewModel.limparErroGardar() }
                )
            }
        }

        item { ResumoNotaFinal(fila, estado) }

        if (columnas.isEmpty()) {
            item {
                Text(
                    "Aínda non hai probas neste trimestre.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            }
        }

        if (exames.isNotEmpty()) {
            item { TituloSeccion("Exames", exames.size, "probas") }
            items(exames.size) { i ->
                val col = exames[i]
                TarxetaColumna(
                    columna = col, fila = fila, estado = estado,
                    ficheiro = if (detalle?.pdfExame?.containsKey(col.id) == true) FichaInfo(Icons.Filled.Description, "PDF") else null,
                    etiquetaAbrir = "Ver o PDF do exame",
                    onAbrir = { abrirExame(col) },
                    onEditar = { edicion = EdicionNota(col.id, col.nome, alumnoId, esRecuperacion = false) },
                    onEditarRecuperacion = { op ->
                        edicion = EdicionNota(op.columna_id, "${op.nome} (${col.nome})", alumnoId, esRecuperacion = true, exameId = col.id)
                    },
                    onAbrirPdfRecuperacion = { op -> abrirPdf(op.ficheiro_id, "Esta recuperación non ten PDF subido.") },
                    onCriterio = { criterio, elixida -> viewModel.cambiarCriterio(col.id, alumnoId, criterio, elixida) }
                )
            }
        }

        if (traballos.isNotEmpty()) {
            item { TituloSeccion("Traballos e Caderno", traballos.size, "entregas") }
            items(traballos.size) { i ->
                val col = traballos[i]
                val nFotos = detalle?.fotos?.get(col.id)?.size ?: 0
                TarxetaColumna(
                    columna = col, fila = fila, estado = estado,
                    ficheiro = if (nFotos > 0) FichaInfo(Icons.Filled.PhotoLibrary, if (nFotos == 1) "1 foto" else "$nFotos fotos") else null,
                    etiquetaAbrir = "Ver as fotos do traballo",
                    onAbrir = { abrirFotos(col) },
                    onEditar = { edicion = EdicionNota(col.id, col.nome, alumnoId, esRecuperacion = false) },
                    onEditarRecuperacion = {}, onCriterio = { _, _ -> }
                )
            }
        }

        if (destrezas.isNotEmpty()) {
            item { TituloSeccion("Proba de Destrezas", destrezas.size, "probas") }
            items(destrezas.size) { i ->
                val col = destrezas[i]
                TarxetaColumna(
                    columna = col, fila = fila, estado = estado,
                    ficheiro = FichaInfo(Icons.Filled.Edit, "Palabras"),
                    etiquetaAbrir = "Ver e editar as palabras acertadas",
                    onAbrir = { viewModel.abrirFollaDestrezas(col.id, alumnoId) },
                    onEditar = { viewModel.abrirFollaDestrezas(col.id, alumnoId) },
                    onEditarRecuperacion = {}, onCriterio = { _, _ -> }
                )
            }
        }

        item { SeccionActitude(detalle, estado.trimestreSeleccionado?.nome.orEmpty()) }

        item { Spacer(Modifier.height(16.dp)) }
    }

    // ----- Teclado numérico (exames, traballos e recuperacións) -----
    edicion?.let { ed ->
        val filaEd = estado.filas.firstOrNull { it.alumno_id == ed.alumnoId }
        val inicial: Double? = if (ed.esRecuperacion) {
            filaEd?.recuperacions?.get(ed.exameId.toString())?.opcions?.firstOrNull { it.columna_id == ed.columnaId }?.valor
        } else {
            columnas.firstOrNull { it.id == ed.columnaId }?.let { c -> filaEd?.let { valorEditable(it, c) } }
        }
        val indice = estado.filas.indexOfFirst { it.alumno_id == ed.alumnoId }
        val seguinte = estado.filas.getOrNull(indice + 1)

        TeclasNotaSheet(
            clave = ed.alumnoId to ed.columnaId,
            titulo = ed.titulo,
            nomeAlumno = filaEd?.nome_completo.orEmpty(),
            grupo = estado.asignacionSeleccionada?.grupo.orEmpty(),
            inicial = inicial,
            podeSeguinte = !ed.esRecuperacion && seguinte != null,
            gardando = estado.gardando,
            erro = estado.erroGardar,
            onPechar = { edicion = null; viewModel.limparErroGardar() },
            onGardar = { valor, paraSeguinte ->
                viewModel.gardarNota(ed.columnaId, ed.alumnoId, valor) {
                    viewModel.marcarGardado(ed.columnaId, ed.alumnoId)
                    if (paraSeguinte && seguinte != null) {
                        edicion = ed.copy(alumnoId = seguinte.alumno_id)
                        onAlumnoCambiado(seguinte.alumno_id)
                    } else {
                        edicion = null
                    }
                }
            }
        )
    }

    // ----- Desglose das palabras acertadas (destrezas) -----
    val folla = estado.folla
    val columnaFolla = columnas.firstOrNull { it.id == estado.follaColumnaId }
    if (folla != null && columnaFolla != null) {
        FollaDestrezasSheet(
            folla = folla,
            columna = columnaFolla,
            nota = viewModel.notaFollaEnDirecto(folla, columnaFolla),
            animar = animarTransicions(),
            gardando = estado.gardando,
            erro = estado.erroGardar,
            onCela = viewModel::alternarCela,
            onGardar = { viewModel.gardarFolla(paraSeguinte = false) },
            onGardarESeguinte = { viewModel.gardarFolla(paraSeguinte = true) },
            onPechar = viewModel::pecharFolla
        )
    }

    // ----- Fotos do traballo -----
    fotosAbertas?.let { ids -> VisorFotos(ids = ids, onPechar = { fotosAbertas = null }) }
}

// ---------- Pezas ----------

@Composable
private fun BannerSenConexion(datosDe: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ActitudeAmbarContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = ActitudeAmbar, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Modo sen conexión", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ActitudeAmbar)
            Text(
                "Datos do ${datosDe ?: "móbil"} · gardado no móbil · pendente de sincronizar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CabeceiraAlumno(fila: FilaAlumnoGradebook, estado: CadernoUiState, onVerTodasMaterias: () -> Unit) {
    val inicial = fila.nome_completo.substringAfter(", ", fila.nome_completo).firstOrNull()?.uppercase() ?: "?"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                inicial,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(fila.nome_completo, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                estado.asignacionSeleccionada?.let { "${it.materia} · ${it.grupo}" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!estado.modoFoiraDeLina) {
            TextButton(onClick = onVerTodasMaterias) { Text("Todas as materias") }
        }
    }
}

@Composable
private fun SelectorTrimestre(trimestres: List<Trimestre>, seleccionado: Trimestre?, onSeleccionar: (Trimestre) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        trimestres.forEach { t ->
            val activo = seleccionado?.id == t.id
            val fondo by animateColorAsState(
                if (activo) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "fondoTrimestre"
            )
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(fondo)
                    .clickable { onSeleccionar(t) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activo) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    "${t.orde}º Trimestre",
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium),
                    color = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResumoNotaFinal(fila: FilaAlumnoGradebook, estado: CadernoUiState) {
    val pond = estado.ponderacion
    val tenTraballos = estado.columnas.any { it.tipo == "traballo" }
    val pctExames = pond?.let { if (tenTraballos) it.pct_exames else it.pct_exames + it.pct_traballos }
    val pctTraballos = pond?.pct_traballos
    val pctActitude = pond?.pct_actitude

    val mediaExames = mediaColumnas(fila, estado.columnas, setOf("exame", "destrezas"))
    val mediaTraballos = mediaColumnas(fila, estado.columnas, setOf("traballo"))
    val cor by animateColorAsState(corNota(fila.nota_final, MaterialTheme.colorScheme), label = "corNotaFinal")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(
                        "NOTA FINAL DO TRIMESTRE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            fila.nota_final?.let { String.format(localeGl, "%.2f", it) } ?: "—",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = cor
                        )
                        Text(
                            " / 10",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
                etiquetaNota(fila.nota_final)?.let { etiqueta ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(cor.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(cor))
                        Spacer(Modifier.width(6.dp))
                        Text(etiqueta, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cor)
                    }
                }
            }

            LiñaMedia("Exames", pctExames, mediaExames, MaterialTheme.colorScheme.primary)
            if (tenTraballos) LiñaMedia("Traballos e Caderno", pctTraballos, mediaTraballos, TraballoMorado)
            LiñaMedia("Actitude e Participación", pctActitude, fila.actitude_media, ActitudeAmbar)

            if (pond == null) {
                Text(
                    "Sen ponderación configurada para este trimestre: a nota final non se pode calcular.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!tenTraballos && pond.pct_traballos > 0) {
                Text(
                    "Regra activa: se non hai traballos, o seu ${formatPct(pond.pct_traballos)}% suma a Exames.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LiñaMedia(etiqueta: String, pct: Double?, media: Double?, cor: Color) {
    val fraccion by animateFloatAsState(
        ((media ?: 0.0) / 10.0).coerceIn(0.0, 1.0).toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "progreso$etiqueta"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (pct != null) "$etiqueta (${formatPct(pct)}%)" else etiqueta,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
            )
            Text(
                "${formatNota(media)} / 10",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (media == null) MaterialTheme.colorScheme.outline else cor
            )
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraccion).clip(RoundedCornerShape(50)).background(cor))
        }
    }
}

@Composable
private fun TituloSeccion(titulo: String, cantidade: Int, unidade: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.width(8.dp))
        Text(
            "$cantidade $unidade",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CaixaNota(valor: Double?, onClick: (() -> Unit)?) {
    val cor by animateColorAsState(corNota(valor, MaterialTheme.colorScheme), label = "corCaixaNota")
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cor.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Editar nota", onClick = onClick) else Modifier)
            .widthIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(formatNota(valor), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = cor)
        Text("/ 10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Indicador pequeno baixo o título dunha proba: "PDF", "3 fotos"... */
private data class FichaInfo(val icona: ImageVector, val texto: String)

/** Tarxeta dunha proba. Tocar a fila abre o seu contido (PDF, fotos ou
 * desglose de palabras); tocar a caixa da nota abre o teclado para editala. */
@Composable
private fun TarxetaColumna(
    columna: ColumnaOut,
    fila: FilaAlumnoGradebook,
    estado: CadernoUiState,
    ficheiro: FichaInfo?,
    etiquetaAbrir: String,
    onAbrir: () -> Unit,
    onEditar: () -> Unit,
    onEditarRecuperacion: (OpcionRecuperacion) -> Unit,
    onAbrirPdfRecuperacion: (OpcionRecuperacion) -> Unit = {},
    onCriterio: (criterio: String, columnaElixidaId: Int?) -> Unit
) {
    val info = fila.recuperacions[columna.id.toString()]?.takeIf { columna.tipo == "exame" }
    val gardado = estado.ultimoGardado == (columna.id to fila.alumno_id)
    val (corIcona, icona) = when (columna.tipo) {
        "exame" -> ExameAzul to Icons.Filled.Edit
        "traballo" -> TraballoMorado to Icons.Filled.Edit
        else -> ActitudeAmbar to Icons.Filled.Star
    }
    val subtitulo = formatData(columna.data).orEmpty()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = etiquetaAbrir, onClick = onAbrir),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(corIcona.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(icona, contentDescription = null, tint = corIcona, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(columna.nome, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (subtitulo.isNotEmpty()) {
                            Text(subtitulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (ficheiro != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(ficheiro.icona, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(ficheiro.texto, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (gardado) {
                            Text(
                                if (estado.modoFoiraDeLina) "✓ gardado no dispositivo" else "✓ gardado",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                CaixaNota(valorEditable(fila, columna), onEditar)
            }

            if (info != null) {
                CriterioRecuperacion(info.criterio, info.columna_elixida_id, info.valor_orixinal, info.opcions, fila.notas[columna.id.toString()], onEditarRecuperacion, onAbrirPdfRecuperacion, onCriterio)
            }
        }
    }
}

@Composable
private fun CriterioRecuperacion(
    criterio: String,
    elixidaId: Int?,
    valorOrixinal: Double?,
    opcions: List<OpcionRecuperacion>,
    valorEfectivo: Double?,
    onEditarRecuperacion: (OpcionRecuperacion) -> Unit,
    onAbrirPdf: (OpcionRecuperacion) -> Unit,
    onCriterio: (String, Int?) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Criterio de cualificación", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Nota efectiva: ${formatNota(valorEfectivo)} / 10",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FichaCriterio("Orixinal (${formatNota(valorOrixinal)})", criterio == "orixinal" || criterio.isEmpty()) { onCriterio("orixinal", null) }
            opcions.forEach { op ->
                val unica = opcions.size == 1
                val nomeCurto = if (unica) "Recuperación" else op.nome
                FichaCriterio("$nomeCurto (${formatNota(op.valor)})", criterio == "recuperacion" && elixidaId == op.columna_id) {
                    onCriterio("recuperacion", op.columna_id)
                }
                val media = if (valorOrixinal != null && op.valor != null) (valorOrixinal + op.valor) / 2 else op.valor
                FichaCriterio("Media${if (unica) "" else " " + op.nome} (${formatNota(media)})", criterio == "media" && elixidaId == op.columna_id) {
                    onCriterio("media", op.columna_id)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            opcions.forEach { op ->
                Text(
                    "✎ ${op.nome}: ${formatNota(op.valor)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ActitudeAmbar,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ActitudeAmbarContainer)
                        .clickable(onClickLabel = "Editar nota da recuperación") { onEditarRecuperacion(op) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
                if (op.ficheiro_id != null) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClickLabel = "Ver o PDF da recuperación") { onAbrirPdf(op) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FichaCriterio(texto: String, seleccionada: Boolean, onClick: () -> Unit) {
    val fondo by animateColorAsState(
        if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "fondoFicha"
    )
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(fondo).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (seleccionada) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            texto, maxLines = 1, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (seleccionada) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- Actitude (ao final) ----------

/** Gráfico de actitude do trimestre e, nun despregable, a media de cada
 * semana (con cada aspecto e as observacións, se as hai). */
@Composable
private fun SeccionActitude(detalle: DetalleAlumnoCaderno?, trimestreNome: String) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    val etiquetas = remember { xerarSemanasCurso().associate { it.codigo to it.etiqueta } }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.padding(top = 10.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Actitude", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                if (trimestreNome.isNotEmpty()) {
                    Text(trimestreNome, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            when {
                detalle == null || detalle.cargando -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }

                detalle.semanasActitude.isEmpty() -> Text(
                    "Aínda non hai rexistros de actitude neste trimestre.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> {
                    // A media de cada semana calcúlase dos aspectos (base 10), igual en liña e offline.
                    val semanas = detalle.semanasActitude.map { it.copy(media = it.mediaSobre10()) }
                    val mediaTrimestre = semanas.mapNotNull { it.media }.let { if (it.isEmpty()) null else it.average() }

                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Media do trimestre", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${formatNota(mediaTrimestre)} / 10",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = corNota(mediaTrimestre, MaterialTheme.colorScheme)
                        )
                    }

                    GraficoActitude(evolucion = semanas, aspectos = detalle.aspectos)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = if (aberto) "Agochar as medias semanais" else "Ver as medias semanais") { aberto = !aberto }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Media de cada semana (${semanas.size})",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(if (aberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }

                    AnimatedVisibility(visible = aberto) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            semanas.forEach { semana ->
                                FilaSemanaActitude(
                                    etiqueta = etiquetas[semana.semana] ?: "Semana ${semana.semana % 100}",
                                    numero = semana.semana % 100,
                                    media = semana.media,
                                    resumo = detalle.aspectos.mapNotNull { aspecto ->
                                        semana.detalles.firstOrNull { it.aspecto_id == aspecto.id }?.let { "${aspecto.nome.take(4)}. ${it.valor}" }
                                    }.joinToString(" · "),
                                    observacions = semana.observacions
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
private fun FilaSemanaActitude(etiqueta: String, numero: Int, media: Double?, resumo: String, observacions: String?) {
    val cor = corNota(media, MaterialTheme.colorScheme)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) { Text("S$numero", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(etiqueta, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            if (resumo.isNotEmpty()) {
                Text(resumo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!observacions.isNullOrBlank()) {
                Text(observacions, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${formatNota(media)} / 10",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = cor,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(cor.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

// ---------- Visor de fotos ----------

/** Fotos dun traballo a pantalla completa: desliza para pasar de foto e
 * toca dúas veces para ampliar (arrastrando mentres está ampliada). */
@Composable
private fun VisorFotos(ids: List<Int>, onPechar: () -> Unit) {
    Dialog(onDismissRequest = onPechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState { ids.size }
        var ampliada by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, userScrollEnabled = !ampliada, modifier = Modifier.fillMaxSize()) { pagina ->
                FotoAmpliable(url = ApiClient.urlImaxeFicheiro(ids[pagina]), onAmpliada = { ampliada = it })
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPechar) {
                    Icon(Icons.Filled.Close, contentDescription = "Pechar", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${pager.currentPage + 1} / ${ids.size}",
                    style = MaterialTheme.typography.labelLarge, color = Color.White,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun FotoAmpliable(url: String?, onAmpliada: (Boolean) -> Unit) {
    var escala by remember { mutableFloatStateOf(1f) }
    var desprazamento by remember { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = url,
        contentDescription = "Foto do traballo",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    val novaEscala = if (escala > 1f) 1f else 2.5f
                    escala = novaEscala
                    desprazamento = Offset.Zero
                    onAmpliada(novaEscala > 1f)
                })
            }
            .pointerInput(escala > 1f) {
                if (escala > 1f) {
                    detectDragGestures { cambio, arrastre ->
                        cambio.consume()
                        desprazamento += arrastre
                    }
                }
            }
            .graphicsLayer(scaleX = escala, scaleY = escala, translationX = desprazamento.x, translationY = desprazamento.y)
    )
}

// ---------- Teclado numérico ----------

private fun textoDeNota(v: Double?): String =
    v?.toString()?.let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }?.replace('.', ',').orEmpty()

private fun notaDeTexto(t: String): Double? = t.replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeclasNotaSheet(
    clave: Any,
    titulo: String,
    nomeAlumno: String,
    grupo: String,
    inicial: Double?,
    podeSeguinte: Boolean,
    gardando: Boolean,
    erro: String?,
    onPechar: () -> Unit,
    onGardar: (valor: Double?, paraSeguinte: Boolean) -> Unit
) {
    var texto by remember(clave) { mutableStateOf(textoDeNota(inicial)) }
    // A primeira tecla substitúe a nota que xa había, coma nun teclado de calculadora.
    var substituir by remember(clave) { mutableStateOf(true) }

    fun premer(t: String) {
        val base = if (substituir) "" else texto
        substituir = false
        val novo = when {
            t == "," -> when {
                base.contains(',') -> base
                base.isEmpty() -> "0,"
                else -> "$base,"
            }
            base == "0" -> t
            else -> base + t
        }
        val decimais = novo.substringAfter(',', "")
        val valor = notaDeTexto(novo)
        if (decimais.length <= 2 && (valor == null || valor <= 10.0)) texto = novo
    }

    val valorActual = notaDeTexto(texto)
    val cor = corNota(valorActual, MaterialTheme.colorScheme)

    ModalBottomSheet(
        onDismissRequest = onPechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nota de $titulo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (grupo.isNotEmpty()) "$nomeAlumno · $grupo" else nomeAlumno,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPechar) { Icon(Icons.Filled.Close, contentDescription = "Pechar") }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cor.copy(alpha = 0.10f)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(
                        texto.ifEmpty { "—" },
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = cor
                    )
                    Text(" / 10", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    etiquetaNota(valorActual)?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cor,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(cor.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Limpar nota", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { texto = ""; substituir = false }.padding(vertical = 2.dp)
                    )
                }
            }

            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(",", "0", "⌫")).forEach { liña ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    liña.forEach { tecla ->
                        val borrar = tecla == "⌫"
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (borrar) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable {
                                    if (borrar) {
                                        texto = texto.dropLast(1); substituir = false
                                    } else premer(tecla)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tecla, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }

            if (erro != null) {
                Text(erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { onGardar(notaDeTexto(texto), false) },
                enabled = !gardando,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (gardando) "Gardando…" else "Gardar", style = MaterialTheme.typography.titleMedium)
            }
            if (podeSeguinte) {
                FilledTonalButton(
                    onClick = { onGardar(notaDeTexto(texto), true) },
                    enabled = !gardando,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Gardar e seguinte alumno →") }
            }
        }
    }
}
