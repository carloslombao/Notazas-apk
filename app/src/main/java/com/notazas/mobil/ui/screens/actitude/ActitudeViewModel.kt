package com.notazas.mobil.ui.screens.actitude

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notazas.mobil.datos.modelos.ActitudeSemanalIn
import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.DetalleActitudeIn
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.offline.ASPECTOS_POR_DEFECTO
import com.notazas.mobil.datos.offline.CacheOffline
import com.notazas.mobil.datos.offline.TRIMESTRES_OFFLINE
import com.notazas.mobil.datos.offline.aplicarGardadoActitudeOffline
import com.notazas.mobil.datos.offline.comoAsignacion
import com.notazas.mobil.datos.offline.construirAlumnosOffline
import com.notazas.mobil.datos.offline.gardarLocalPendente
import com.notazas.mobil.datos.offline.horaActualCurta
import com.notazas.mobil.datos.offline.lerAvaliacionsOffline
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.datos.util.SemanaCurso
import com.notazas.mobil.datos.util.ordenadosPorNome
import com.notazas.mobil.datos.util.semanaActual
import com.notazas.mobil.datos.util.xerarSemanasCurso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import retrofit2.HttpException

/** Estado do panel de rexistro dun alumno (bottom sheet). `valores`
 * só contén os aspectos con nivel elixido (aspectoId -> 1..5). */
data class FollaActitude(
    val alumnoId: Int,
    val nome: String,
    val posicion: Int,
    val total: Int,
    val valores: Map<Int, Int> = emptyMap(),
    val observacions: String = ""
) {
    /** Total en base 10 en directo (media sobre 5 escalada a 10). */
    val media10: Double?
        get() = if (valores.isEmpty()) null else valores.values.average() / 5.0 * 10.0
}

data class ActitudeUiState(
    val cargando: Boolean = true,
    val cargandoAvaliacions: Boolean = false,
    val erro: String? = null,
    /** true = non hai servidor accesible; traballando sobre a caché
     * local de BoxAbalar (ver datos/offline/). */
    val modoFoiraDeLina: Boolean = false,
    val grupos: List<Asignacion> = emptyList(),
    val asignacionSeleccionada: Asignacion? = null,
    val trimestre: Trimestre? = null,
    val aspectos: List<AspectoActitudeOut> = emptyList(),
    val semanas: List<SemanaCurso> = emptyList(),
    val semanaSeleccionada: SemanaCurso? = null,
    val alumnos: List<FilaAlumnoGradebook> = emptyList(),
    /** alumnoId -> avaliación da semana elixida (null = sen avaliar).
     * Só ten entrada se a consulta ao servidor foi ben. */
    val avaliacions: Map<Int, ActitudeSemanalOut?> = emptyMap(),
    val folla: FollaActitude? = null,
    val gardando: Boolean = false,
    val erroGardar: String? = null
)

class ActitudeViewModel(application: Application) : AndroidViewModel(application) {

    var estado by mutableStateOf(ActitudeUiState())
        private set

    private val json = Json { ignoreUnknownKeys = true }
    private var traballoAvaliacions: Job? = null

    // Só se usa en modo offline: a entrada de caché completa da
    // asignación seleccionada, para ler/escribir sen volver ao disco
    // en cada tecla.
    private var entradaOfflineActual: com.notazas.mobil.datos.offline.CacheAsignacionEntrada? = null

    init {
        cargarInicial()
    }

