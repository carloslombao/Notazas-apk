package com.notazas.mobil.ui.screens.probas

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.AsignacionManualIn
import com.notazas.mobil.datos.modelos.ColumnaCreate
import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.datos.util.ordenadosPorNome
import com.notazas.mobil.datos.util.reducirImaxe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.util.UUID

enum class PasoTraballos { TRABALLO, FOTOS, SUBINDO, RESULTADO }

/** Unha foto xa procesada polo servidor. Sen `alumnoId` = pendente de
 * asignar a man (o QR non se leu). */
data class ResultadoFoto(
    val clave: String,
    val qrAtopado: Boolean,
    val alumnoId: Int?,
    val nomeAlumno: String?,
    val ficheiroId: Int?,
    val pendenteId: String?
)

data class SubirTraballosUiState(
    val cargando: Boolean = true,
    val erro: String? = null,
    val asignacions: List<Asignacion> = emptyList(),
    val asignacionSeleccionada: Asignacion? = null,
    val trimestres: List<Trimestre> = emptyList(),
    val trimestreSeleccionado: Trimestre? = null,
    val cargandoColumnas: Boolean = false,
    val columnas: List<ColumnaOut> = emptyList(),
    val columnaSeleccionadaId: Int? = null,
    val alumnos: List<FilaAlumnoGradebook> = emptyList(),
    val paso: PasoTraballos = PasoTraballos.TRABALLO,
    val fotos: List<Uri> = emptyList(),
    val subidas: Int = 0,
    val totalSubida: Int = 0,
    val erroSubida: String? = null,
    val resultados: List<ResultadoFoto> = emptyList(),
    val mostrarNovo: Boolean = false,
    val gardandoNovo: Boolean = false,
    val erroNovo: String? = null,
    val fotoAAsignar: ResultadoFoto? = null,
    val asignando: Boolean = false,
    val erroAsignar: String? = null
) {
    val columnaSeleccionada: ColumnaOut?
        get() = columnas.firstOrNull { it.id == columnaSeleccionadaId }
}

class SubirTraballosViewModel : ViewModel() {

    var estado by mutableStateOf(SubirTraballosUiState())
        private set

    init {
        cargarInicial()
    }

    // ---------- Paso 1: materia, trimestre e traballo ----------

    private fun cargarInicial() {
        viewModelScope.launch {
            try {
                // Como na web, só se pode subir fotos ás materias propias.
                val propias = ApiClient.servizo.listarAsignacions().filter { it.esMina }
                val trimestres = ApiClient.servizo.listarTrimestres()
                    .filter { it.orde <= 3 }
                    .sortedBy { it.orde }
                estado = estado.copy(
                    cargando = false,
                    asignacions = propias,
                    asignacionSeleccionada = propias.firstOrNull(),
                    trimestres = trimestres,
                    trimestreSeleccionado = trimestres.firstOrNull { it.activo } ?: trimestres.firstOrNull()
                )
                recargarColumnas()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirTraballos", "Erro na carga inicial", e)
                estado = estado.copy(cargando = false, erro = "Non se puido cargar a información. Comproba a conexión.")
            }
        }
    }

    fun seleccionarAsignacion(asignacion: Asignacion) {
        estado = estado.copy(asignacionSeleccionada = asignacion, columnaSeleccionadaId = null)
        recargarColumnas()
    }

    fun seleccionarTrimestre(trimestre: Trimestre) {
        estado = estado.copy(trimestreSeleccionado = trimestre, columnaSeleccionadaId = null)
        recargarColumnas()
    }

    fun seleccionarColumna(id: Int) {
        estado = estado.copy(columnaSeleccionadaId = id)
    }

