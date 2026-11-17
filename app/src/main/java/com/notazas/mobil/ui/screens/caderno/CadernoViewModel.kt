package com.notazas.mobil.ui.screens.caderno

import android.app.Application
import java.io.File
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.DestrezaPalabrasUpdate
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.NotaUpdateIn
import com.notazas.mobil.datos.modelos.PonderacionOut
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.local.SeleccionActual
import com.notazas.mobil.datos.offline.ASPECTOS_POR_DEFECTO
import com.notazas.mobil.datos.offline.CacheAsignacionEntrada
import com.notazas.mobil.datos.offline.CacheOffline
import com.notazas.mobil.datos.offline.TRIMESTRES_OFFLINE
import com.notazas.mobil.datos.offline.actitudeAlumnoOfflineGrupo
import com.notazas.mobil.datos.offline.alumnoOfflinePorId
import com.notazas.mobil.datos.offline.comoAsignacion
import com.notazas.mobil.datos.offline.construirCadernoOffline
import com.notazas.mobil.datos.offline.gardarCriterioRecuperacionOffline
import com.notazas.mobil.datos.offline.gardarDestrezaOffline
import com.notazas.mobil.datos.offline.gardarLocalPendente
import com.notazas.mobil.datos.offline.gardarNotaOffline
import com.notazas.mobil.datos.offline.lerAcertosOffline
import com.notazas.mobil.datos.offline.columnaOfflinePorId
import com.notazas.mobil.datos.offline.etiquetaDatosOffline
import com.notazas.mobil.datos.offline.refrescarCacheSeFaiFalta
import com.notazas.mobil.datos.offline.notaDestrezaPalabras
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.datos.util.descargarPdfACache
import com.notazas.mobil.datos.util.ordenadosPorNome
import com.notazas.mobil.ui.screens.destrezas.Destreza
import com.notazas.mobil.ui.screens.destrezas.FollaDestrezas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** Extras do alumno aberto no Caderno: ficheiros (PDF dos exames, fotos
 * dos traballos) e actitude do trimestre. Os ficheiros só existen en
 * liña (viven no disco do servidor do colexio). */
data class DetalleAlumnoCaderno(
    val alumnoId: Int,
    val cargando: Boolean = true,
    /** columnaId do exame -> id do ficheiro PDF escaneado. */
    val pdfExame: Map<Int, Int> = emptyMap(),
    /** columnaId do traballo -> ids das fotos (máis antiga primeiro). */
    val fotos: Map<Int, List<Int>> = emptyMap(),
    val aspectos: List<AspectoActitudeOut> = emptyList(),
    val semanasActitude: List<ActitudeSemanalOut> = emptyList()
)

data class CadernoUiState(
    val cargando: Boolean = true,
    val erro: String? = null,
    /** true = sen servidor accesible; traballando sobre a caché local
     * de BoxAbalar (ver datos/offline/CadernoOffline.kt). */
    val modoFoiraDeLina: Boolean = false,
    /** Cando son os datos da caché offline ("18/10 11:27"); só en modo offline. */
    val datosDe: String? = null,
    val asignacions: List<Asignacion> = emptyList(),
    val asignacionSeleccionada: Asignacion? = null,
    val trimestres: List<Trimestre> = emptyList(),
    val trimestreSeleccionado: Trimestre? = null,
    val columnas: List<ColumnaOut> = emptyList(),
    val ponderacion: PonderacionOut? = null,
    val filas: List<FilaAlumnoGradebook> = emptyList(),
    val busca: String = "",
    val gardando: Boolean = false,
    val erroGardar: String? = null,
    /** (columnaId, alumnoId) da última nota gardada, para o "✓ gardado". */
    val ultimoGardado: Pair<Int, Int>? = null,
    val detalleAlumno: DetalleAlumnoCaderno? = null,
    /** Desglose de palabras acertadas dunha proba de Destrezas, aberto. */
    val folla: FollaDestrezas? = null,
    val follaColumnaId: Int? = null,
    val cargandoFolla: Boolean = false,
    val abrindoFicheiro: Boolean = false,
    /** Mensaxe curta e puntual para amosar (Toast) e logo limpar. */
    val aviso: String? = null
)