    private fun cargarInicial() {
        viewModelScope.launch {
            try {
                val asignacions = ApiClient.servizo.listarAsignacions()
                val trimestres = ApiClient.servizo.listarTrimestres()
                val aspectos = ApiClient.servizo.listarAspectosActitude().sortedBy { it.orde }
                val semanas = xerarSemanasCurso()
                val grupos = gruposPropios(asignacions)
                val asignacion = grupos.firstOrNull()

                estado = estado.copy(
                    modoFoiraDeLina = false,
                    grupos = grupos,
                    asignacionSeleccionada = asignacion,
                    trimestre = trimestres.firstOrNull { it.activo } ?: trimestres.minByOrNull { it.orde },
                    aspectos = aspectos,
                    semanas = semanas,
                    semanaSeleccionada = semanas.semanaActual()
                )

                if (asignacion != null) {
                    cargarAlumnos(asignacion)
                } else {
                    estado = estado.copy(cargando = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Actitude", "Sen servidor accesible, probando modo fóra do colexio", e)
                cargarInicialOffline()
            }
        }
    }

    // ---------- Modo "fóra do colexio" (sen servidor accesible) ----------

    private suspend fun cargarInicialOffline() {
        val contexto = getApplication<Application>()
        val entradas = CacheOffline.listarAsignacionsCacheadas(contexto)
        if (entradas.isEmpty()) {
            estado = estado.copy(
                cargando = false,
                erro = "Sen conexión e sen datos preparados para traballar fóra do colexio. Conéctate unha vez e prepárao en Axustes."
            )
            return
        }

        val aspectos = (CacheOffline.lerAspectos(contexto) ?: ASPECTOS_POR_DEFECTO).sortedBy { it.orde }
        val grupos = entradas.map { it.comoAsignacion() }.distinctBy { it.grupo_id }.ordenadosPorNome { it.grupo }
        val asignacion = grupos.firstOrNull()
        val semanas = xerarSemanasCurso()

        estado = estado.copy(
            modoFoiraDeLina = true,
            grupos = grupos,
            asignacionSeleccionada = asignacion,
            trimestre = TRIMESTRES_OFFLINE.firstOrNull(),
            aspectos = aspectos,
            semanas = semanas,
            semanaSeleccionada = semanas.semanaActual(),
            erro = null
        )

        if (asignacion != null) {
            cargarAlumnosOffline(asignacion, entradas)
        } else {
            estado = estado.copy(cargando = false)
        }
    }

    private suspend fun cargarAlumnosOffline(asignacion: Asignacion, entradasCacheadas: List<com.notazas.mobil.datos.offline.CacheAsignacionEntrada>) {
        // Unha asignación "miña" en offline pode representar varias
        // materias do mesmo grupo (agrupadas por grupo_id sintético);
        // calquera delas serve para ler/escribir a actitude (é
        // compartida). Quedamos coa primeira que cadre.
        val entrada = entradasCacheadas.firstOrNull { it.comoAsignacion().grupo_id == asignacion.grupo_id }
        if (entrada == null) {
            estado = estado.copy(cargando = false, erro = "Non se atopan datos preparados para este grupo.")
            return
        }
        entradaOfflineActual = entrada
        estado = estado.copy(
            cargando = false,
            alumnos = construirAlumnosOffline(entrada).ordenadosPorNome { it.nome_completo }
        )
        cargarAvaliacionsOffline()
    }

    private fun cargarAvaliacionsOffline() {
        val entrada = entradaOfflineActual ?: return
        val trimestre = estado.trimestre ?: return
        val semana = estado.semanaSeleccionada ?: return
        estado = estado.copy(
            avaliacions = lerAvaliacionsOffline(entrada, trimestre.nome, semana.codigo, estado.aspectos),
            cargandoAvaliacions = false
        )
    }

    // ---------- Comúns (en liña e offline) ----------

    fun seleccionarAsignacion(asignacion: Asignacion) {
        estado = estado.copy(asignacionSeleccionada = asignacion)
        if (estado.modoFoiraDeLina) {
            viewModelScope.launch {
                val contexto = getApplication<Application>()
                cargarAlumnosOffline(asignacion, CacheOffline.listarAsignacionsCacheadas(contexto))
            }
        } else {
            cargarAlumnos(asignacion)
        }
    }

    fun seleccionarSemana(semana: SemanaCurso) {
        estado = estado.copy(semanaSeleccionada = semana)
        if (estado.modoFoiraDeLina) cargarAvaliacionsOffline() else cargarAvaliacions()
    }

    private fun cargarAlumnos(asignacion: Asignacion) {
        traballoAvaliacions?.cancel()
        val trimestre = estado.trimestre
        if (trimestre == null) {
            estado = estado.copy(cargando = false, erro = "Non hai ningún trimestre configurado.")
            return
        }
        estado = estado.copy(cargando = true, erro = null, alumnos = emptyList(), avaliacions = emptyMap())
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.verGradebookTrimestre(asignacion.id, trimestre.id)
                // Se mentres tanto cambiou a asignación, esta resposta xa non vale.
                if (estado.asignacionSeleccionada?.id != asignacion.id) return@launch
                estado = estado.copy(
                    cargando = false,
                    alumnos = resposta.filas.ordenadosPorNome { it.nome_completo }
                )
                cargarAvaliacions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Actitude", "Erro cargando alumnos", e)
                estado = estado.copy(cargando = false, erro = "Non se puido cargar o alumnado deste grupo.")
            }
        }
    }

