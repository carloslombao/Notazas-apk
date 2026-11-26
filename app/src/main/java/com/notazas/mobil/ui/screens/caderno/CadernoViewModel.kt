package com.notazas.mobil.ui.screens.caderno

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notazas.mobil.datos.local.SeleccionActual
import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.DestrezaPalabrasUpdate
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.NotaUpdateIn
import com.notazas.mobil.datos.modelos.PonderacionOut
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.offline.ASPECTOS_POR_DEFECTO
import com.notazas.mobil.datos.offline.CacheAsignacionEntrada
import com.notazas.mobil.datos.offline.CacheOffline
import com.notazas.mobil.datos.offline.TRIMESTRES_OFFLINE
import com.notazas.mobil.datos.offline.actitudeAlumnoOfflineGrupo
import com.notazas.mobil.datos.offline.alumnoOfflinePorId
import com.notazas.mobil.datos.offline.columnaOfflinePorId
import com.notazas.mobil.datos.offline.comoAsignacion
import com.notazas.mobil.datos.offline.construirCadernoOffline
import com.notazas.mobil.datos.offline.etiquetaDatosOffline
import com.notazas.mobil.datos.offline.gardarCriterioRecuperacionOffline
import com.notazas.mobil.datos.offline.gardarDestrezaOffline
import com.notazas.mobil.datos.offline.gardarLocalPendente
import com.notazas.mobil.datos.offline.gardarNotaOffline
import com.notazas.mobil.datos.offline.lerAcertosOffline
import com.notazas.mobil.datos.offline.notaDestrezaPalabras
import com.notazas.mobil.datos.offline.refrescarCacheSeFaiFalta
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.datos.util.descargarPdfACache
import com.notazas.mobil.datos.util.ordenadosPorNome
import com.notazas.mobil.ui.componentes.corMateria
import com.notazas.mobil.ui.componentes.coresDasMaterias
import com.notazas.mobil.ui.screens.destrezas.Destreza
import com.notazas.mobil.ui.screens.destrezas.FollaDestrezas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File

/** Datos dunha materia (asignación) do grupo no trimestre elixido. */
data class MateriaCaderno(
    val asignacion: Asignacion,
    val columnas: List<ColumnaOut>,
    val ponderacion: PonderacionOut?,
    val filas: List<FilaAlumnoGradebook>
)

/** Un grupo ("1ºA") coas materias que o profesor ten nel. É o único filtro do Caderno. */
data class GrupoCaderno(val id: Int, val nome: String, val asignacions: List<Asignacion>)

data class AlumnoCaderno(val alumnoId: Int, val nomeCompleto: String)

/** Extras do alumno aberto no Caderno: ficheiros (PDF dos exames, fotos dos traballos) e actitude
 * do trimestre. Os ficheiros só existen en liña (viven no disco do servidor do colexio). */
data class DetalleAlumnoCaderno(
    val alumnoId: Int,
    val cargando: Boolean = true,
    /** columnaId do exame (de calquera materia) -> id do ficheiro PDF escaneado. */
    val pdfExame: Map<Int, Int> = emptyMap(),
    /** columnaId do traballo -> ids das fotos (máis antiga primeiro). */
    val fotos: Map<Int, List<Int>> = emptyMap(),
    val aspectos: List<AspectoActitudeOut> = emptyList(),
    val semanasActitude: List<ActitudeSemanalOut> = emptyList()
)

