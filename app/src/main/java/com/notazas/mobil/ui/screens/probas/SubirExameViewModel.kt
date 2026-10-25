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
import com.notazas.mobil.datos.modelos.AsignacionManualExameIn
import com.notazas.mobil.datos.modelos.ColumnaCreate
import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.NotaUpdateIn
import com.notazas.mobil.datos.modelos.OpcionRecuperacion
import com.notazas.mobil.datos.modelos.ResultadoSegmentoExame
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.datos.util.gardarEAbrirPdf
import com.notazas.mobil.datos.util.ordenadosPorNome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream

enum class ModoExame { EXAME, RECUPERACION }
enum class PasoExame { PROBA, SUBINDO, RESULTADO }

/** Un alumno que ten nota no exame orixinal E na recuperación que
 * acabamos de subir: hai que decidir que facer con esa recuperación. */
data class RecuperacionPendente(
    val alumnoId: Int,
    val nome: String,
    val valorOrixinal: Double,
    val valorRecuperacion: Double,
    val examOrixinalColumnaId: Int,
    val recuperacionColumnaId: Int
)

data class SubirExameUiState(
    val modo: ModoExame = ModoExame.EXAME,
    val cargando: Boolean = true,
    val erro: String? = null,
    val asignacions: List<Asignacion> = emptyList(),
    val asignacionSeleccionada: Asignacion? = null,
    val trimestres: List<Trimestre> = emptyList(),
    val trimestreSeleccionado: Trimestre? = null,
    val cargandoColumnas: Boolean = false,
    val columnasExame: List<ColumnaOut> = emptyList(),
    val examOrixinalSeleccionadoId: Int? = null,
    val columnasRecuperacion: List<OpcionRecuperacion> = emptyList(),
    val columnaSeleccionadaId: Int? = null,
    val alumnos: List<FilaAlumnoGradebook> = emptyList(),
    val paso: PasoExame = PasoExame.PROBA,
    val mostrarNovo: Boolean = false,
    val gardandoNovo: Boolean = false,
    val erroNovo: String? = null,
    val subindo: Boolean = false,
    val erroSubida: String? = null,
    val resultados: List<ResultadoSegmentoExame> = emptyList(),
    val paxinasSenAsignar: Int = 0,
    val recuperacionsPendentes: List<RecuperacionPendente> = emptyList(),
    val resolvendoRecuperacion: Set<Int> = emptySet(),
    val recuperacionsResoltas: Set<Int> = emptySet(),
    val erroResolver: String? = null,
    val resultadoAAsignar: ResultadoSegmentoExame? = null,
    val notaAsignar: String = "",
    val asignando: Boolean = false,
    val erroAsignar: String? = null,
    val abrindoPdf: Boolean = false
) {
    val columnaSeleccionadaNome: String?
        get() = when (modo) {
            ModoExame.EXAME -> columnasExame.firstOrNull { it.id == columnaSeleccionadaId }?.nome
            ModoExame.RECUPERACION -> columnasRecuperacion.firstOrNull { it.columna_id == columnaSeleccionadaId }?.nome
        }
}

class SubirExameViewModel : ViewModel() {

    var estado by mutableStateOf(SubirExameUiState())
        private set

    init {
        cargarInicial()
    }

    // ---------- Materia, trimestre e proba ----------

    private fun cargarInicial() {
        viewModelScope.launch {
            try {
                val propias = ApiClient.servizo.listarAsignacions().filter { it.esMina }
                val trimestres = ApiClient.servizo.listarTrimestres().filter { it.orde <= 3 }.sortedBy { it.orde }
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
                Log.e("SubirExame", "Erro na carga inicial", e)
                estado = estado.copy(cargando = false, erro = "Non se puido cargar a información. Comproba a conexión.")
            }
        }
    }

    fun seleccionarAsignacion(asignacion: Asignacion) {
        estado = estado.copy(asignacionSeleccionada = asignacion, columnaSeleccionadaId = null, examOrixinalSeleccionadoId = null)
        recargarColumnas()
    }

    fun seleccionarTrimestre(trimestre: Trimestre) {
        estado = estado.copy(trimestreSeleccionado = trimestre, columnaSeleccionadaId = null, examOrixinalSeleccionadoId = null)
        recargarColumnas()
    }