    /** Pide ao servidor a avaliación da semana elixida de cada alumno
     * (en paralelo) para saber quen está avaliado e con que media. */
    private fun cargarAvaliacions() {
        val asignacion = estado.asignacionSeleccionada ?: return
        val semana = estado.semanaSeleccionada ?: return
        val alumnos = estado.alumnos

        traballoAvaliacions?.cancel()
        estado = estado.copy(avaliacions = emptyMap(), cargandoAvaliacions = true, erro = null)

        traballoAvaliacions = viewModelScope.launch {
            val resultados = coroutineScope {
                alumnos.map { alumno ->
                    async {
                        try {
                            val el = ApiClient.servizo.verSemanaConcreta(asignacion.id, alumno.alumno_id, semana.codigo)
                            Triple(alumno.alumno_id, true, el.comoAvaliacion())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("Actitude", "Erro cargando semana de ${alumno.alumno_id}", e)
                            Triple(alumno.alumno_id, false, null)
                        }
                    }
                }.awaitAll()
            }
            val correctos = resultados.filter { it.second }
            val fallos = resultados.size - correctos.size
            estado = estado.copy(
                avaliacions = correctos.associate { it.first to it.third },
                cargandoAvaliacions = false,
                erro = if (fallos > 0) "Non se puideron cargar $fallos avaliación(s). Comproba a conexión." else null
            )
        }
    }

    // ---------- Panel de rexistro (bottom sheet) ----------

    fun abrirFolla(alumnoId: Int) {
        val indice = estado.alumnos.indexOfFirst { it.alumno_id == alumnoId }
        if (indice < 0) return

        if (estado.avaliacions.containsKey(alumnoId) || estado.modoFoiraDeLina) {
            mostrarFolla(indice, estado.avaliacions[alumnoId])
            return
        }

        // Sen dato fiable en caché (a carga da lista fallou ou aínda
        // non chegou): consultamos ese alumno antes de abrir, para non
        // machucar unha avaliación existente ao gardar.
        val asignacion = estado.asignacionSeleccionada ?: return
        val semana = estado.semanaSeleccionada ?: return
        viewModelScope.launch {
            try {
                val el = ApiClient.servizo.verSemanaConcreta(asignacion.id, alumnoId, semana.codigo)
                val existente = el.comoAvaliacion()
                estado = estado.copy(avaliacions = estado.avaliacions + (alumnoId to existente))
                mostrarFolla(indice, existente)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Actitude", "Erro abrindo alumno $alumnoId", e)
                val mensaxe = "Non se puido cargar a avaliación deste alumno."
                estado = if (estado.folla != null) estado.copy(erroGardar = mensaxe) else estado.copy(erro = mensaxe)
            }
        }
    }

    private fun mostrarFolla(indice: Int, existente: ActitudeSemanalOut?) {
        val alumno = estado.alumnos[indice]
        estado = estado.copy(
            folla = FollaActitude(
                alumnoId = alumno.alumno_id,
                nome = alumno.nome_completo,
                posicion = indice + 1,
                total = estado.alumnos.size,
                valores = existente?.detalles?.associate { it.aspecto_id to it.valor } ?: emptyMap(),
                observacions = existente?.observacions.orEmpty()
            ),
            erroGardar = null
        )
    }

    fun pecharFolla() {
        estado = estado.copy(folla = null, erroGardar = null)
    }

    /** Elixe un nivel; tocar o mesmo nivel outra vez deixa o aspecto
     * sen avaliar (igual que na web). */
    fun seleccionarNivel(aspectoId: Int, nivel: Int) {
        val folla = estado.folla ?: return
        val novos = if (folla.valores[aspectoId] == nivel) folla.valores - aspectoId
        else folla.valores + (aspectoId to nivel)
        estado = estado.copy(folla = folla.copy(valores = novos))
    }

