package com.notazas.mobil.ui.componentes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Fila de teléfono tocable (48 dp mínimo). Ao tocala abre un flotante
 * centrado con dúas opcións: chamada normal ou con número oculto.
 */
@Composable
fun FilaTelefono(nomeTitor: String, telefono: String, modifier: Modifier = Modifier) {
    var aberto by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Chamar a $nomeTitor", role = Role.Button) { aberto = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            telefono,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (aberto) {
        DialogoChamada(
            nomeTitor = nomeTitor,
            telefono = telefono,
            onPechar = { aberto = false }
        )
    }
}

@Composable
private fun DialogoChamada(nomeTitor: String, telefono: String, onPechar: () -> Unit) {
    val contexto = LocalContext.current
    val animar = animacionsActivadas()
    // Empeza oculto e pasa a visible ao compoñerse: dispara a entrada.
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    Dialog(onDismissRequest = onPechar) {
        AnimatedVisibility(
            visibleState = visible,
            enter = if (animar) fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.92f) else EnterTransition.None,
            exit = ExitTransition.None
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        nomeTitor,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        telefono,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(4.dp))

                    OpcionChamada(
                        icona = Icons.Filled.Phone,
                        titulo = "Chamada normal",
                        subtitulo = "Móstrase o teu número",
                        onClick = {
                            abrirMarcador(contexto, telefono, oculto = false)
                            onPechar()
                        }
                    )
                    OpcionChamada(
                        icona = Icons.Filled.VisibilityOff,
                        titulo = "Chamada con número oculto",
                        subtitulo = "Engade #31# ao principio",
                        onClick = {
                            abrirMarcador(contexto, telefono, oculto = true)
                            onPechar()
                        }
                    )

                    TextButton(onClick = onPechar, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}

@Composable
private fun OpcionChamada(
    icona: ImageVector,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icona, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(
                subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Abre o marcador predeterminado co número xa escrito (ACTION_DIAL:
 * non chama soa nin pide permisos; o usuario preme chamar). O `#` vai
 * codificado (%23) para que o URI `tel:` o conserve. */
private fun abrirMarcador(contexto: Context, telefono: String, oculto: Boolean) {
    val limpo = telefono.filter { it.isDigit() || it == '+' }
    val numero = if (oculto) "#31#$limpo" else limpo
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(numero)))
    try {
        contexto.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(contexto, "Non hai ningunha app de chamadas no dispositivo.", Toast.LENGTH_LONG).show()
    }
}

/** Respecta o axuste do sistema "Escala de duración de animacións". */
@Composable
private fun animacionsActivadas(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
