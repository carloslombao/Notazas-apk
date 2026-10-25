package com.notazas.mobil.ui.screens.probas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notazas.mobil.ui.screens.destrezas.DestrezasScreen

private enum class DestinoProbas { ENTRADA, TRABALLOS, DESTREZAS, EXAMES }

@Composable
fun ProbasScreen() {
    var destino by rememberSaveable { mutableStateOf(DestinoProbas.ENTRADA) }

    when (destino) {
        DestinoProbas.TRABALLOS -> SubirTraballosScreen(onSair = { destino = DestinoProbas.ENTRADA })
        DestinoProbas.DESTREZAS -> DestrezasScreen(onSair = { destino = DestinoProbas.ENTRADA })
        DestinoProbas.EXAMES -> SubirExameScreen(onSair = { destino = DestinoProbas.ENTRADA })
        DestinoProbas.ENTRADA -> EntradaProbas(
            onTraballos = { destino = DestinoProbas.TRABALLOS },
            onDestrezas = { destino = DestinoProbas.DESTREZAS },
            onExames = { destino = DestinoProbas.EXAMES }
        )
    }
}

@Composable
private fun EntradaProbas(onTraballos: () -> Unit, onDestrezas: () -> Unit, onExames: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Probas",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        AccionProba(
            icona = Icons.Filled.PhotoLibrary,
            titulo = "Subir fotos de traballos",
            subtitulo = "Fotos da galería co QR do alumno visible",
            activa = true,
            onClick = onTraballos
        )
        AccionProba(
            icona = Icons.Filled.Headphones,
            titulo = "Proba Destrezas",
            subtitulo = "Listening, Reading e Speaking por palabras",
            activa = true,
            onClick = onDestrezas
        )
        AccionProba(
            icona = Icons.Filled.Description,
            titulo = "Subir exame",
            subtitulo = "PDF escaneado, con lectura de QR e nota",
            activa = true,
            onClick = onExames
        )
    }
}

@Composable
private fun AccionProba(
    icona: ImageVector,
    titulo: String,
    subtitulo: String,
    activa: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = activa,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceContainerLowest,
            disabledContainerColor = scheme.surfaceContainer
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(if (activa) scheme.primaryContainer else scheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icona,
                    contentDescription = null,
                    tint = if (activa) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    titulo,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (activa) scheme.onSurface else scheme.onSurfaceVariant
                )
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}
