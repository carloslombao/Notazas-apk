package com.notazas.mobil.ui.componentes

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Cabeza de perfil falando con ondas de son saíndo da boca — a icona
 * de Speaking na Proba Destrezas, calcada ao debuxo que pasou Carlos
 * (trazo groso sen recheo, coma o resto das iconas Material da app).
 */
val IconaSpeaking: ImageVector by lazy {
    ImageVector.Builder(
        name = "Speaking",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 512f,
        viewportHeight = 512f
    ).apply {
        val trazo = SolidColor(Color.Black)
        // Cabeza + pescozo (perfil mirando á dereita).
        path(
            fill = null, stroke = trazo, strokeLineWidth = 32f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            moveTo(176f, 187f)
            curveTo(150f, 90f, 230f, 16f, 344f, 16f)
            curveTo(436f, 16f, 496f, 90f, 496f, 165f)
            curveTo(496f, 230f, 460f, 255f, 445f, 300f)
            curveTo(435f, 330f, 440f, 365f, 460f, 395f)
            curveTo(480f, 425f, 495f, 460f, 495f, 496f)
            lineTo(288f, 496f)
            curveTo(288f, 460f, 290f, 430f, 275f, 400f)
            curveTo(260f, 380f, 235f, 375f, 210f, 372f)
            curveTo(180f, 368f, 155f, 350f, 172f, 315f)
            curveTo(180f, 300f, 178f, 288f, 165f, 282f)
            curveTo(150f, 275f, 148f, 262f, 158f, 250f)
            curveTo(168f, 242f, 168f, 230f, 158f, 222f)
            curveTo(148f, 214f, 165f, 200f, 176f, 187f)
            close()
        }
        // Puntos de "voz interior" (cellas suxeridas polo debuxo).
        path(fill = trazo, stroke = null) {
            moveTo(340f, 55f)
            curveTo(390f, 60f, 430f, 90f, 450f, 130f)
            curveTo(452f, 134f, 448f, 138f, 444f, 136f)
            curveTo(424f, 100f, 388f, 74f, 342f, 70f)
            curveTo(338f, 70f, 336f, 55f, 340f, 55f)
            close()
        }
        path(fill = trazo, stroke = null) {
            moveTo(460f, 155f)
            curveTo(468f, 155f, 474f, 161f, 474f, 169f)
            curveTo(474f, 177f, 468f, 183f, 460f, 183f)
            curveTo(452f, 183f, 446f, 177f, 446f, 169f)
            curveTo(446f, 161f, 452f, 155f, 460f, 155f)
            close()
        }
        // Tres ondas de son saíndo da boca, cara á esquerda.
        for (i in 0..2) {
            val x0 = 152f - i * 48f
            path(
                fill = null, stroke = trazo, strokeLineWidth = 32f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
            ) {
                moveTo(x0, 205f - i * 20f)
                curveTo(x0 - 40f - i * 6f, 240f - i * 8f, x0 - 40f - i * 6f, 272f + i * 8f, x0, 307f + i * 20f)
            }
        }
    }.build()
}

@Composable
fun IconaSpeaking(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(imageVector = IconaSpeaking, contentDescription = "Speaking", tint = tint, modifier = modifier)
}
