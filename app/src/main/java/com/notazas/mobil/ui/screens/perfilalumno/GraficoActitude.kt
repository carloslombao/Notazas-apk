package com.notazas.mobil.ui.screens.perfilalumno

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut

private val PALETA_ASPECTOS = listOf(
    Color(0xFF2170E4), // azul
    Color(0xFFE5484D), // vermello
    Color(0xFF10B981), // verde
    Color(0xFF842BD2), // morado
    Color(0xFFF6A609)  // ámbar (por se hai máis de 4 aspectos)
)
private val COR_PROMEDIO = Color(0xFF006C49)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GraficoActitude(
    evolucion: List<ActitudeSemanalOut>,
    aspectos: List<AspectoActitudeOut>
) {
    if (evolucion.isEmpty()) {
        Text(
            "Aínda non hai rexistros de actitude para este alumno.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val aspectosOrdenados = aspectos.sortedBy { it.orde }
    val corPorAspecto = aspectosOrdenados.mapIndexed { i, a -> a.id to PALETA_ASPECTOS[i % PALETA_ASPECTOS.size] }.toMap()
    val semanas = evolucion.sortedBy { it.semana }
    val maxValor = 5f

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val ancho = size.width
            val alto = size.height
            val nGrupos = semanas.size
            val anchoGrupo = ancho / nGrupos

            // liñas guía horizontais (1 a 5)
            for (nivel in 1..5) {
                val y = alto - (nivel / maxValor) * alto
                drawLine(Color(0x33000000), Offset(0f, y), Offset(ancho, y), strokeWidth = 1.5f)
            }

            val puntosMedia = mutableListOf<Offset>()

            semanas.forEachIndexed { i, semana ->
                val detalles = semana.detalles.sortedBy { d ->
                    aspectosOrdenados.indexOfFirst { it.id == d.aspecto_id }
                }
                val nBarras = detalles.size.coerceAtLeast(1)
                val anchoUtil = anchoGrupo * 0.75f
                val anchoBarra = anchoUtil / nBarras
                val inicioGrupo = i * anchoGrupo + (anchoGrupo - anchoUtil) / 2f

                detalles.forEachIndexed { j, d ->
                    val alturaBarra = (d.valor / maxValor) * alto
                    val x = inicioGrupo + j * anchoBarra
                    drawRect(
                        color = corPorAspecto[d.aspecto_id] ?: Color.Gray,
                        topLeft = Offset(x, alto - alturaBarra),
                        size = Size(anchoBarra * 0.75f, alturaBarra)
                    )
                }

                semana.media?.let {
                    // `media` vén en base 10; as barras e as liñas guía van de 1 a 5.
                    val y = alto - (it.toFloat() / 2f / maxValor) * alto
                    puntosMedia.add(Offset(i * anchoGrupo + anchoGrupo / 2f, y))
                }
            }

            for (k in 0 until puntosMedia.size - 1) {
                drawLine(COR_PROMEDIO, puntosMedia[k], puntosMedia[k + 1], strokeWidth = 5f)
            }
            puntosMedia.forEach { p ->
                drawCircle(Color.White, radius = 7f, center = p)
                drawCircle(COR_PROMEDIO, radius = 7f, center = p, style = Stroke(width = 3f))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // amosa só algunhas etiquetas se hai moitas semanas, para non amoreallas
            val paso = if (semanas.size > 8) 2 else 1
            semanas.forEachIndexed { i, semana ->
                if (i % paso == 0) {
                    // O código é anoInicio*100 + nº de semana (ex. 202603): amosamos só o número.
                    Text("S${semana.semana % 100}", style = MaterialTheme.typography.labelSmall)
                } else {
                    Spacer(Modifier)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            aspectosOrdenados.forEachIndexed { i, aspecto ->
                LendaItem(cor = PALETA_ASPECTOS[i % PALETA_ASPECTOS.size], etiqueta = aspecto.nome)
            }
            LendaItem(cor = COR_PROMEDIO, etiqueta = "Promedio", liña = true)
        }
    }
}

@Composable
private fun LendaItem(cor: Color, etiqueta: String, liña: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(if (liña) 14.dp else 10.dp, if (liña) 3.dp else 10.dp)
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(if (liña) 3.dp else 10.dp)) {
                if (liña) {
                    drawLine(cor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height)
                } else {
                    drawCircle(cor, radius = size.minDimension / 2)
                }
            }
        }
        Spacer(Modifier.size(4.dp, 0.dp))
        Text(etiqueta, style = MaterialTheme.typography.labelSmall)
    }
}