    fun cambiarObservacions(texto: String) {
        val folla = estado.folla ?: return
        estado = estado.copy(folla = folla.copy(observacions = texto))
    }

    fun gardar(paraSeguinte: Boolean) {
        if (estado.modoFoiraDeLina) {
            gardarOffline(paraSeguinte)
            return
        }
        val folla = estado.folla ?: return
        val asignacion = estado.asignacionSeleccionada ?: return
        val trimestre = estado.trimestre ?: return
        val semana = estado.semanaSeleccionada ?: return
        if (estado.gardando) return

        val detalles = estado.aspectos.mapNotNull { aspecto ->
            folla.valores[aspecto.id]?.let { DetalleActitudeIn(aspecto.id, it) }
        }
        estado = estado.copy(gardando = true, erroGardar = null)

        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.gardarActitudeSemanal(
                    ActitudeSemanalIn(
                        asignacion_id = asignacion.id,
                        alumno_id = folla.alumnoId,
                        trimestre_id = trimestre.id,
                        semana = semana.codigo,
                        observacions = folla.observacions.ifBlank { null },
                        detalles = detalles
                    )
                )
                estado = estado.copy(
                    gardando = false,
                    avaliacions = estado.avaliacions + (folla.alumnoId to resposta)
                )
                // `posicion` empeza en 1, así que coincide co índice do seguinte.
                val seguinte = estado.alumnos.getOrNull(folla.posicion)
                if (paraSeguinte && seguinte != null) abrirFolla(seguinte.alumno_id) else pecharFolla()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Actitude", "Erro gardando actitude", e)
                val mensaxe = if (e is HttpException && e.code() == 403) {
                    "Non tes permiso para avaliar nesta asignación."
                } else {
                    "Non se puido gardar. Comproba a conexión e proba de novo."
                }
                estado = estado.copy(gardando = false, erroGardar = mensaxe)
            }
        }
    }

    private fun gardarOffline(paraSeguinte: Boolean) {
        val folla = estado.folla ?: return
        val trimestre = estado.trimestre ?: return
        val semana = estado.semanaSeleccionada ?: return
        val entrada = entradaOfflineActual ?: return
        // "nome, apelidos" tal como se construíu en construirAlumnosOffline.
        val (apelidos, nome) = folla.nome.split(", ", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else folla.nome to ""
        }
        if (estado.gardando) return
        estado = estado.copy(gardando = true, erroGardar = null)

        viewModelScope.launch {
            val (entradaNova, resultado) = aplicarGardadoActitudeOffline(
                entrada = entrada,
                trimestreNome = trimestre.nome,
                alumnoNome = nome,
                alumnoApelidos = apelidos,
                semanaCodigo = semana.codigo,
                valoresPorAspectoId = folla.valores,
                observacions = folla.observacions.ifBlank { null },
                aspectos = estado.aspectos
            )
            gardarLocalPendente(getApplication(), entradaNova)
            entradaOfflineActual = entradaNova
            estado = estado.copy(
                gardando = false,
                avaliacions = estado.avaliacions + (folla.alumnoId to resultado),
                erroGardar = "Gardado no móbil ás ${horaActualCurta()} — pendente de sincronizar"
            )
            val seguinte = estado.alumnos.getOrNull(folla.posicion)
            if (paraSeguinte && seguinte != null) abrirFolla(seguinte.alumno_id) else pecharFolla()
        }
    }

    /** A actitude é do profesor e do alumno, non da materia: abonda unha
     * asignación por grupo. Escóllese unha das súas (a que permite gardar);
     * se non ten ningunha propia, unha calquera do grupo (só lectura). */
    private fun gruposPropios(asignacions: List<Asignacion>): List<Asignacion> {
        val propias = asignacions.filter { it.esMina }.ifEmpty { asignacions }
        return propias.distinctBy { it.grupo_id }.ordenadosPorNome { it.grupo }
    }

    private fun JsonElement.comoAvaliacion(): ActitudeSemanalOut? =
        if (this is JsonNull) null else json.decodeFromJsonElement(ActitudeSemanalOut.serializer(), this)
}
