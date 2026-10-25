package com.notazas.mobil.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notazas.mobil.datos.local.SesionLocal
import com.notazas.mobil.datos.modelos.LoginRequest
import com.notazas.mobil.datos.modelos.UsuarioSelector
import com.notazas.mobil.datos.rede.ApiClient
import kotlinx.coroutines.launch

data class LoginUiState(
    val cargandoUsuarios: Boolean = true,
    val usuarios: List<UsuarioSelector> = emptyList(),
    val usuarioSeleccionado: UsuarioSelector? = null,
    val contrasinal: String = "",
    val entrando: Boolean = false,
    val erro: String? = null,
    // token + usuario válidos tras un login correcto
    val sesionIniciada: Pair<String, UsuarioSelector>? = null
)

class LoginViewModel : ViewModel() {

    var estado by mutableStateOf(LoginUiState())
        private set

    init {
        cargarUsuarios()
    }

    fun cargarUsuarios() {
        estado = estado.copy(cargandoUsuarios = true, erro = null)
        viewModelScope.launch {
            try {
                val usuarios = ApiClient.servizo.listarUsuarios()
                estado = estado.copy(cargandoUsuarios = false, usuarios = usuarios)
            } catch (e: Exception) {
                estado = estado.copy(
                    cargandoUsuarios = false,
                    erro = "Non se puido conectar co servidor (${ApiClient.servidorActual()}). " +
                        "Comproba que Notazas está arrincado e que a IP é correcta."
                )
            }
        }
    }

    fun seleccionarUsuario(usuario: UsuarioSelector) {
        estado = estado.copy(usuarioSeleccionado = usuario, erro = null)
    }

    fun cambiarContrasinal(valor: String) {
        estado = estado.copy(contrasinal = valor, erro = null)
    }

    /** Chamar despois de reaccionar a un login correcto, para que este
     * ViewModel (que pode seguir vivo se se reutiliza) non volva
     * disparar o mesmo login unha segunda vez. */
    fun sesionConsumida() {
        estado = estado.copy(sesionIniciada = null, contrasinal = "")
    }

    /** Cambia a IP/porto do servidor (ex. nun móbil real fora do
     * emulador) e volve intentar cargar a lista de usuarios. */
    fun cambiarServidorERecargar(novaUrl: String) {
        ApiClient.cambiarServidor(novaUrl)
        cargarUsuarios()
    }

    fun entrar() {
        val usuario = estado.usuarioSeleccionado ?: run {
            estado = estado.copy(erro = "Escolle un usuario")
            return
        }
        if (estado.contrasinal.isBlank()) {
            estado = estado.copy(erro = "Escribe o contrasinal")
            return
        }
        estado = estado.copy(entrando = true, erro = null)
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.login(
                    LoginRequest(usuario_id = usuario.id, contrasinal = estado.contrasinal)
                )
                SesionLocal.gardar(resposta.access_token, resposta.usuario, ApiClient.servidorActual())
                estado = estado.copy(
                    entrando = false,
                    sesionIniciada = resposta.access_token to resposta.usuario
                )
            } catch (e: Exception) {
                estado = estado.copy(entrando = false, erro = "Usuario ou contrasinal incorrecto")
            }
        }
    }
}