data class CadernoUiState(
    val cargando: Boolean = true,
    val erro: String? = null,
    /** true = sen servidor accesible; traballando sobre a caché local de BoxAbalar. */
    val modoFoiraDeLina: Boolean = false,
    /** Cando son os datos da caché offline ("18/10 11:27"); só en modo offline. */
    val datosDe: String? = null,
    val grupos: List<GrupoCaderno> = emptyList(),
    val grupoSeleccionadoId: Int? = null,
    val trimestres: List<Trimestre> = emptyList(),
    val trimestreSeleccionado: Trimestre? = null,
    /** Materias do grupo elixido no trimestre elixido (cada unha co seu gradebook). */
    val materias: List<MateriaCaderno> = emptyList(),
    /** Alumnado do grupo (unión das filas de todas as materias), ordenado por apelidos. */
    val alumnos: List<AlumnoCaderno> = emptyList(),
    val busca: String = "",
    val gardando: Boolean = false,
    val erroGardar: String? = null,
    /** (asignacionId, columnaId, alumnoId) da última nota gardada, para o "✓ gardado". */
    val ultimoGardado: Triple<Int, Int, Int>? = null,
    val detalleAlumno: DetalleAlumnoCaderno? = null,
    /** Desglose de palabras acertadas dunha proba de Destrezas, aberto. */
    val folla: FollaDestrezas? = null,
    val follaAsignacionId: Int? = null,
    val follaColumnaId: Int? = null,
    val cargandoFolla: Boolean = false,
    val abrindoFicheiro: Boolean = false,
    /** Mensaxe curta e puntual para amosar (Toast) e logo limpar. */
    val aviso: String? = null,
    /** Cor de cada materia do grupo (nunca hai dúas iguais dentro do grupo). */
    val cores: Map<String, Color> = emptyMap()
) {
    fun corDe(materia: String): Color = cores[materia] ?: corMateria(materia)
    val grupoSeleccionado: GrupoCaderno? get() = grupos.firstOrNull { it.id == grupoSeleccionadoId }

    fun materia(asignacionId: Int): MateriaCaderno? = materias.firstOrNull { it.asignacion.id == asignacionId }
}

class CadernoViewModel(application: Application) : AndroidViewModel(application) {

    var estado by mutableStateOf(CadernoUiState())
        private set

    // Só en modo offline.
    private var entradasOffline: List<CacheAsignacionEntrada> = emptyList()