    fun cambiarModo(modo: ModoExame) {
        if (modo == estado.modo) return
        estado = estado.copy(modo = modo, columnaSeleccionadaId = null, examOrixinalSeleccionadoId = null, columnasRecuperacion = emptyList())
    }

    private fun recargarColumnas(columnaASeleccionar: Int? = null) {
        val asignacion = estado.asignacionSeleccionada
        val trimestre = estado.trimestreSeleccionado
        if (asignacion == null || trimestre == null) {
            estado = estado.copy(columnasExame = emptyList(), alumnos = emptyList(), columnaSeleccionadaId = null)
            return
        }
        estado = estado.copy(cargandoColumnas = true, erro = null)
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.verGradebookTrimestre(asignacion.id, trimestre.id)
                if (estado.asignacionSeleccionada?.id != asignacion.id || estado.trimestreSeleccionado?.id != trimestre.id) return@launch

                val exames = resposta.columnas.filter { it.tipo == "exame" }
                val elixidaExame = estado.examOrixinalSeleccionadoId?.takeIf { id -> exames.any { it.id == id } }
                estado = estado.copy(
                    cargandoColumnas = false,
                    columnasExame = exames,
                    alumnos = resposta.filas.ordenadosPorNome { it.nome_completo },
                    examOrixinalSeleccionadoId = elixidaExame
                )
                recalcularRecuperacions()
                val elixida = columnaASeleccionar ?: estado.columnaSeleccionadaId?.let { id ->
                    val existe = when (estado.modo) {
                        ModoExame.EXAME -> exames.any { it.id == id }
                        ModoExame.RECUPERACION -> estado.columnasRecuperacion.any { it.columna_id == id }
                    }
                    if (existe) id else null
                }
                estado = estado.copy(columnaSeleccionadaId = elixida)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirExame", "Erro cargando exames", e)
                estado = estado.copy(cargandoColumnas = false, erro = "Non se puideron cargar os exames deste trimestre.")
            }
        }
    }

    fun seleccionarExameOrixinal(id: Int) {
        estado = estado.copy(examOrixinalSeleccionadoId = id, columnaSeleccionadaId = null)
        recalcularRecuperacions()
    }

    /** As opcións de recuperación dun exame veñen xa resoltas en
     * calquera fila do gradebook (son as mesmas para toda a clase) —
     * non fai falta outra chamada ao servidor. */
    private fun recalcularRecuperacions() {
        val examId = estado.examOrixinalSeleccionadoId
        val opcions = if (examId != null) {
            estado.alumnos.firstOrNull()?.recuperacions?.get(examId.toString())?.opcions.orEmpty()
        } else emptyList()
        estado = estado.copy(columnasRecuperacion = opcions)
    }

    fun seleccionarColumna(id: Int) {
        estado = estado.copy(columnaSeleccionadaId = id)
    }

    // ---------- Novo exame / nova recuperación ----------

    fun abrirNovo() {
        estado = estado.copy(mostrarNovo = true, erroNovo = null)
    }

    fun pecharNovo() {
        estado = estado.copy(mostrarNovo = false, erroNovo = null)
    }

    fun crearColumna(nome: String, data: String?) {
        val asignacion = estado.asignacionSeleccionada ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        val recuperacionDe = if (estado.modo == ModoExame.RECUPERACION) estado.examOrixinalSeleccionadoId else null
        if (estado.modo == ModoExame.RECUPERACION && recuperacionDe == null) {
            estado = estado.copy(erroNovo = "Elixe primeiro de que exame é a recuperación.")
            return
        }
        if (nome.isBlank()) {
            estado = estado.copy(erroNovo = "Escribe un nome.")
            return
        }
        if (estado.gardandoNovo) return
        estado = estado.copy(gardandoNovo = true, erroNovo = null)
        viewModelScope.launch {
            try {
                val nova = ApiClient.servizo.crearColumna(
                    asignacion.id,
                    ColumnaCreate(
                        trimestre_id = trimestre.id,
                        tipo = if (recuperacionDe != null) "recuperacion" else "exame",
                        nome = nome.trim(),
                        data = data,
                        columna_orixinal_id = recuperacionDe
                    )
                )
                estado = estado.copy(mostrarNovo = false, gardandoNovo = false)
                recargarColumnas(columnaASeleccionar = nova.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirExame", "Erro creando columna", e)
                val mensaxe = if (e is HttpException && e.code() == 403) {
                    "Non tes permiso para crear probas nesta materia."
                } else {
                    "Non se puido crear. Comproba a conexión e proba de novo."
                }
                estado = estado.copy(gardandoNovo = false, erroNovo = mensaxe)
            }
        }
    }

    // ---------- Subida do PDF ----------

    fun limparErroSubida() {
        estado = estado.copy(erroSubida = null)
    }

    fun subirPdf(contexto: Context, uri: Uri) {
        val columnaId = estado.columnaSeleccionadaId ?: return
        if (estado.subindo) return
        estado = estado.copy(paso = PasoExame.SUBINDO, subindo = true, erroSubida = null)

        viewModelScope.launch {
            var temporal: File? = null
            try {
                val ficheiro = withContext(Dispatchers.IO) {
                    val destino = File.createTempFile("exame_", ".pdf", contexto.cacheDir)
                    contexto.contentResolver.openInputStream(uri)?.use { entrada ->
                        FileOutputStream(destino).use { saida -> entrada.copyTo(saida) }
                    } ?: throw java.io.IOException("Non se puido ler o PDF escollido")
                    destino
                }
                temporal = ficheiro
                val parte = MultipartBody.Part.createFormData(
                    "pdf", "exame.pdf", ficheiro.asRequestBody("application/pdf".toMediaType())
                )
                val resposta = ApiClient.servizo.escanearExames(columnaId, parte)

                estado = estado.copy(
                    paso = PasoExame.RESULTADO,
                    subindo = false,
                    resultados = resposta.resultados,
                    paxinasSenAsignar = resposta.paxinas_sen_asignar
                )
                if (estado.modo == ModoExame.RECUPERACION) calcularRecuperacionsPendentes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirExame", "Erro subindo o PDF", e)
                estado = estado.copy(
                    paso = PasoExame.PROBA,
                    subindo = false,
                    erroSubida = "Non se puido subir o PDF. Comproba a conexión e proba de novo."
                )
            } finally {
                temporal?.delete()
            }
        }
    }

    /** Cruza os resultados da recuperación recén subida coa nota
     * orixinal que xa tiña cada alumno (do gradebook cargado antes da
     * subida) para saber a quen hai que preguntarlle que facer. */
    private fun calcularRecuperacionsPendentes() {
        val examId = estado.examOrixinalSeleccionadoId ?: return
        val recuperacionColumnaId = estado.columnaSeleccionadaId ?: return
        val pendentes = estado.resultados.mapNotNull { resultado ->
            val alumnoId = resultado.alumno_id ?: return@mapNotNull null
            val valorRecup = resultado.nota_lida ?: return@mapNotNull null
            val fila = estado.alumnos.firstOrNull { it.alumno_id == alumnoId } ?: return@mapNotNull null
            val valorOrix = fila.recuperacions[examId.toString()]?.valor_orixinal ?: return@mapNotNull null
            RecuperacionPendente(alumnoId, fila.nome_completo, valorOrix, valorRecup, examId, recuperacionColumnaId)
        }
        estado = estado.copy(recuperacionsPendentes = pendentes, recuperacionsResoltas = emptySet(), erroResolver = null)
    }

    fun resolverRecuperacion(pendente: RecuperacionPendente, criterio: String) {
        if (pendente.alumnoId in estado.resolvendoRecuperacion) return
        estado = estado.copy(resolvendoRecuperacion = estado.resolvendoRecuperacion + pendente.alumnoId, erroResolver = null)
        viewModelScope.launch {
            try {
                // O backend sobrescribe sempre `valor`, así que primeiro lemos
                // o valor actual do exame orixinal para reenvialo sen tocar.
                val actual = ApiClient.servizo.verNotaSimple(pendente.examOrixinalColumnaId, pendente.alumnoId)
                ApiClient.servizo.gardarNota(
                    NotaUpdateIn(
                        columna_id = pendente.examOrixinalColumnaId,
                        alumno_id = pendente.alumnoId,
                        valor = actual.valor,
                        criterio_recuperacion = criterio,
                        columna_recuperacion_elixida_id = pendente.recuperacionColumnaId
                    )
                )
                estado = estado.copy(
                    resolvendoRecuperacion = estado.resolvendoRecuperacion - pendente.alumnoId,
                    recuperacionsResoltas = estado.recuperacionsResoltas + pendente.alumnoId
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirExame", "Erro resolvendo recuperación de ${pendente.alumnoId}", e)
                estado = estado.copy(
                    resolvendoRecuperacion = estado.resolvendoRecuperacion - pendente.alumnoId,
                    erroResolver = "Non se puido gardar para ${pendente.nome}. Proba de novo."
                )
            }
        }
    }

    // ---------- Asignación manual (QR non recoñecido) ----------

    fun abrirAsignar(resultado: ResultadoSegmentoExame) {
        // O QR non se recoñeceu, pero o OCR da cela da nota pode ter ido
        // ben igualmente — precargámola para que só haxa que confirmala
        // ou corrixila, non escribila enteira a man.
        val notaPrecargada = resultado.nota_lida?.let { formatarNotaParaEditar(it) } ?: ""
        estado = estado.copy(resultadoAAsignar = resultado, notaAsignar = notaPrecargada, erroAsignar = null)
    }

    /** "7" se é enteira, "7,5" se ten decimais — coma a escribiría o
     * profesor a man no campo de nota. */
    private fun formatarNotaParaEditar(valor: Double): String {
        val arredondada = Math.round(valor * 100) / 100.0
        return if (arredondada == Math.floor(arredondada)) {
            arredondada.toInt().toString()
        } else {
            "%.2f".format(arredondada).trimEnd('0').trimEnd('.').replace(".", ",")
        }
    }

    fun pecharAsignar() {
        estado = estado.copy(resultadoAAsignar = null, erroAsignar = null)
    }

    fun cambiarNotaAsignar(texto: String) {
        // Admite coma ou punto decimal, só díxitos.
        if (texto.isEmpty() || texto.matches(Regex("^\\d{0,2}([.,]\\d{0,2})?$"))) {
            estado = estado.copy(notaAsignar = texto)
        }
    }

    fun asignar(alumno: FilaAlumnoGradebook) {
        val resultado = estado.resultadoAAsignar ?: return
        val pendenteId = resultado.pendente_id ?: return
        val columnaId = estado.columnaSeleccionadaId ?: return
        val nota = estado.notaAsignar.replace(",", ".").toDoubleOrNull()
        if (estado.asignando) return
        estado = estado.copy(asignando = true, erroAsignar = null)
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.asignarManualExame(
                    columnaId, AsignacionManualExameIn(pendente_id = pendenteId, alumno_id = alumno.alumno_id, nota = nota)
                )
                estado = estado.copy(
                    asignando = false,
                    resultadoAAsignar = null,
                    resultados = estado.resultados.map { if (it.pendente_id == pendenteId) resposta else it }
                )
                if (estado.modo == ModoExame.RECUPERACION) calcularRecuperacionsPendentes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirExame", "Erro asignando exame", e)
                estado = estado.copy(asignando = false, erroAsignar = "Non se puido asignar. Proba de novo.")
            }
        }
    }

    fun verPdfPendente(contexto: Context, resultado: ResultadoSegmentoExame) {
        val pendenteId = resultado.pendente_id ?: return
        if (estado.abrindoPdf) return
        estado = estado.copy(abrindoPdf = true)
        viewModelScope.launch {
            try {
                val corpo = ApiClient.servizo.verExamePendentePdf(pendenteId)
                val intent = gardarEAbrirPdf(contexto, corpo, "exame_pendente.pdf")
                contexto.startActivity(intent)
                estado = estado.copy(abrindoPdf = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SubirExame", "Erro abrindo o PDF pendente", e)
                estado = estado.copy(abrindoPdf = false, erroAsignar = "Non se puido abrir o PDF. Comproba se hai algunha app para ver PDF no móbil.")
            }
        }
    }

    fun reiniciar() {
        estado = estado.copy(
            paso = PasoExame.PROBA,
            resultados = emptyList(),
            paxinasSenAsignar = 0,
            recuperacionsPendentes = emptyList(),
            recuperacionsResoltas = emptySet(),
            erroResolver = null,
            erroSubida = null
        )
    }
}