class CadernoViewModel(application: Application) : AndroidViewModel(application) {

    var estado by mutableStateOf(CadernoUiState())
        private set

    // Só en modo offline.
    private var entradasOffline: List<CacheAsignacionEntrada> = emptyList()

    /**
     * Relé a asignación DO DISCO (e actualiza a copia en memória). A caché cambia mentres este
     * ViewModel está vivo: as pantallas de Actitude e Destrezas gardan nela, ou o refresco de
     * arranque remata despois de que o Caderno cargase. Usar só `entradasOffline` amosaba datos
     * vellos (as destrezas novas e as semanas de actitude recentes non aparecían ata reiniciar a app).
     */
    private suspend fun entradaFresca(asignacionId: Int): CacheAsignacionEntrada? {
        val doDisco = CacheOffline.lerAsignacion(getApplication(), asignacionId)
            ?: return entradasOffline.firstOrNull { it.asignacion_id == asignacionId }
        entradasOffline = if (entradasOffline.any { it.asignacion_id == asignacionId }) {
            entradasOffline.map { if (it.asignacion_id == asignacionId) doDisco else it }
        } else entradasOffline + doDisco
        return doDisco
    }

    /**
     * Ao entrar no Caderno: se noutra pantalla (p. ex. Destrezas) se cambiou de materia ou de
     * trimestre, segue esa escolla (está gardada en SeleccionActual); se non cambiou nada, só
     * recarga os datos do disco cando estamos sen conexión.
     */
    fun sincronizarSeleccionGardada() {
        if (estado.cargando) return
        val actualA = estado.asignacionSeleccionada ?: return
        val actualT = estado.trimestreSeleccionado ?: return
        val guardadaA = SeleccionActual.asignacionId(getApplication())
        val ordeG = SeleccionActual.trimestreOrde(getApplication())
        val novaA = estado.asignacions.firstOrNull { it.id == guardadaA } ?: actualA
        val novoT = estado.trimestres.firstOrNull { it.orde == ordeG } ?: actualT
        if (novaA.id == actualA.id && novoT.id == actualT.id) {
            recargarSeOffline()
            return
        }
        estado = estado.copy(asignacionSeleccionada = novaA, trimestreSeleccionado = novoT, erroGardar = null)
        recargar(novaA, novoT)
    }

    /** Ao volver a esta pantalla: se estamos sen conexión, recarga os datos do disco. */
    fun recargarSeOffline() {
        if (!estado.modoFoiraDeLina || estado.cargando) return
        val asignacion = estado.asignacionSeleccionada ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        viewModelScope.launch { aplicarOffline(asignacion, trimestre) }
    }

    init {
        cargarInicial()
    }

    // ---------- Carga inicial ----------

