package com.notazas.mobil.ui.screens.perfilalumno

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.PerfilAlumnoOut
import com.notazas.mobil.datos.rede.ApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

data class PerfilAlumnoUiState(
    val cargando: Boolean = true,
    val erro: String? = null,
    val perfil: PerfilAlumnoOut? = null,
    val aspectosActitude: List<AspectoActitudeOut> = emptyList(),
    val evolucionActitude: List<ActitudeSemanalOut> = emptyList()
)

class PerfilAlumnoViewModel : ViewModel() {

    var estado by mutableStateOf(PerfilAlumnoUiState())
        private set

    fun cargar(alumnoId: Int) {
        estado = estado.copy(cargando = true, erro = null)
        viewModelScope.launch {
            try {
                val servizo = ApiClient.servizo
                val perfilDeferred = async { servizo.verPerfilAlumno(alumnoId) }
                val aspectosDeferred = async { servizo.listarAspectosActitude() }
                val evolucionDeferred = async { servizo.verEvolucionActitude(alumnoId) }

                val perfil = perfilDeferred.await()
                val aspectos = aspectosDeferred.await()
                val evolucion = evolucionDeferred.await()

                estado = estado.copy(
                    cargando = false,
                    perfil = perfil,
                    aspectosActitude = aspectos,
                    evolucionActitude = evolucion
                )
            } catch (e: Exception) {
                estado = estado.copy(cargando = false, erro = "Non se puido cargar a ficha do alumno.")
            }
        }
    }
}