    /** Carga as columnas de tipo traballo do trimestre (e o alumnado da
     * materia, que se usa despois para asignar a man as fotos sen QR). */
    private fun recargarColumnas(columnaASeleccionar: Int? = null) {
        val asignacion = estado.asignacionSeleccionada
        val trimestre = estado.trimestreSeleccionado
        if (asignacion == null || trimestre == null) {
            estado = estado.copy(columnas = emptyList(), alumnos = emptyList(), columnaSeleccionadaId = null)
            return
        }
        estado = estado.copy(cargandoColumnas = true, erro = null)
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.verGradebookTrimestre(asignacion.id, trimestre.id)
                // Se mentres tanto cambiou a selección, esta resposta xa non vale.
                if (estado.asignacionSeleccionada?.id != asignacion.id ||
                    estado.trimestreSeleccionado?.id != trimestre.id
                ) return@launch

                val traballos = resposta.columnas.filter { it.tipo == "traballo" }
                // Non se elixe un traballo á toa: só se mantén a selección
                // anterior, a que acaba de crearse, ou o único que hai.
                val elixida = columnaASeleccionar
                    ?: estado.columnaSeleccionadaId?.takeIf { id -> traballos.any { it.id == id } }
                    ?: traballos.singleOrNull()?.id
                estado = estado.copy(
                    cargandoColumnas = false,
                    columnas = traballos,
                    columnaSeleccionadaId = elixida,
                    alumnos = resposta.filas.ordenadosPorNome { it.nome_completo }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirTraballos", "Erro cargando traballos", e)
                estado = estado.copy(cargandoColumnas = false, erro = "Non se puideron cargar os traballos deste trimestre.")
            }
        }
    }

    // ---------- Novo traballo ----------

    fun abrirNovo() {
        estado = estado.copy(mostrarNovo = true, erroNovo = null)
    }

    fun pecharNovo() {
        estado = estado.copy(mostrarNovo = false, erroNovo = null)
    }

    /** `data` en formato AAAA-MM-DD ou null (opcional, como na web). */
    fun crearTraballo(nome: String, data: String?) {
        val asignacion = estado.asignacionSeleccionada ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        if (nome.isBlank()) {
            estado = estado.copy(erroNovo = "Escribe un nome para o traballo.")
            return
        }
        if (estado.gardandoNovo) return
        estado = estado.copy(gardandoNovo = true, erroNovo = null)
        viewModelScope.launch {
            try {
                val nova = ApiClient.servizo.crearColumna(
                    asignacion.id,
                    ColumnaCreate(trimestre_id = trimestre.id, tipo = "traballo", nome = nome.trim(), data = data)
                )
                estado = estado.copy(mostrarNovo = false, gardandoNovo = false)
                recargarColumnas(columnaASeleccionar = nova.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirTraballos", "Erro creando traballo", e)
                val mensaxe = if (e is HttpException && e.code() == 403) {
                    "Non tes permiso para crear traballos nesta materia."
                } else {
                    "Non se puido crear o traballo. Comproba a conexión e proba de novo."
                }
                estado = estado.copy(gardandoNovo = false, erroNovo = mensaxe)
            }
        }
    }

    // ---------- Paso 2: fotos ----------

    fun irAFotos() {
        if (estado.columnaSeleccionadaId == null) return
        estado = estado.copy(paso = PasoTraballos.FOTOS, erroSubida = null)
    }

    fun volverATraballo() {
        estado = estado.copy(paso = PasoTraballos.TRABALLO, erroSubida = null)
    }

    fun engadirFotos(uris: List<Uri>) {
        estado = estado.copy(fotos = (estado.fotos + uris).distinct(), erroSubida = null)
    }

    fun quitarFoto(uri: Uri) {
        estado = estado.copy(fotos = estado.fotos - uri)
    }

    // ---------- Paso 3: subida en tandas ----------

    /** Reduce e sube as fotos en tandas pequenas: dá progreso real e, se
     * unha tanda falla, as xa subidas non se repiten (quítanse da lista). */
    fun subir(contexto: Context) {
        val columnaId = estado.columnaSeleccionadaId ?: return
        val lote = estado.fotos
        if (lote.isEmpty() || estado.paso == PasoTraballos.SUBINDO) return
        val app = contexto.applicationContext

        estado = estado.copy(paso = PasoTraballos.SUBINDO, subidas = 0, totalSubida = lote.size, erroSubida = null)

        viewModelScope.launch {
            var feitas = 0
            try {
                for (tanda in lote.chunked(TAMANO_TANDA)) {
                    val temporais = mutableListOf<File>()
                    try {
                        val partes = tanda.mapIndexed { i, uri ->
                            val ficheiro = reducirImaxe(app, uri)
                            temporais += ficheiro
                            MultipartBody.Part.createFormData(
                                "ficheiros",
                                "foto_${feitas + i + 1}.jpg",
                                ficheiro.asRequestBody("image/jpeg".toMediaType())
                            )
                        }
                        val resposta = ApiClient.servizo.cargarTraballos(
                            partes,
                            columnaId.toString().toRequestBody("text/plain".toMediaType())
                        )
                        val novos = resposta.resultados.map {
                            ResultadoFoto(
                                clave = UUID.randomUUID().toString(),
                                qrAtopado = it.qr_atopado,
                                alumnoId = it.alumno_id,
                                nomeAlumno = it.nome_alumno,
                                ficheiroId = it.ficheiro_id,
                                pendenteId = it.pendente_id
                            )
                        }
                        feitas += tanda.size
                        estado = estado.copy(
                            subidas = feitas,
                            fotos = estado.fotos - tanda.toSet(),
                            resultados = estado.resultados + novos
                        )
                    } finally {
                        temporais.forEach { it.delete() }
                    }
                }
                estado = estado.copy(paso = PasoTraballos.RESULTADO)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirTraballos", "Erro subindo fotos", e)
                val mensaxe = if (feitas > 0) {
                    "Subíronse $feitas fotos; quedan ${estado.fotos.size}. Proba de novo para enviar as que faltan."
                } else {
                    "Non se puido subir. Comproba a conexión e proba de novo."
                }
                estado = estado.copy(paso = PasoTraballos.FOTOS, erroSubida = mensaxe)
            }
        }
    }

    // ---------- Paso 4: resultado e asignación a man ----------

    fun abrirAsignar(resultado: ResultadoFoto) {
        estado = estado.copy(fotoAAsignar = resultado, erroAsignar = null)
    }

    fun pecharAsignar() {
        estado = estado.copy(fotoAAsignar = null, erroAsignar = null)
    }

    fun asignar(alumno: FilaAlumnoGradebook) {
        val resultado = estado.fotoAAsignar ?: return
        val pendenteId = resultado.pendenteId ?: return
        val columnaId = estado.columnaSeleccionadaId ?: return
        if (estado.asignando) return
        estado = estado.copy(asignando = true, erroAsignar = null)
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.asignarManual(
                    AsignacionManualIn(pendente_id = pendenteId, alumno_id = alumno.alumno_id, columna_id = columnaId)
                )
                estado = estado.copy(
                    asignando = false,
                    fotoAAsignar = null,
                    resultados = estado.resultados.map {
                        if (it.clave == resultado.clave) {
                            it.copy(
                                alumnoId = resposta.alumno_id,
                                nomeAlumno = resposta.nome_alumno,
                                ficheiroId = resposta.ficheiro_id,
                                pendenteId = null
                            )
                        } else it
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirTraballos", "Erro asignando foto", e)
                estado = estado.copy(asignando = false, erroAsignar = "Non se puido asignar. Proba de novo.")
            }
        }
    }

    /** Remata o fluxo: limpa fotos e resultados, mantendo materia e trimestre. */
    fun reiniciar() {
        estado = estado.copy(
            paso = PasoTraballos.TRABALLO,
            fotos = emptyList(),
            resultados = emptyList(),
            subidas = 0,
            totalSubida = 0,
            erroSubida = null,
            fotoAAsignar = null
        )
    }

    private companion object {
        const val TAMANO_TANDA = 3
    }
}