    private fun cargarInicial() {
        estado = estado.copy(cargando = true, erro = null)
        viewModelScope.launch {
            try {
                val asignacions = ApiClient.servizo.listarAsignacions()
                val trimestres = ApiClient.servizo.listarTrimestres().filter { it.orde <= 3 }.sortedBy { it.orde }

                // Por defecto: a última materia e trimestre que elixiu o profesor (compartido coas outras
                // pantallas); se non hai, a primeira propia e o trimestre activo.
                val guardada = SeleccionActual.asignacionId(getApplication())
                val asignacionPorDefecto = asignacions.firstOrNull { it.id == guardada }
                    ?: asignacions.firstOrNull { it.esMina } ?: asignacions.firstOrNull()
                val ordeGardada = SeleccionActual.trimestreOrde(getApplication())
                val trimestrePorDefecto = trimestres.firstOrNull { it.orde == ordeGardada }
                    ?: trimestres.firstOrNull { it.activo } ?: trimestres.firstOrNull()

                estado = estado.copy(
                    modoFoiraDeLina = false,
                    asignacions = asignacions,
                    trimestres = trimestres,
                    asignacionSeleccionada = asignacionPorDefecto,
                    trimestreSeleccionado = trimestrePorDefecto
                )

                if (asignacionPorDefecto != null && trimestrePorDefecto != null) {
                    // Mantén fresca a caché do modo sen conexión, pero SEN repetilo en cada apertura do
                    // Caderno (a app xa o fai ao arrancar): só se pasaron máis de 10 min, e en segundo plano.
                    launch(Dispatchers.Default) { refrescarCacheSeFaiFalta(getApplication()) }
                    cargarGradebook(asignacionPorDefecto, trimestrePorDefecto)
                } else {
                    estado = estado.copy(cargando = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CadernoViewModel", "Sen servidor accesible, probando modo fóra do colexio", e)
                cargarInicialOffline()
            }
        }
    }

    private suspend fun cargarInicialOffline() {
        val entradas = CacheOffline.listarAsignacionsCacheadas(getApplication())
        if (entradas.isEmpty()) {
            estado = estado.copy(
                cargando = false,
                erro = "Sen conexión e sen datos gardados no móvil. Abre a app unha vez con conexión ao servidor (cole ou casa) e co teu BoxAbalar persoal configurado na web para que se garden."
            )
            return
        }
        entradasOffline = entradas
        val asignacions = entradas.map { it.comoAsignacion() }.ordenadosPorNome { "${it.grupo} ${it.materia}" }
        val guardada = SeleccionActual.asignacionId(getApplication())
        val asignacion = asignacions.firstOrNull { it.id == guardada } ?: asignacions.first()
        val entrada = entradas.first { it.asignacion_id == asignacion.id }
        // Trimestre por defecto: o que elixiu o profesor; se non, o último que xa ten columnas.
        val ordeGardada = SeleccionActual.trimestreOrde(getApplication())
        val trimestre = TRIMESTRES_OFFLINE.firstOrNull { it.orde == ordeGardada }
            ?: TRIMESTRES_OFFLINE.lastOrNull { entrada.snapshot.trimestres[it.nome]?.columnas?.isNotEmpty() == true }
            ?: TRIMESTRES_OFFLINE.first()

        estado = estado.copy(
            modoFoiraDeLina = true,
            asignacions = asignacions,
            asignacionSeleccionada = asignacion,
            trimestres = TRIMESTRES_OFFLINE,
            trimestreSeleccionado = trimestre,
            erro = null
        )
        aplicarOffline(asignacion, trimestre)
    }

    private suspend fun aplicarOffline(asignacion: Asignacion, trimestre: Trimestre) {
        val entrada = entradaFresca(asignacion.id)
        if (entrada == null) {
            estado = estado.copy(cargando = false, erro = "Non se atopan datos preparados para esta materia.")
            return
        }
        // Copias DO DISCO das outras materias do mesmo grupo (a actitude é compartida entre elas).
        val outras = entradasOffline
            .filter { it.asignacion_id != asignacion.id && it.comoAsignacion().grupo_id == asignacion.grupo_id }
            .mapNotNull { entradaFresca(it.asignacion_id) }
        val datos = withContext(Dispatchers.Default) { construirCadernoOffline(entrada, trimestre.nome, outras) }
        estado = estado.copy(
            cargando = false, erro = null, datosDe = etiquetaDatosOffline(entrada.snapshot.modificado_en),
            columnas = datos.columnas, ponderacion = datos.ponderacion, filas = datos.filas
        )
    }

    // ---------- Selección ----------

    fun seleccionarAsignacion(asignacion: Asignacion) {
        SeleccionActual.gardarAsignacion(getApplication(), asignacion.id)
        estado = estado.copy(asignacionSeleccionada = asignacion, erroGardar = null)
        val trimestre = estado.trimestreSeleccionado ?: return
        recargar(asignacion, trimestre)
    }

    fun seleccionarTrimestre(trimestre: Trimestre) {
        SeleccionActual.gardarTrimestre(getApplication(), trimestre.orde)
        estado = estado.copy(trimestreSeleccionado = trimestre, erroGardar = null)
        val asignacion = estado.asignacionSeleccionada ?: return
        recargar(asignacion, trimestre)
    }

    fun cambiarBusca(valor: String) {
        estado = estado.copy(busca = valor)
    }

    private fun recargar(asignacion: Asignacion, trimestre: Trimestre) {
        if (estado.modoFoiraDeLina) {
            viewModelScope.launch { aplicarOffline(asignacion, trimestre) }
        } else {
            cargarGradebook(asignacion, trimestre)
        }
    }

    private fun cargarGradebook(asignacion: Asignacion, trimestre: Trimestre, silencioso: Boolean = false) {
        if (!silencioso) estado = estado.copy(cargando = true, erro = null)
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.verGradebookTrimestre(asignacion.id, trimestre.id)
                if (estado.asignacionSeleccionada?.id != asignacion.id || estado.trimestreSeleccionado?.id != trimestre.id) return@launch
                estado = estado.copy(
                    cargando = false, erro = null,
                    columnas = resposta.columnas, ponderacion = resposta.ponderacion, filas = resposta.filas
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CadernoViewModel", "Erro en cargarGradebook()", e)
                estado = if (silencioso) estado.copy(cargando = false)
                else estado.copy(cargando = false, erro = "Non se puido cargar o caderno desta asignación.")
            }
        }
    }

    // ---------- Edición de notas ----------

    /** Garda a nota dunha columna (exame, traballo ou recuperación).
     * `onFeito` só se chama se se gardou ben. */
    fun gardarNota(columnaId: Int, alumnoId: Int, valor: Double?, onFeito: () -> Unit = {}) {
        if (estado.gardando) return
        val asignacion = estado.asignacionSeleccionada ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        estado = estado.copy(gardando = true, erroGardar = null)

        viewModelScope.launch {
            try {
                if (estado.modoFoiraDeLina) {
                    val entrada = entradaFresca(asignacion.id) ?: error("Sen datos da materia")
                    val columna = columnaOfflinePorId(entrada, trimestre.nome, columnaId)
                    val alumno = alumnoOfflinePorId(entrada, alumnoId)
                    if (columna == null || alumno == null) {
                        estado = estado.copy(gardando = false, erroGardar = "Non se atopou a columna ou o alumno.")
                        return@launch
                    }
                    val nova = gardarNotaOffline(entrada, trimestre.nome, columna, alumno.nome, alumno.apelidos, valor)
                    gardarOfflineYRefrescar(asignacion, trimestre, nova)
                } else {
                    val info = estado.filas.firstOrNull { it.alumno_id == alumnoId }?.recuperacions?.get(columnaId.toString())
                    // O backend sobrescribe sempre o criterio: hai que reenviar o actual.
                    ApiClient.servizo.gardarNota(
                        NotaUpdateIn(
                            columna_id = columnaId, alumno_id = alumnoId, valor = valor,
                            criterio_recuperacion = info?.criterio,
                            columna_recuperacion_elixida_id = info?.columna_elixida_id
                        )
                    )
                    estado = estado.copy(gardando = false, ultimoGardado = columnaId to alumnoId)
                    cargarGradebook(asignacion, trimestre, silencioso = true)
                }
                onFeito()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CadernoViewModel", "Erro gardando nota", e)
                estado = estado.copy(gardando = false, erroGardar = mensaxeErroGardar(e))
            }
        }
    }

    /** Cambia o criterio de recuperación dun exame para un alumno:
     * "orixinal", "recuperacion" ou "media" (estes dous coa columna elixida). */
    fun cambiarCriterio(exameId: Int, alumnoId: Int, criterio: String, columnaElixidaId: Int?) {
        if (estado.gardando) return
        val asignacion = estado.asignacionSeleccionada ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        estado = estado.copy(gardando = true, erroGardar = null)

        viewModelScope.launch {
            try {
                if (estado.modoFoiraDeLina) {
                    val entrada = entradaFresca(asignacion.id) ?: error("Sen datos da materia")
                    val exame = columnaOfflinePorId(entrada, trimestre.nome, exameId)
                    val alumno = alumnoOfflinePorId(entrada, alumnoId)
                    if (exame == null || alumno == null) {
                        estado = estado.copy(gardando = false, erroGardar = "Non se atopou o exame ou o alumno.")
                        return@launch
                    }
                    val elixida = columnaElixidaId?.let { columnaOfflinePorId(entrada, trimestre.nome, it) }
                    val nova = gardarCriterioRecuperacionOffline(
                        entrada, trimestre.nome, exame, alumno.nome, alumno.apelidos, criterio, elixida
                    )
                    gardarOfflineYRefrescar(asignacion, trimestre, nova)
                } else {
                    // Reenvíase o valor orixinal actual: o backend sobrescribe `valor`.
                    val valorOrixinal = estado.filas.firstOrNull { it.alumno_id == alumnoId }
                        ?.recuperacions?.get(exameId.toString())?.valor_orixinal
                    ApiClient.servizo.gardarNota(
                        NotaUpdateIn(
                            columna_id = exameId, alumno_id = alumnoId, valor = valorOrixinal,
                            criterio_recuperacion = criterio,
                            columna_recuperacion_elixida_id = if (criterio == "orixinal") null else columnaElixidaId
                        )
                    )
                    estado = estado.copy(gardando = false)
                    cargarGradebook(asignacion, trimestre, silencioso = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CadernoViewModel", "Erro gardando criterio", e)
                estado = estado.copy(gardando = false, erroGardar = mensaxeErroGardar(e))
            }
        }
    }

    private suspend fun gardarOfflineYRefrescar(asignacion: Asignacion, trimestre: Trimestre, nova: CacheAsignacionEntrada) {
        gardarLocalPendente(getApplication(), nova)
        entradasOffline = entradasOffline.map { if (it.asignacion_id == nova.asignacion_id) nova else it }
        estado = estado.copy(gardando = false)
        aplicarOffline(asignacion, trimestre)
    }

    /** Marca (columnaId, alumnoId) como "gardado" (✓) tras un gardado ben feito. */
    fun marcarGardado(columnaId: Int, alumnoId: Int) {
        estado = estado.copy(ultimoGardado = columnaId to alumnoId)
    }

    fun limparErroGardar() {
        estado = estado.copy(erroGardar = null)
    }

    // ---------- Extras do alumno: ficheiros e actitude ----------

    private suspend fun <T> intentar(bloque: suspend () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("CadernoViewModel", "Extra do alumno non dispoñible", e)
        null
    }

    /** Carga o PDF dos exames, as fotos dos traballos e a actitude do
     * trimestre do alumno aberto. Os ficheiros só se poden obter en liña. */
    fun cargarDetalleAlumno(alumnoId: Int) {
        val asignacion = estado.asignacionSeleccionada ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        estado = estado.copy(detalleAlumno = DetalleAlumnoCaderno(alumnoId = alumnoId))

        viewModelScope.launch {
            val detalle = if (estado.modoFoiraDeLina) {
                val entrada = entradaFresca(asignacion.id)
                val alumno = entrada?.let { alumnoOfflinePorId(it, alumnoId) }
                val aspectos = (CacheOffline.lerAspectos(getApplication()) ?: ASPECTOS_POR_DEFECTO).sortedBy { it.orde }
                // A actitude é compartida entre as materias do grupo: súnese a de todas as copias.
                val doGrupo = entradasOffline
                    .filter { it.comoAsignacion().grupo_id == asignacion.grupo_id }
                    .mapNotNull { entradaFresca(it.asignacion_id) }
                    .ifEmpty { listOfNotNull(entrada) }
                DetalleAlumnoCaderno(
                    alumnoId = alumnoId, cargando = false, aspectos = aspectos,
                    semanasActitude = if (alumno != null) {
                        actitudeAlumnoOfflineGrupo(doGrupo, trimestre.nome, alumno, aspectos)
                    } else emptyList()
                )
            } else {
                coroutineScope {
                    val notasDetalle = async { intentar { ApiClient.servizo.verNotasDetalleTrimestre(alumnoId, trimestre.id) } }
                    val ficheiros = async { intentar { ApiClient.servizo.listarFicheirosAlumno(alumnoId) } }
                    val aspectos = async { intentar { ApiClient.servizo.listarAspectosActitude() } }
                    val actitude = async { intentar { ApiClient.servizo.verHistoricoActitude(asignacion.id, alumnoId, trimestre.id) } }

                    val pdfExame = notasDetalle.await()
                        ?.firstOrNull { it.asignacion_id == asignacion.id }?.columnas.orEmpty()
                        .filter { it.tipo == "exame" && it.ficheiro_id != null }
                        .associate { it.columna_id to it.ficheiro_id!! }
                    val fotos = ficheiros.await().orEmpty()
                        .filter { it.tipo == "traballo" && it.columna_id != null }
                        .sortedBy { it.data_subida }
                        .groupBy({ it.columna_id!! }, { it.id })
                    DetalleAlumnoCaderno(
                        alumnoId = alumnoId, cargando = false, pdfExame = pdfExame, fotos = fotos,
                        aspectos = aspectos.await().orEmpty().sortedBy { it.orde },
                        semanasActitude = actitude.await().orEmpty().sortedBy { it.semana }
                    )
                }
            }
            // Se mentres tanto se cambiou de alumno, esta resposta xa non vale.
            if (estado.detalleAlumno?.alumnoId == alumnoId) estado = estado.copy(detalleAlumno = detalle)
        }
    }

    /** Descarga o PDF escaneado dun exame á caché e devolve o ficheiro para o visor de PDF da
     * app (VisorPdf). Mentres descarga, `abrindoFicheiro` evita premer dobre. */
    fun abrirPdfExame(ficheiroId: Int, onListo: (File) -> Unit) {
        if (estado.abrindoFicheiro) return
        estado = estado.copy(abrindoFicheiro = true)
        viewModelScope.launch {
            try {
                val corpo = ApiClient.descargarFicheiro(ficheiroId)
                val ficheiro = descargarPdfACache(getApplication(), corpo, "exame_$ficheiroId.pdf")
                estado = estado.copy(abrindoFicheiro = false)
                onListo(ficheiro)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CadernoViewModel", "Erro abrindo o PDF do exame", e)
                estado = estado.copy(abrindoFicheiro = false, aviso = "Non se puido abrir o PDF do exame.")
            }
        }
    }

    fun mostrarAviso(texto: String) {
        estado = estado.copy(aviso = texto)
    }

    fun limparAviso() {
        estado = estado.copy(aviso = null)
    }

    // ---------- Desglose das Destrezas (palabras acertadas) ----------

    fun abrirFollaDestrezas(columnaId: Int, alumnoId: Int) {
        val columna = estado.columnas.firstOrNull { it.id == columnaId } ?: return
        if (estado.cargandoFolla || estado.filas.none { it.alumno_id == alumnoId }) return

        if (estado.modoFoiraDeLina) {
            val asignacion = estado.asignacionSeleccionada ?: return
            val trimestre = estado.trimestreSeleccionado ?: return
            viewModelScope.launch {
                val entrada = entradaFresca(asignacion.id) ?: return@launch
                val alumno = alumnoOfflinePorId(entrada, alumnoId) ?: return@launch
                val (listening, reading, speaking) = lerAcertosOffline(entrada, trimestre.nome, columna.id, alumno.nome, alumno.apelidos)
                mostrarFolla(columnaId, alumnoId, listening.toList(), reading.toList(), speaking.toList())
            }
            return
        }

        estado = estado.copy(cargandoFolla = true, erroGardar = null)
        viewModelScope.launch {
            try {
                // Sempre se le do servidor: asi non se machuca unha avaliación existente ao gardar.
                val existente = ApiClient.servizo.verDestreza(columnaId, alumnoId)
                mostrarFolla(columnaId, alumnoId, existente.acertos_listening, existente.acertos_reading, existente.acertos_speaking)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CadernoViewModel", "Erro abrindo o desglose de destrezas", e)
                estado = estado.copy(cargandoFolla = false, aviso = "Non se puido cargar o desglose deste alumno.")
            }
        }
    }

    private fun mostrarFolla(columnaId: Int, alumnoId: Int, listening: List<Int>, reading: List<Int>, speaking: List<Int>) {
        val indice = estado.filas.indexOfFirst { it.alumno_id == alumnoId }
        if (indice < 0) {
            estado = estado.copy(cargandoFolla = false)
            return
        }
        val verdes = buildSet {
            listening.forEach { add(Destreza.LISTENING to it) }
            reading.forEach { add(Destreza.READING to it) }
            speaking.forEach { add(Destreza.SPEAKING to it) }
        }
        estado = estado.copy(
            cargandoFolla = false,
            follaColumnaId = columnaId,
            folla = FollaDestrezas(alumnoId, estado.filas[indice].nome_completo, indice + 1, estado.filas.size, verdes),
            erroGardar = null
        )
    }

    fun pecharFolla() {
        estado = estado.copy(folla = null, follaColumnaId = null, erroGardar = null)
    }

    fun alternarCela(destreza: Destreza, palabraId: Int) {
        val folla = estado.folla ?: return
        val clave = destreza to palabraId
        val novos = if (clave in folla.verdes) folla.verdes - clave else folla.verdes + clave
        estado = estado.copy(folla = folla.copy(verdes = novos))
    }

    /** Nota /10 en directo (mesma fórmula ca servidor); null se non hai acertos. */
    fun notaFollaEnDirecto(folla: FollaDestrezas, columna: ColumnaOut): Double? = notaDestrezaPalabras(
        columna.palabras.size, columna.pct_listening, columna.pct_reading, columna.pct_speaking,
        folla.verdes.count { it.first == Destreza.LISTENING },
        folla.verdes.count { it.first == Destreza.READING },
        folla.verdes.count { it.first == Destreza.SPEAKING }
    )

    fun gardarFolla(paraSeguinte: Boolean) {
        val folla = estado.folla ?: return
        val columnaId = estado.follaColumnaId ?: return
        val asignacion = estado.asignacionSeleccionada ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        if (estado.gardando) return
        estado = estado.copy(gardando = true, erroGardar = null)

        fun ids(destreza: Destreza) = folla.verdes.filter { it.first == destreza }.map { it.second }

        viewModelScope.launch {
            try {
                if (estado.modoFoiraDeLina) {
                    val entrada = entradaFresca(asignacion.id) ?: error("Sen datos da materia")
                    val columna = estado.columnas.first { it.id == columnaId }
                    val alumno = alumnoOfflinePorId(entrada, folla.alumnoId) ?: error("Alumno non atopado")
                    val (nova, _) = gardarDestrezaOffline(
                        entrada, trimestre.nome, columna.id, alumno.nome, alumno.apelidos,
                        ids(Destreza.LISTENING).toSet(), ids(Destreza.READING).toSet(), ids(Destreza.SPEAKING).toSet()
                    )
                    gardarOfflineYRefrescar(asignacion, trimestre, nova)
                } else {
                    ApiClient.servizo.gardarDestrezaPalabras(
                        DestrezaPalabrasUpdate(
                            columna_id = columnaId, alumno_id = folla.alumnoId,
                            acertos_listening = ids(Destreza.LISTENING),
                            acertos_reading = ids(Destreza.READING),
                            acertos_speaking = ids(Destreza.SPEAKING)
                        )
                    )
                    estado = estado.copy(gardando = false)
                    cargarGradebook(asignacion, trimestre, silencioso = true)
                }
                estado = estado.copy(ultimoGardado = columnaId to folla.alumnoId)
                // `posicion` empeza en 1, así que coincide co índice do seguinte.
                val seguinte = estado.filas.getOrNull(folla.posicion)
                if (paraSeguinte && seguinte != null) abrirFollaDestrezas(columnaId, seguinte.alumno_id) else pecharFolla()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CadernoViewModel", "Erro gardando o desglose de destrezas", e)
                estado = estado.copy(gardando = false, erroGardar = mensaxeErroGardar(e))
            }
        }
    }

    private fun mensaxeErroGardar(e: Exception): String =
        if (e is HttpException && e.code() == 403) "Non tes permiso para editar esta materia."
        else "Non se puido gardar. Comproba a conexión e proba de novo."
}
