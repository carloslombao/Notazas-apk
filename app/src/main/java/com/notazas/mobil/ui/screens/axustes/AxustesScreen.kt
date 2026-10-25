package com.notazas.mobil.ui.screens.axustes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notazas.mobil.datos.local.SesionLocal
import com.notazas.mobil.datos.cumpleanos.AlmacenCumpleanos
import com.notazas.mobil.datos.modelos.UsuarioSelector
import com.notazas.mobil.datos.offline.CacheOffline
import com.notazas.mobil.datos.offline.sincronizarPendentes
import com.notazas.mobil.datos.offline.sincronizarTodo
import com.notazas.mobil.datos.rede.ApiClient
import kotlinx.coroutines.launch

private enum class EstadoConexion { COMPROBANDO, CONECTADO, SEN_CONEXION }

@Composable
fun AxustesScreen(usuario: UsuarioSelector, onPecharSesion: () -> Unit) {
    var ipServidor by remember { mutableStateOf(ApiClient.servidorActual()) }
    var estadoConexion by remember { mutableStateOf(EstadoConexion.COMPROBANDO) }
    var pendentes by remember { mutableStateOf(0) }
    var sincronizando by remember { mutableStateOf(false) }
    var mensaxeSincronizacion by remember { mutableStateOf<String?>(null) }
    var pechandoSesion by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()
    val contexto = LocalContext.current

    suspend fun actualizarPendentes() {
        pendentes = CacheOffline.listarPendentesIds(contexto).size
    }

    LaunchedEffect(Unit) { actualizarPendentes() }

    suspend fun comprobarConexion() {
        estadoConexion = EstadoConexion.COMPROBANDO
        estadoConexion = try {
            ApiClient.servizo.listarTrimestres()
            EstadoConexion.CONECTADO
        } catch (e: Exception) {
            EstadoConexion.SEN_CONEXION
        }
    }

    LaunchedEffect(Unit) { comprobarConexion() }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("${usuario.apelidos}, ${usuario.nome}", style = MaterialTheme.typography.headlineMedium)
        Text(usuario.rol, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(24.dp))

        // Estado de conexión
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cor = when (estadoConexion) {
                        EstadoConexion.CONECTADO -> MaterialTheme.colorScheme.primary
                        EstadoConexion.SEN_CONEXION -> MaterialTheme.colorScheme.error
                        EstadoConexion.COMPROBANDO -> MaterialTheme.colorScheme.outline
                    }
                    Box(Modifier.size(10.dp).background(cor, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (estadoConexion) {
                            EstadoConexion.CONECTADO -> "Conectado ao servidor"
                            EstadoConexion.SEN_CONEXION -> "Sen conexión"
                            EstadoConexion.COMPROBANDO -> "Comprobando..."
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(ApiClient.servidorActual(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { ambito.launch { comprobarConexion() } }) {
                    if (estadoConexion == EstadoConexion.COMPROBANDO) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Detectar de novo")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Sincronización con BoxAbalar: as notas gardadas sen conexión
        // quédanse no móbil ata que se suben aquí (ou ao pechar sesión, ou
        // soas ao abrir a app se quedara algo pendente dunha vez anterior).
        // Non se sobe cada vez que se garda algo porque WebDAV non aguanta
        // ben moitas escrituras seguidas. O alumnado/probas para traballar
        // sen conexión (modo "fóra do colexio") prepárase soa, sen botón: ao
        // escanear o QR e cada vez que se abre a app con conexión.
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Sincronización con BoxAbalar", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (pendentes == 0) {
                        "Sen cambios pendentes. Sincronizar fusiona nota a nota co que haxa en BoxAbalar (nunca o sobrescribe) e funciona tamén coa wifi do colexio. Notas, Actitude e Destrezas gardan primeiro no móbil."
                    } else if (pendentes == 1) {
                        "1 materia ten cambios sen subir aínda."
                    } else {
                        "$pendentes materias teñen cambios sen subir aínda."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        sincronizando = true
                        mensaxeSincronizacion = null
                        ambito.launch {
                            val resultado = sincronizarTodo(contexto)
                            mensaxeSincronizacion = resultado.mensaxe
                            actualizarPendentes()
                            sincronizando = false
                        }
                    },
                    enabled = !sincronizando
                ) {
                    if (sincronizando) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (pendentes > 0) "Sincronizar ($pendentes con cambios)" else "Sincronizar agora")
                    }
                }
                mensaxeSincronizacion?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SeccionCumpleanos()

        Spacer(Modifier.height(16.dp))

        SeccionActualizacion()

        Spacer(Modifier.height(24.dp))

        Text("Servidor (só para probas nun móbil real)", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ipServidor,
            onValueChange = { ipServidor = it },
            label = { Text("Ex. http://192.168.1.50:8000/") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            ApiClient.cambiarServidor(ipServidor)
            ambito.launch {
                SesionLocal.actualizarServidor(ApiClient.servidorActual())
                comprobarConexion()
            }
        }) {
            Text("Gardar")
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                pechandoSesion = true
                ambito.launch {
                    // Mellor esforzo: se falla, as notas quedan pendentes e
                    // subiránse soas a próxima vez que se abra a app con
                    // conexión. Nunca bloquea o peche de sesión.
                    try {
                        sincronizarPendentes(contexto)
                    } catch (e: Exception) {
                        // ignorado a propósito
                    }
                    // Datos de menores (datas de nacemento): non quedan no móvil sen sesión.
                    AlmacenCumpleanos.borrar(contexto)
                    onPecharSesion()
                }
            },
            enabled = !pechandoSesion,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            if (pechandoSesion) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onErrorContainer)
            } else {
                Text("Pechar sesión", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
