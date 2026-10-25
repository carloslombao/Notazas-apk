package com.notazas.mobil.ui.screens.alumnado

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.offline.CacheAsignacionEntrada
import com.notazas.mobil.datos.offline.CacheOffline
import com.notazas.mobil.datos.offline.comoAsignacion
import com.notazas.mobil.datos.offline.etiquetaDatosOffline
import com.notazas.mobil.datos.offline.idAlumnoOffline
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.datos.util.ordenadosPorNome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// TODO(fase 1): non hai un endpoint de "listar alumnado dun grupo"
// independente de materia no backend aínda — de momento reutilizamos
// o roster que devolve o gradebook dunha asignación calquera dese
// grupo (os alumnos son os mesmos, cambie a materia que cambie).
data class AlumnadoUiState(
    val cargando: Boolean = true,
    val erro: String? = null,
    /** Sen servidor accesible: lista lida da caché local (snapshot de BoxAbalar). */
    val modoFoiraDeLina: Boolean = false,
    /** Cando son os datos da caché offline ("18/10 11:27"); só en modo offline. */
    val datosDe: String? = null,
    val asignacions: List<Asignacion> = emptyList(),
    val asignacionSeleccionada: Asignacion? = null,
    val alumnos: List<FilaAlumnoGradebook> = emptyList(),
    val busca: String = ""
)

class AlumnadoViewModel(application: Application) : AndroidViewModel(application) {

    var estado by mutableStateOf(AlumnadoUiState())
        private set

    private var trimestrePorDefecto: Trimestre? = null
    private var entradasOffline: List<CacheAsignacionEntrada> = emptyList()

    init {
        cargarInicial()
    }

    private fun cargarInicial() {
        estado = estado.copy(cargando = true, erro = null)
        viewModelScope.launch {
            try {
                val asignacions = ApiClient.servizo.listarAsignacions()
                val trimestres = ApiClient.servizo.listarTrimestres()
                trimestrePorDefecto = trimestres.firstOrNull { it.activo } ?: trimestres.minByOrNull { it.orde }

                val asignacionPorDefecto = asignacions.firstOrNull { it.esMina } ?: asignacions.firstOrNull()
                estado = estado.copy(
                    modoFoiraDeLina = false, asignacions = asignacions, asignacionSeleccionada = asignacionPorDefecto
                )

                if (asignacionPorDefecto != null) {
                    cargarAlumnado(asignacionPorDefecto)
                } else {
                    estado = estado.copy(cargando = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AlumnadoViewModel", "Sen servidor accesible, probando modo fóra do colexio", e)
                cargarInicialOffline()
            }
        }
    }

    /** Sen servidor: o alumnado sae das copias gardadas no móbil (as mesmas do Caderno). */
    private suspend fun cargarInicialOffline() {
        val entradas = CacheOffline.listarAsignacionsCacheadas(getApplication())
        if (entradas.isEmpty()) {
            estado = estado.copy(
                cargando = false,
                erro = "Sen conexión e sen datos gardados no móbil. Abre a app unha vez con conexión ao servidor (cole ou casa) e co teu BoxAbalar persoal configurado na web para que se garden."
            )
            return
        }
        entradasOffline = entradas
        val asignacions = entradas.map { it.comoAsignacion() }.ordenadosPorNome { "${it.grupo} ${it.materia}" }
        val asignacion = asignacions.first()
        estado = estado.copy(modoFoiraDeLina = true, asignacions = asignacions, asignacionSeleccionada = asignacion, erro = null)
        cargarAlumnadoOffline(asignacion)
    }

    private fun cargarAlumnadoOffline(asignacion: Asignacion) {
        val entrada = entradasOffline.firstOrNull { it.asignacion_id == asignacion.id }
        if (entrada == null) {
            estado = estado.copy(cargando = false, erro = "Non se atopan datos gardados para esta materia.")
            return
        }
        val alumnos = entrada.snapshot.alumnado.map {
            FilaAlumnoGradebook(
                alumno_id = idAlumnoOffline(it.nome, it.apelidos),
                nome_completo = "${it.apelidos}, ${it.nome}"
            )
        }.ordenadosPorNome { it.nome_completo }
        estado = estado.copy(
            cargando = false, erro = null, alumnos = alumnos,
            datosDe = etiquetaDatosOffline(entrada.snapshot.modificado_en)
        )
    }

    fun seleccionarAsignacion(asignacion: Asignacion) {
        estado = estado.copy(asignacionSeleccionada = asignacion)
        if (estado.modoFoiraDeLina) cargarAlumnadoOffline(asignacion) else cargarAlumnado(asignacion)
    }

    fun cambiarBusca(valor: String) {
        estado = estado.copy(busca = valor)
    }

    private fun cargarAlumnado(asignacion: Asignacion) {
        val trimestre = trimestrePorDefecto ?: return
        estado = estado.copy(cargando = true, erro = null)
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.verGradebookTrimestre(asignacion.id, trimestre.id)
                val ordenados = resposta.filas.ordenadosPorNome { it.nome_completo }
                estado = estado.copy(cargando = false, alumnos = ordenados)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                estado = estado.copy(cargando = false, erro = "Non se puido cargar o alumnado deste grupo.")
            }
        }
    }
}