    /**
     * Relé a asignación DO DISCO (e actualiza a copia en memoria). A caché cambia mentres este
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

    init {
        cargarInicial()
    }

    // ---------- Grupos ----------

    private fun construirGrupos(asignacions: List<Asignacion>): List<GrupoCaderno> =
        asignacions.groupBy { it.grupo_id }
            .map { (id, lista) -> GrupoCaderno(id, lista.first().grupo, lista.sortedBy { it.materia }) }
            .sortedBy { it.nome }

    private fun elixirGrupo(grupos: List<GrupoCaderno>): GrupoCaderno? {
        val nomeGardado = SeleccionActual.grupoNome(getApplication())
        return grupos.firstOrNull { it.nome == nomeGardado } ?: grupos.firstOrNull()
    }

    private fun unirAlumnos(materias: List<MateriaCaderno>): List<AlumnoCaderno> =
        materias.flatMap { it.filas }
            .distinctBy { it.alumno_id }
            .map { AlumnoCaderno(it.alumno_id, it.nome_completo) }
            .ordenadosPorNome { it.nomeCompleto }

    // ---------- Carga inicial ----------

    private fun cargarInicial() {
        estado = estado.copy(cargando = true, erro = null)
        viewModelScope.launch {
            try {
                val asignacions = ApiClient.servizo.listarAsignacions()
                val trimestres = ApiClient.servizo.listarTrimestres().filter { it.orde <= 3 }.sortedBy { it.orde }

                val grupos = construirGrupos(asignacions)
                val grupo = elixirGrupo(grupos)
                val ordeGardada = SeleccionActual.trimestreOrde(getApplication())
                val trimestre = trimestres.firstOrNull { it.orde == ordeGardada }
                    ?: trimestres.firstOrNull { it.activo } ?: trimestres.firstOrNull()

                estado = estado.copy(
                    modoFoiraDeLina = false,
                    grupos = grupos,
                    grupoSeleccionadoId = grupo?.id,
                    trimestres = trimestres,
                    trimestreSeleccionado = trimestre
                )

                if (grupo != null && trimestre != null) {
                    // Mantén fresca a caché do modo sen conexión, pero SEN repetilo en cada apertura do
                    // Caderno (a app xa o fai ao arrancar): só se pasaron máis de 10 min, e en segundo plano.
                    launch(Dispatchers.Default) { refrescarCacheSeFaiFalta(getApplication()) }
                    cargarMaterias(grupo, trimestre)
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
                erro = "Sen conexión e sen datos gardados no móbil. Abre a app unha vez con conexión ao servidor (cole ou casa) e co teu BoxAbalar persoal configurado na web para que se garden."
            )
            return
        }
        entradasOffline = entradas
        val grupos = construirGrupos(entradas.map { it.comoAsignacion() })
        val grupo = elixirGrupo(grupos)
        // Trimestre por defecto: o que elixiu o profesor; se non, o último que xa ten columnas.
        val ordeGardada = SeleccionActual.trimestreOrde(getApplication())
        val trimestre = TRIMESTRES_OFFLINE.firstOrNull { it.orde == ordeGardada }
            ?: TRIMESTRES_OFFLINE.lastOrNull { t -> entradas.any { it.snapshot.trimestres[t.nome]?.columnas?.isNotEmpty() == true } }
            ?: TRIMESTRES_OFFLINE.first()

        estado = estado.copy(
            modoFoiraDeLina = true,
            grupos = grupos,
            grupoSeleccionadoId = grupo?.id,
            trimestres = TRIMESTRES_OFFLINE,
            trimestreSeleccionado = trimestre,
            erro = null
        )
        if (grupo != null) aplicarOffline(grupo, trimestre) else estado = estado.copy(cargando = false)
    }

    private suspend fun aplicarOffline(grupo: GrupoCaderno, trimestre: Trimestre) {
        val entradas = grupo.asignacions.mapNotNull { entradaFresca(it.id) }
        if (entradas.isEmpty()) {
            estado = estado.copy(cargando = false, erro = "Non se atopan datos preparados para este grupo.")
            return
        }
        // Cada materia calcúlase con todas as copias do grupo (a actitude é compartida entre elas).
        val materias = withContext(Dispatchers.Default) {
            grupo.asignacions.mapNotNull { asignacion ->
                val entrada = entradas.firstOrNull { it.asignacion_id == asignacion.id } ?: return@mapNotNull null
                val outras = entradas.filter { it.asignacion_id != asignacion.id }
                val datos = construirCadernoOffline(entrada, trimestre.nome, outras)
                MateriaCaderno(asignacion, datos.columnas, datos.ponderacion, datos.filas)
            }
        }
        estado = estado.copy(
            cargando = false, erro = null, datosDe = etiquetaDatosOffline(entradas.first().snapshot.modificado_en),
            materias = materias, alumnos = unirAlumnos(materias),
            cores = coresDasMaterias(materias.map { it.asignacion.materia })
        )
    }

    // ---------- Selección ----------

    fun seleccionarGrupo(grupo: GrupoCaderno) {
        SeleccionActual.gardarGrupo(getApplication(), grupo.nome)
        estado = estado.copy(grupoSeleccionadoId = grupo.id, busca = "", erroGardar = null)
        recargar()
    }

    fun seleccionarTrimestre(trimestre: Trimestre) {
        SeleccionActual.gardarTrimestre(getApplication(), trimestre.orde)
        estado = estado.copy(trimestreSeleccionado = trimestre, erroGardar = null)
        recargar()
    }

    fun cambiarBusca(valor: String) {
        estado = estado.copy(busca = valor)
    }

    /**
     * Ao entrar no Caderno: se noutra pantalla (p. ex. Destrezas) se cambiou de trimestre, segue esa
     * escolla (está gardada en SeleccionActual); se non cambiou nada, só recarga os datos do disco
     * cando estamos sen conexión.
     */
    fun sincronizarSeleccionGardada() {
        if (estado.cargando) return
        val grupoAtual = estado.grupoSeleccionado
        val trimAtual = estado.trimestreSeleccionado ?: return
        val nomeG = SeleccionActual.grupoNome(getApplication())
        val ordeG = SeleccionActual.trimestreOrde(getApplication())
        val novoGrupo = estado.grupos.firstOrNull { it.nome == nomeG } ?: grupoAtual ?: return
        val novoTrim = estado.trimestres.firstOrNull { it.orde == ordeG } ?: trimAtual
        if (novoGrupo.id == grupoAtual?.id && novoTrim.id == trimAtual.id) {
            recargarSeOffline()
            return
        }
        estado = estado.copy(grupoSeleccionadoId = novoGrupo.id, trimestreSeleccionado = novoTrim, erroGardar = null)
        recargar()
    }

    /** Ao volver a esta pantalla: se estamos sen conexión, recarga os datos do disco. */
    fun recargarSeOffline() {
        if (!estado.modoFoiraDeLina || estado.cargando) return
        recargar()
    }

