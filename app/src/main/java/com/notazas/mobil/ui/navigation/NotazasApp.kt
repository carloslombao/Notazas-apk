package com.notazas.mobil.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.notazas.mobil.datos.local.SesionGardada
import com.notazas.mobil.datos.local.SesionLocal
import com.notazas.mobil.datos.modelos.UsuarioSelector
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.ui.screens.actitude.ActitudeScreen
import com.notazas.mobil.ui.screens.alumnado.AlumnadoScreen
import com.notazas.mobil.ui.screens.axustes.AxustesScreen
import com.notazas.mobil.ui.screens.caderno.CadernoScreen
import com.notazas.mobil.ui.screens.login.DesbloqueoScreen
import com.notazas.mobil.ui.screens.login.LoginScreen
import com.notazas.mobil.ui.screens.perfilalumno.PerfilAlumnoScreen
import com.notazas.mobil.ui.screens.probas.ProbasScreen
import kotlinx.coroutines.launch

/**
 * Composable raíz. Tres estados posibles:
 *  1. Comprobando se hai sesión gardada (breve, ao abrir a app).
 *  2. Hai sesión gardada pero require desbloqueo con biometría.
 *  3. Sen sesión: Login completo (lista de usuarios + contrasinal).
 * Tras iniciar sesión (de calquera das duas formas) móstrase o
 * Scaffold coa barra de navegación inferior.
 */
@Composable
fun NotazasApp() {
    val ambito = rememberCoroutineScope()
    var comprobando by remember { mutableStateOf(true) }
    var sesionGardada by remember { mutableStateOf<SesionGardada?>(null) }
    var sesionActiva by remember { mutableStateOf<Pair<String, UsuarioSelector>?>(null) }

    LaunchedEffect(Unit) {
        val gardada = SesionLocal.cargar()
        if (gardada != null) {
            ApiClient.cambiarServidor(gardada.servidor)
        }
        sesionGardada = gardada
        comprobando = false
    }

    remember {
        ApiClient.onSesionCaducada = {
            ambito.launch {
                ApiClient.definirToken(null)
                SesionLocal.limpar()
                sesionActiva = null
                sesionGardada = null
            }
        }
    }

    when {
        comprobando -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        sesionActiva != null -> {
            PantallaPrincipal(
                usuario = sesionActiva!!.second,
                onPecharSesion = {
                    ApiClient.definirToken(null)
                    sesionActiva = null
                    sesionGardada = null
                    ambito.launch { SesionLocal.limpar() }
                }
            )
        }

        sesionGardada != null -> {
            DesbloqueoScreen(
                sesionGardada = sesionGardada!!,
                onDesbloqueado = {
                    ApiClient.definirToken(sesionGardada!!.token)
                    sesionActiva = sesionGardada!!.token to sesionGardada!!.usuario
                },
                onUsarOutraConta = {
                    sesionGardada = null
                    ambito.launch { SesionLocal.limpar() }
                }
            )
        }

        else -> {
            LoginScreen(onSesionIniciada = { token, usuario ->
                ApiClient.definirToken(token)
                sesionActiva = token to usuario
            })
        }
    }
}

@Composable
private fun PantallaPrincipal(
    usuario: UsuarioSelector,
    onPecharSesion: () -> Unit
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val rutaActual = backStackEntry?.destination?.route

                destinosBarraInferior.forEach { destino ->
                    NavigationBarItem(
                        selected = rutaActual == destino.ruta,
                        onClick = {
                            navController.navigate(destino.ruta) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destino.icona, contentDescription = destino.etiqueta) },
                        label = {
                            Text(
                                text = destino.etiqueta,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destino.Caderno.ruta,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destino.Caderno.ruta) {
                CadernoScreen(usuario = usuario, onAlumnoClick = { id -> navController.navigate("perfil/$id") })
            }
            composable(Destino.Alumnado.ruta) {
                AlumnadoScreen(onAlumnoClick = { id -> navController.navigate("perfil/$id") })
            }
            composable(Destino.Probas.ruta) { ProbasScreen() }
            composable(Destino.Actitude.ruta) { ActitudeScreen() }
            composable(Destino.Axustes.ruta) { AxustesScreen(usuario = usuario, onPecharSesion = onPecharSesion) }
            composable(
                "perfil/{alumnoId}",
                arguments = listOf(navArgument("alumnoId") { type = NavType.IntType })
            ) { backStackEntry ->
                val alumnoId = backStackEntry.arguments?.getInt("alumnoId") ?: return@composable
                PerfilAlumnoScreen(alumnoId = alumnoId, onVoltar = { navController.popBackStack() })
            }
        }
    }
}
