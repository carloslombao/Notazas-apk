package com.notazas.mobil.ui.componentes

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Renderiza as páxinas dun PDF co PdfRenderer de Android (sen dependencias). PdfRenderer só deixa
 * ter UNHA páxina aberta á vez, polo que todo acceso vai baixo o mesmo bloqueo. Os mapas de bits
 * gárdanse nunha caché LRU limitada a 1/8 da memoria da app.
 */
internal class RenderizadorPdf(ficheiro: File) {
    private val bloqueo = Any()
    private val descritor: ParcelFileDescriptor = ParcelFileDescriptor.open(ficheiro, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(descritor)
    } catch (e: Exception) {
        descritor.close()
        throw e
    }
    private var pechado = false

    val numPaxinas: Int = renderer.pageCount

    /** alto / ancho de cada páxina (para reservar o espazo antes de renderizala). */
    val relacions: List<Float> = (0 until numPaxinas).map { i ->
        val pax = renderer.openPage(i)
        try {
            if (pax.width > 0) pax.height.toFloat() / pax.width else 1.414f
        } finally {
            pax.close()
        }
    }

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(8 * 1024 * 1024)) {
        override fun sizeOf(chave: String, valor: Bitmap): Int = valor.byteCount
    }

    /** Renderiza a páxina co ancho indicado (en píxeles). null se o visor xa se pechou. */
    suspend fun renderizar(indice: Int, anchoPx: Int): Bitmap? = withContext(Dispatchers.Default) {
        synchronized(bloqueo) {
            if (pechado) return@synchronized null
            val chave = "$indice@$anchoPx"
            cache.get(chave)?.let { return@synchronized it }

            val alto = (anchoPx * relacions[indice]).toInt().coerceAtLeast(1)
            val mapa = Bitmap.createBitmap(anchoPx, alto, Bitmap.Config.ARGB_8888)
            mapa.eraseColor(android.graphics.Color.WHITE) // PdfRenderer pinta sobre transparente
            val pax = renderer.openPage(indice)
            try {
                pax.render(mapa, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            } finally {
                pax.close()
            }
            cache.put(chave, mapa)
            mapa
        }
    }

    fun pechar() {
        synchronized(bloqueo) {
            if (pechado) return
            pechado = true
            cache.evictAll()
            renderer.close()
            descritor.close()
        }
    }
}

/**
 * Visor de PDF a pantalla completa dentro da app (p. ex. o exame escaneado dun alumno). Desliza
 * para pasar de páxina; zoom con dous dedos, dobre toque ou os botóns +/−; e un botón opcional
 * para abrilo cun visor externo.
 */
@Composable
fun VisorPdf(
    ficheiro: File,
    titulo: String,
    onPechar: () -> Unit,
    onAbrirFora: (() -> Unit)? = null
) {
    var renderizador by remember(ficheiro) { mutableStateOf<RenderizadorPdf?>(null) }
    var erro by remember(ficheiro) { mutableStateOf<String?>(null) }
    var zoomFino by remember { mutableFloatStateOf(1f) }
    val estadoLista = rememberLazyListState()
    // O zoom aplícase en pasos de 0,25 para non volver renderizar as páxinas en cada píxel do pinch.
    val zoom = (zoomFino * 4).roundToInt() / 4f

    LaunchedEffect(ficheiro) {
        try {
            renderizador = withContext(Dispatchers.IO) { RenderizadorPdf(ficheiro) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            erro = "Non se puido ler este PDF."
        }
    }
    DisposableEffect(ficheiro) {
        onDispose { renderizador?.pechar() }
    }

    Dialog(onDismissRequest = onPechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxSize()) {
                val total = renderizador?.numPaxinas ?: 0
                BarraVisorPdf(
                    titulo = titulo,
                    detalle = if (total > 0) "Páxina ${estadoLista.firstVisibleItemIndex + 1} / $total · ${(zoom * 100).roundToInt()} %" else "",
                    onMenos = { zoomFino = (zoomFino - 0.5f).coerceAtLeast(1f) },
                    onMais = { zoomFino = (zoomFino + 0.5f).coerceAtMost(4f) },
                    onAbrirFora = onAbrirFora,
                    onPechar = onPechar
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val rend = renderizador
                    when {
                        erro != null -> Text(
                            erro!!, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                        rend == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        else -> PaxinasPdf(
                            renderizador = rend, zoom = zoom, estadoLista = estadoLista,
                            onZoomFino = { zoomFino = it }, zoomFinoActual = { zoomFino }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarraVisorPdf(
    titulo: String,
    detalle: String,
    onMenos: () -> Unit,
    onMais: () -> Unit,
    onAbrirFora: (() -> Unit)?,
    onPechar: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPechar) { Icon(Icons.Filled.Close, contentDescription = "Pechar") }
        Column(Modifier.weight(1f)) {
            Text(
                titulo, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            if (detalle.isNotEmpty()) {
                Text(detalle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onMenos) { Icon(Icons.Filled.Remove, contentDescription = "Reducir") }
        IconButton(onClick = onMais) { Icon(Icons.Filled.Add, contentDescription = "Ampliar") }
        if (onAbrirFora != null) {
            IconButton(onClick = onAbrirFora) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Abrir noutra app")
            }
        }
    }
}

@Composable
private fun PaxinasPdf(
    renderizador: RenderizadorPdf,
    zoom: Float,
    estadoLista: androidx.compose.foundation.lazy.LazyListState,
    onZoomFino: (Float) -> Unit,
    zoomFinoActual: () -> Float
) {
    val densidade = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Pinch con dous dedos: intercéptase ANTES ca o scroll da lista (PointerEventPass.Initial)
            // só cando hai 2+ dedos, así o desprazamento cun dedo segue funcionando.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val evento = awaitPointerEvent(PointerEventPass.Initial)
                        if (evento.changes.size >= 2) {
                            val cambio = evento.calculateZoom()
                            if (cambio != 1f) {
                                onZoomFino((zoomFinoActual() * cambio).coerceIn(1f, 4f))
                                evento.changes.forEach { it.consume() }
                            }
                        }
                    } while (evento.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onZoomFino(if (zoomFinoActual() > 1.2f) 1f else 2.5f) })
            }
    ) {
        val anchoVista = maxWidth
        val anchoPaxina = anchoVista * zoom
        // Ancho de renderizado en píxeles: limitado e redondeado a centos para reutilizar a caché.
        val anchoPx = with(densidade) { (anchoPaxina - 16.dp).toPx() }.toInt()
            .coerceAtMost(2000).let { (it / 100) * 100 }.coerceAtLeast(300)

        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                state = estadoLista,
                modifier = Modifier.width(anchoPaxina),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(renderizador.numPaxinas) { indice ->
                    PaxinaPdf(renderizador, indice, anchoPx)
                }
            }
        }
    }
}

@Composable
private fun PaxinaPdf(renderizador: RenderizadorPdf, indice: Int, anchoPx: Int) {
    // Mantense o mapa anterior mentres se renderiza o novo (ao cambiar o zoom) para non pestanexar.
    var mapa by remember(indice) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(indice, anchoPx) {
        mapa = renderizador.renderizar(indice, anchoPx) ?: mapa
    }

    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f / renderizador.relacions[indice]),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        val actual = mapa
        if (actual != null) {
            Image(
                bitmap = actual.asImageBitmap(),
                contentDescription = "Páxina ${indice + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
