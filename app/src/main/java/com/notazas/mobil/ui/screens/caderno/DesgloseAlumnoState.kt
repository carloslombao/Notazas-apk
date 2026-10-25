package com.notazas.mobil.ui.screens.caderno

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.MateriaDetalleTrimestreOut
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.datos.util.SemanaCurso
import com.notazas.mobil.datos.util.xerarSemanasCurso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class DesgloseUiState(
    val nomeAlumno: String = "",
    val grupo: String = "",
    val cargandoBase: Boolean = true,
    val erro: String? = null,
    val trimestres: List<Trimestre> = emptyList(),
    val trimestreSeleccionado: Trimestre? = null,
    val cargandoTrimestre: Boolean = false,
    /** Cache por trimestre_id: así trocar de pestana non volve a pedir
     * ao servidor o que xa se consultou nesta apertura do panel. */
    val porTrimestre: Map<Int, List<MateriaDetalleTrimestreOut>> = emptyMap(),
    val aspectos: List<AspectoActitudeOut> = emptyList(),
    /** Todas as semanas de actitude do curso (o mesmo endpoint que usa
     * o gráfico de evolución do perfil); fíltranse por trimestre no
     * propio ecrán. */
    val actitude: List<ActitudeSemanalOut> = emptyList(),
    val semanasCurso: List<SemanaCurso> = emptyList(),
    /** null = "todas as materias". */
    val filtroAsignacionIds: Set<Int>? = null,
    val filtroDespregado: Boolean = false
) {
    val materiasDoTrimestre: List<MateriaDetalleTrimestreOut>
        get() = trimestreSeleccionado?.let { porTrimestre[it.id] }.orEmpty()

    val materiasFiltradas: List<MateriaDetalleTrimestreOut>
        get() = filtroAsignacionIds?.let { ids -> materiasDoTrimestre.filter { it.asignacion_id in ids } } ?: materiasDoTrimestre
}

/** Estado do panel de desglose dun alumno (bottom sheet aberto dende o
 * Caderno). Non é un androidx ViewModel a propósito: vive só mentres o
 * panel está aberto, así que abonda con `remember` + o CoroutineScope
 * da composición que o crea. */
class DesgloseAlumnoState(private val scope: CoroutineScope, private val alumnoId: Int, nomeAlumno: String, grupo: String) {

    var estado by mutableStateOf(DesgloseUiState(nomeAlumno = nomeAlumno, grupo = grupo))
        private set

    init {
        estado = estado.copy(semanasCurso = xerarSemanasCurso())
        cargarBase()
    }

    private fun cargarBase() {
        scope.launch {
            try {
                val trimestres = ApiClient.servizo.listarTrimestres().filter { it.orde <= 3 }.sortedBy { it.orde }
                val aspectos = ApiClient.servizo.listarAspectosActitude().sortedBy { it.orde }
                val actitude = ApiClient.servizo.verEvolucionActitude(alumnoId)
                estado = estado.copy(
                    cargandoBase = false,
                    trimestres = trimestres,
                    trimestreSeleccionado = trimestres.firstOrNull { it.activo } ?: trimestres.firstOrNull(),
                    aspectos = aspectos,
                    actitude = actitude
                )
                cargarTrimestreActual()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Desglose", "Erro cargando o desglose de $alumnoId", e)
                estado = estado.copy(cargandoBase = false, erro = "Non se puido cargar o desglose. Comproba a conexión.")
            }
        }
    }

    fun seleccionarTrimestre(trimestre: Trimestre) {
        estado = estado.copy(trimestreSeleccionado = trimestre)
        cargarTrimestreActual()
    }

    private fun cargarTrimestreActual() {
        val trimestre = estado.trimestreSeleccionado ?: return
        if (estado.porTrimestre.containsKey(trimestre.id)) return
        estado = estado.copy(cargandoTrimestre = true, erro = null)
        scope.launch {
            try {
                val materias = ApiClient.servizo.verNotasDetalleTrimestre(alumnoId, trimestre.id)
                if (estado.trimestreSeleccionado?.id != trimestre.id) return@launch
                estado = estado.copy(
                    cargandoTrimestre = false,
                    porTrimestre = estado.porTrimestre + (trimestre.id to materias)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Desglose", "Erro cargando o trimestre ${trimestre.id}", e)
                estado = estado.copy(cargandoTrimestre = false, erro = "Non se puideron cargar as notas deste trimestre.")
            }
        }
    }

    fun alternarFiltroDespregado() {
        estado = estado.copy(filtroDespregado = !estado.filtroDespregado)
    }

    /** null (=todas) só se todas están marcadas; se se desmarca unha
     * soa, pasa a gardar explicitamente as que quedan marcadas. */
    fun alternarMateria(asignacionId: Int) {
        val todasAsIds = estado.materiasDoTrimestre.map { it.asignacion_id }.toSet()
        val actuais = estado.filtroAsignacionIds ?: todasAsIds
        val novas = if (asignacionId in actuais) actuais - asignacionId else actuais + asignacionId
        estado = estado.copy(filtroAsignacionIds = if (novas == todasAsIds) null else novas)
    }
}
