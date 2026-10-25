package com.notazas.mobil.ui.screens.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.notazas.mobil.datos.local.SesionGardada
import com.notazas.mobil.ui.seguridade.mostrarPromptBiometrico

/**
 * Pantalla amosada cando xa hai un usuario gardado no móbil: só pide
 * confirmar identidade con biometría, sen volver escribir contrasinal.
 */
@Composable
fun DesbloqueoScreen(
    sesionGardada: SesionGardada,
    onDesbloqueado: () -> Unit,
    onUsarOutraConta: () -> Unit
) {
    val activity = LocalContext.current as? FragmentActivity
    var erro by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Notazas",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${sesionGardada.usuario.apelidos}, ${sesionGardada.usuario.nome}",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    val act = activity ?: return@Button
                    mostrarPromptBiometrico(
                        act,
                        onExito = { erro = null; onDesbloqueado() },
                        onErro = { erro = it }
                    )
                },
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Desbloquear")
            }

            erro?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onUsarOutraConta) {
                Text("Usar outra conta")
            }
        }
    }
}