    private fun recargar() {
        val grupo = estado.grupoSeleccionado ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        if (estado.modoFoiraDeLina) {
            viewModelScope.launch { aplicarOffline(grupo, trimestre) }
        } else {
            cargarMaterias(grupo, trimestre)
        }
    }

    private suspend fun <T> intentar(bloque: suspend () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("CadernoViewModel", "Petición non dispoñible", e)
        null
    }

    /** Carga o gradebook de TODAS as materias do grupo en paralelo (en liña). */
    private fun cargarMaterias(grupo: GrupoCaderno, trimestre: Trimestre) {
        estado = estado.copy(cargando = true, erro = null)
        viewModelScope.launch {
            try {
                val resultados = coroutineScope {
                    grupo.asignacions.map { a ->
                        async { a to intentar { ApiClient.servizo.verGradebookTrimestre(a.id, trimestre.id) } }
                    }.awaitAll()
                }
                // Se mentres tanto se cambiou de grupo ou de trimestre, esta resposta xa non vale.
                if (estado.grupoSeleccionadoId != grupo.id || estado.trimestreSeleccionado?.id != trimestre.id) return@launch

                val materias = resultados.mapNotNull { (asignacion, resposta) ->
                    resposta?.let { MateriaCaderno(asignacion, it.columnas, it.ponderacion, it.filas) }
                }
                if (materias.isEmpty()) {
                    estado = estado.copy(cargando = false, erro = "Non se puido cargar o caderno deste grupo.")
                    return@launch
                }
                estado = estado.copy(
                    cargando = false, erro = null, materias = materias, alumnos = unirAlumnos(materias),
                    cores = coresDasMaterias(materias.map { it.asignacion.materia }),
                    aviso = if (materias.size < grupo.asignacions.size) "Algunha materia non se puido cargar." else estado.aviso
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CadernoViewModel", "Erro en cargarMaterias()", e)
                estado = estado.copy(cargando = false, erro = "Non se puido cargar o caderno deste grupo.")
            }
        }
    }

    /** Despois de gardar unha nota en liña: relé só o gradebook desa materia. */
    private suspend fun refrescarMateriaOnline(asignacionId: Int) {
        val trimestre = estado.trimestreSeleccionado ?: return
        val materia = estado.materia(asignacionId) ?: return
        val resposta = intentar { ApiClient.servizo.verGradebookTrimestre(asignacionId, trimestre.id) } ?: return
        if (estado.trimestreSeleccionado?.id != trimestre.id) return
        val novas = estado.materias.map {
            if (it.asignacion.id == asignacionId) MateriaCaderno(materia.asignacion, resposta.columnas, resposta.ponderacion, resposta.filas) else it
        }
        estado = estado.copy(materias = novas, alumnos = unirAlumnos(novas))
    }

    // ---------- Edición de notas ----------

    /** Garda a nota dunha columna (exame, traballo ou recuperación) dunha materia.
     * `onFeito` só se chama se se gardou ben. */
    fun gardarNota(asignacionId: Int, columnaId: Int, alumnoId: Int, valor: Double?, onFeito: () -> Unit = {}) {
        if (estado.gardando) return
        val trimestre = estado.trimestreSeleccionado ?: return
        estado = estado.copy(gardando = true, erroGardar = null)

        viewModelScope.launch {
            try {
                if (estado.modoFoiraDeLina) {
                    val entrada = entradaFresca(asignacionId) ?: error("Sen datos da materia")
                    val columna = columnaOfflinePorId(entrada, trimestre.nome, columnaId)
                    val alumno = alumnoOfflinePorId(entrada, alumnoId)
                    if (columna == null || alumno == null) {
                        estado = estado.copy(gardando = false, erroGardar = "Non se atopou a columna ou o alumno.")
                        return@launch
                    }
                    val nova = gardarNotaOffline(entrada, trimestre.nome, columna, alumno.nome, alumno.apelidos, valor)
                    gardarOfflineYRefrescar(nova)
                } else {
                    val info = estado.materia(asignacionId)?.filas?.firstOrNull { it.alumno_id == alumnoId }
                        ?.recuperacions?.get(columnaId.toString())
                    // O backend sobrescribe sempre o criterio: hai que reenviar o actual.
                    ApiClient.servizo.gardarNota(
                        NotaUpdateIn(
                            columna_id = columnaId, alumno_id = alumnoId, valor = valor,
                            criterio_recuperacion = info?.criterio,
                            columna_recuperacion_elixida_id = info?.columna_elixida_id
                        )
                    )
                    estado = estado.copy(gardando = false, ultimoGardado = Triple(asignacionId, columnaId, alumnoId))
                    refrescarMateriaOnline(asignacionId)
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
    fun cambiarCriterio(asignacionId: Int, exameId: Int, alumnoId: Int, criterio: String, columnaElixidaId: Int?) {
        if (estado.gardando) return
        val trimestre = estado.trimestreSeleccionado ?: return
        estado = estado.copy(gardando = true, erroGardar = null)

        viewModelScope.launch {
            try {
                if (estado.modoFoiraDeLina) {
                    val entrada = entradaFresca(asignacionId) ?: error("Sen datos da materia")
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
                    gardarOfflineYRefrescar(nova)
                } else {
                    // Reenvíase o valor orixinal actual: o backend sobrescribe `valor`.
                    val valorOrixinal = estado.materia(asignacionId)?.filas?.firstOrNull { it.alumno_id == alumnoId }
                        ?.recuperacions?.get(exameId.toString())?.valor_orixinal
                    ApiClient.servizo.gardarNota(
                        NotaUpdateIn(
                            columna_id = exameId, alumno_id = alumnoId, valor = valorOrixinal,
                            criterio_recuperacion = criterio,
                            columna_recuperacion_elixida_id = if (criterio == "orixinal") null else columnaElixidaId
                        )
                    )
                    estado = estado.copy(gardando = false)
                    refrescarMateriaOnline(asignacionId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CadernoViewModel", "Erro gardando criterio", e)
                estado = estado.copy(gardando = false, erroGardar = mensaxeErroGardar(e))
            }
        }
    }

    private suspend fun gardarOfflineYRefrescar(nova: CacheAsignacionEntrada) {
        gardarLocalPendente(getApplication(), nova)
        entradasOffline = entradasOffline.map { if (it.asignacion_id == nova.asignacion_id) nova else it }
        estado = estado.copy(gardando = false)
        val grupo = estado.grupoSeleccionado
        val trimestre = estado.trimestreSeleccionado
        if (grupo != null && trimestre != null) aplicarOffline(grupo, trimestre)
    }

    /** Marca (asignacionId, columnaId, alumnoId) como "gardado" (✓) tras un gardado ben feito. */
    fun marcarGardado(asignacionId: Int, columnaId: Int, alumnoId: Int) {
        estado = estado.copy(ultimoGardado = Triple(asignacionId, columnaId, alumnoId))
    }

    fun limparErroGardar() {
        estado = estado.copy(erroGardar = null)
    }

    // ---------- Extras do alumno: ficheiros e actitude ----------

    /** Carga o PDF dos exames, as fotos dos traballos e a actitude do trimestre do alumno aberto
     * (de TODAS as materias do grupo). Os ficheiros só se poden obter en liña. */
    fun cargarDetalleAlumno(alumnoId: Int) {
        val grupo = estado.grupoSeleccionado ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        estado = estado.copy(detalleAlumno = DetalleAlumnoCaderno(alumnoId = alumnoId))

        viewModelScope.launch {
            val detalle = if (estado.modoFoiraDeLina) {
                val entradas = grupo.asignacions.mapNotNull { entradaFresca(it.id) }
                val alumno = entradas.firstOrNull()?.let { alumnoOfflinePorId(it, alumnoId) }
                val aspectos = (CacheOffline.lerAspectos(getApplication()) ?: ASPECTOS_POR_DEFECTO).sortedBy { it.orde }
                DetalleAlumnoCaderno(
                    alumnoId = alumnoId, cargando = false, aspectos = aspectos,
                    // A actitude é compartida entre as materias do grupo: súmase a de todas as copias.
                    semanasActitude = if (alumno != null) actitudeAlumnoOfflineGrupo(entradas, trimestre.nome, alumno, aspectos) else emptyList()
                )
            } else {
                // A actitude é do profesor e do alumno, non da materia: vale calquera asignación do grupo.
                val asignacionActitude = grupo.asignacions.first().id
                coroutineScope {
                    val notasDetalle = async { intentar { ApiClient.servizo.verNotasDetalleTrimestre(alumnoId, trimestre.id) } }
                    val ficheiros = async { intentar { ApiClient.servizo.listarFicheirosAlumno(alumnoId) } }
                    val aspectos = async { intentar { ApiClient.servizo.listarAspectosActitude() } }
                    val actitude = async { intentar { ApiClient.servizo.verHistoricoActitude(asignacionActitude, alumnoId, trimestre.id) } }

                    val pdfExame = notasDetalle.await().orEmpty()
                        .flatMap { it.columnas }
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

    fun abrirFollaDestrezas(asignacionId: Int, columnaId: Int, alumnoId: Int) {
        val columna = estado.materia(asignacionId)?.columnas?.firstOrNull { it.id == columnaId } ?: return
        if (estado.cargandoFolla || estado.alumnos.none { it.alumnoId == alumnoId }) return

        if (estado.modoFoiraDeLina) {
            val trimestre = estado.trimestreSeleccionado ?: return
            viewModelScope.launch {
                val entrada = entradaFresca(asignacionId) ?: return@launch
                val alumno = alumnoOfflinePorId(entrada, alumnoId) ?: return@launch
                val (listening, reading, speaking) = lerAcertosOffline(entrada, trimestre.nome, columna.id, alumno.nome, alumno.apelidos)
                mostrarFolla(asignacionId, columnaId, alumnoId, listening.toList(), reading.toList(), speaking.toList())
            }
            return
        }

        estado = estado.copy(cargandoFolla = true, erroGardar = null)
        viewModelScope.launch {
            try {
                // Sempre se le do servidor: asi non se machuca unha avaliación existente ao gardar.
                val existente = ApiClient.servizo.verDestreza(columnaId, alumnoId)
                mostrarFolla(asignacionId, columnaId, alumnoId, existente.acertos_listening, existente.acertos_reading, existente.acertos_speaking)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CadernoViewModel", "Erro abrindo o desglose de destrezas", e)
                estado = estado.copy(cargandoFolla = false, aviso = "Non se puido cargar o desglose deste alumno.")
            }
        }
    }

    private fun mostrarFolla(
        asignacionId: Int, columnaId: Int, alumnoId: Int,
        listening: List<Int>, reading: List<Int>, speaking: List<Int>
    ) {
        val indice = estado.alumnos.indexOfFirst { it.alumnoId == alumnoId }
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
            follaAsignacionId = asignacionId,
            follaColumnaId = columnaId,
            folla = FollaDestrezas(alumnoId, estado.alumnos[indice].nomeCompleto, indice + 1, estado.alumnos.size, verdes),
            erroGardar = null
        )
    }

    fun pecharFolla() {
        estado = estado.copy(folla = null, follaAsignacionId = null, follaColumnaId = null, erroGardar = null)
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
        val asignacionId = estado.follaAsignacionId ?: return
        val trimestre = estado.trimestreSeleccionado ?: return
        if (estado.gardando) return
        estado = estado.copy(gardando = true, erroGardar = null)

        fun ids(destreza: Destreza) = folla.verdes.filter { it.first == destreza }.map { it.second }

        viewModelScope.launch {
            try {
                if (estado.modoFoiraDeLina) {
                    val entrada = entradaFresca(asignacionId) ?: error("Sen datos da materia")
                    val columna = estado.materia(asignacionId)?.columnas?.first { it.id == columnaId } ?: error("Columna non atopada")
                    val alumno = alumnoOfflinePorId(entrada, folla.alumnoId) ?: error("Alumno non atopado")
                    val (nova, _) = gardarDestrezaOffline(
                        entrada, trimestre.nome, columna.id, alumno.nome, alumno.apelidos,
                        ids(Destreza.LISTENING).toSet(), ids(Destreza.READING).toSet(), ids(Destreza.SPEAKING).toSet()
                    )
                    gardarOfflineYRefrescar(nova)
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
                    refrescarMateriaOnline(asignacionId)
                }
                estado = estado.copy(ultimoGardado = Triple(asignacionId, columnaId, folla.alumnoId))
                // `posicion` empeza en 1, así que coincide co índice do seguinte.
                val seguinte = estado.alumnos.getOrNull(folla.posicion)
                if (paraSeguinte && seguinte != null) abrirFollaDestrezas(asignacionId, columnaId, seguinte.alumnoId) else pecharFolla()
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
