package com.notazas.mobil.ui.screens.login

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notazas.mobil.datos.local.CredenciaisRemotas
import com.notazas.mobil.datos.local.CredenciaisRemotasGardadas
import com.notazas.mobil.datos.local.NextcloudPersoalGardado
import com.notazas.mobil.datos.local.SesionLocal
import com.notazas.mobil.datos.modelos.CanxearQrIn
import com.notazas.mobil.datos.modelos.UsuarioSelector
import com.notazas.mobil.datos.offline.prepararParaForaDoColexio
import com.notazas.mobil.datos.rede.ApiClient
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val TAG = "EscanearQr"

data class EscanearQrUiState(
    /** false = cámara activa agardando un QR; evita procesar o mesmo
     * fotograma varias veces mentres a chamada de rede está en curso. */
    val procesando: Boolean = false,
    val erro: String? = null,
    val sesionIniciada: Pair<String, UsuarioSelector>? = null
)

class EscanearQrViewModel(application: Application) : AndroidViewModel(application) {

    var estado by mutableStateOf(EscanearQrUiState())
        private set

    /** O texto plano decodificado do QR: "ip:porto:codigo" (ver
     * aprovisionamento_qr_movil.md). Non garda nin loguea o código en
     * ningún punto deste fluxo — é un segredo dun só uso. */
    fun procesarTextoQr(texto: String) {
        if (estado.procesando) return

        val partes = texto.trim().split(":", limit = 3)
        if (partes.size != 3 || partes[1].toIntOrNull() == null) {
            estado = estado.copy(erro = "Ese código non parece un QR de Notazas. Proba a escanear de novo.")
            return
        }
        val (ip, portoTexto, codigo) = partes
        val porto = portoTexto.toInt()
        val destino = "$ip:$porto"

        estado = estado.copy(procesando = true, erro = null)
        ApiClient.cambiarServidor("http://$ip:$porto/")

        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.canxearQrAprovisionamento(CanxearQrIn(codigo))

                // A resposta do canxeo non trae o id numérico nin a cor do
                // avatar (necesarios para UsuarioSelector); completámolos
                // coa lista pública de usuarios do mesmo servidor.
                val usuarios = ApiClient.servizo.listarUsuarios()
                val coincidencias = usuarios.filter {
                    it.nome.equals(resposta.nome, ignoreCase = true) && it.apelidos.equals(resposta.apelidos, ignoreCase = true)
                }
                val usuarioCompleto = when {
                    coincidencias.size == 1 -> coincidencias.first()
                    else -> {
                        // 0 ou >1 coincidencias: raro, pero non bloqueamos a
                        // sesión por iso — entra igual, sen cor de avatar nin
                        // filtrado "é miña" perfecto ata que volva iniciar sesión
                        // normal unha vez.
                        Log.w(TAG, "listarUsuarios() non deu unha coincidencia única (${coincidencias.size}) para o usuario aprovisionado")
                        UsuarioSelector(id = -1, nome = resposta.nome, apelidos = resposta.apelidos, rol = resposta.rol, avatar_cor = "#006C49")
                    }
                }

                SesionLocal.gardar(resposta.token_acceso_remoto, usuarioCompleto, ApiClient.servidorActual())
                CredenciaisRemotas.gardar(
                    CredenciaisRemotasGardadas(
                        urlServidorCentro = resposta.url_servidor,
                        usuarioCentro = resposta.usuario_nextcloud,
                        tokenCentro = resposta.token,
                        notazasFieldKey = resposta.notazas_field_key,
                        nextcloudPersoal = resposta.nextcloud_persoal?.let {
                            NextcloudPersoalGardado(it.url_servidor, it.usuario_nextcloud, it.token)
                        }
                    )
                )

                // Deixa preparado o modo "fóra do colexio" xa dende aquí,
                // sen que o profesor teña que facer nada máis. Mellor
                // esforzo: se falla (p.ex. aínda sen BoxAbalar persoal
                // configurado), non bloquea o login — queda sen preparar e
                // xa se tentará de novo soa ao abrir a app (MainActivity).
                try {
                    prepararParaForaDoColexio(getApplication())
                } catch (e: Exception) {
                    Log.w(TAG, "Non se puido preparar o modo offline tras o QR", e)
                }

                estado = estado.copy(procesando = false, sesionIniciada = resposta.token_acceso_remoto to usuarioCompleto)
            } catch (e: HttpException) {
                Log.w(TAG, "Canxeo de QR rexeitado polo servidor ($destino): código ${e.code()}")
                estado = estado.copy(
                    procesando = false,
                    erro = "Este QR xa caducou ou xa se usou. Xera outro dende o teu perfil na web."
                )
            } catch (e: UnknownHostException) {
                Log.w(TAG, "Host non atopado: $destino", e)
                estado = estado.copy(procesando = false, erro = "Non se atopa $destino. Comproba que esa IP é correcta e que estás na wifi do colexio.")
            } catch (e: ConnectException) {
                Log.w(TAG, "Conexión rexeitada: $destino", e)
                estado = estado.copy(procesando = false, erro = "$destino non responde. Comproba que o servidor está acendido e que estás na mesma wifi.")
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Tempo esgotado: $destino", e)
                estado = estado.copy(procesando = false, erro = "Tardou demasiado en responder $destino. Volve intentalo.")
            } catch (e: IOException) {
                Log.e(TAG, "Erro de rede no canxeo do QR ($destino)", e)
                estado = estado.copy(
                    procesando = false,
                    erro = "Non se puido conectar a $destino. Comproba que estás conectado á wifi do colexio e volve escanear."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro inesperado no canxeo do QR ($destino)", e)
                estado = estado.copy(
                    procesando = false,
                    erro = "Algo fallou ao procesar a resposta do servidor ($destino). Mira o Logcat (etiqueta EscanearQr) para máis detalle."
                )
            }
        }
    }

    fun limparErro() {
        estado = estado.copy(erro = null)
    }

    fun sesionConsumida() {
        estado = estado.copy(sesionIniciada = null)
    }
}
