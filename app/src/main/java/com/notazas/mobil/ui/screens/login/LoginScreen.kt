package com.notazas.mobil.ui.screens.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notazas.mobil.datos.modelos.UsuarioSelector
import com.notazas.mobil.datos.rede.ApiClient

@Composable
fun LoginScreen(
    onSesionIniciada: (token: String, usuario: UsuarioSelector) -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val estado = viewModel.estado
    var escaneandoQr by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(estado.sesionIniciada) {
        estado.sesionIniciada?.let { (token, usuario) ->
            onSesionIniciada(token, usuario)
            viewModel.sesionConsumida()
        }
    }

    if (escaneandoQr) {
        EscanearQrScreen(
            onSesionIniciada = onSesionIniciada,
            onCancelar = { escaneandoQr = false }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Notazas",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "CEIP Plurilingüe",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    if (estado.cargandoUsuarios) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (estado.erro != null && estado.usuarios.isEmpty()) {
                        // Sen conexión ao cargar a lista de usuarios: o problema
                        // normal é que a IP por defecto (10.0.2.2, só válida no
                        // emulador) non serve nun móbil real na wifi do colexio.
                        ConfiguracionServidorInline(
                            erro = estado.erro,
                            onReintentar = viewModel::cambiarServidorERecargar
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = { escaneandoQr = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Escanear QR")
                        }
                    } else {
                        SelectorUsuario(
                            usuarios = estado.usuarios,
                            seleccionado = estado.usuarioSeleccionado,
                            onSeleccionar = viewModel::seleccionarUsuario
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = estado.contrasinal,
                            onValueChange = viewModel::cambiarContrasinal,
                            label = { Text("Contrasinal") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        estado.erro?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = viewModel::entrar,
                            enabled = !estado.entrando,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (estado.entrando) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Entrar")
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        TextButton(
                            onClick = { escaneandoQr = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Escanear QR")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfiguracionServidorInline(
    erro: String?,
    onReintentar: (String) -> Unit
) {
    var url by remember { mutableStateOf(ApiClient.servidorActual()) }

    Column {
        Text(
            text = erro ?: "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "IP do servidor do colexio (a mesma que usas na web, porto 8000):",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Ex. http://192.168.1.50:8000/") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onReintentar(url) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reintentar")
        }
    }
}

@Composable
private fun SelectorUsuario(
    usuarios: List<UsuarioSelector>,
    seleccionado: UsuarioSelector?,
    onSeleccionar: (UsuarioSelector) -> Unit
) {
    var expandido by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = seleccionado?.let { "${it.apelidos}, ${it.nome}" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Usuario") },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandido = true }
        )
        // Superficie transparente para capturar o toque en todo o campo
        // (o OutlinedTextField é readOnly, así que non abre teclado).
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expandido = true }
        )

        DropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (usuarios.isEmpty()) {
                DropdownMenuItem(text = { Text("Non hai usuarios activos") }, onClick = {})
            }
            usuarios.forEach { usuario ->
                DropdownMenuItem(
                    text = { Text("${usuario.apelidos}, ${usuario.nome}") },
                    onClick = {
                        onSeleccionar(usuario)
                        expandido = false
                    }
                )
            }
        }
    }
}
