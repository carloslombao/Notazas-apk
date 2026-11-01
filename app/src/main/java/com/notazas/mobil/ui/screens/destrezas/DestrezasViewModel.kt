package com.notazas.mobil.ui.screens.destrezas

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.ColumnaCreate
import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.DestrezaPalabrasUpdate
import com.notazas.mobil.datos.modelos.FilaAlumnoGradebook
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.offline.CacheAsignacionEntrada
import com.notazas.mobil.datos.offline.CacheOffline
import com.notazas.mobil.datos.offline.TRIMESTRES_OFFLINE
import com.notazas.mobil.datos.offline.columnasDestrezas
import com.notazas.mobil.datos.offline.comoAsignacion
import com.notazas.mobil.datos.offline.comoColumnaOut
import com.notazas.mobil.datos.offline.construirAlumnosOffline
import com.notazas.mobil.datos.offline.crearProbaDestrezasOffline
import com.notazas.mobil.datos.offline.gardarDestrezaOffline
import com.notazas.mobil.datos.offline.gardarLocalPendente
import com.notazas.mobil.datos.offline.horaActualCurta
import com.notazas.mobil.datos.offline.idColumnaOffline
import com.notazas.mobil.datos.offline.lerAcertosOffline
import com.notazas.mobil.datos.offline.lerNotasDestrezasOffline
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.datos.util.ordenadosPorNome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import kotlin.math.abs

/** Destreza avaliada nunha cela da táboa. */
enum class Destreza { LISTENING, READING, SPEAKING }

/** Estado do panel de avaliación dun alumno. `verdes` só contén as
 * combinacións (destreza, palabraId) marcadas como acerto. */
data class FollaDestrezas(
    val alumnoId: Int,
    val nome: String,
    val posicion: Int,
    val total: Int,
    val verdes: Set<Pair<Destreza, Int>> = emptySet()
)

data class DestrezasUiState(
    val cargando: Boolean = true,
    val erro: String? = null,
    /** true = sen servidor accesible; traballando sobre a caché local
     * de BoxAbalar (ver datos/offline/). */
    val modoFoiraDeLina: Boolean = false,
    val asignacions: List<Asignacion> = emptyList(),
    val asignacionSeleccionada: Asignacion? = null,
    val trimestres: List<Trimestre> = emptyList(),
    val trimestreSeleccionado: Trimestre? = null,
    val cargandoColumnas: Boolean = false,
    val columnas: List<ColumnaOut> = emptyList(),
    val columnaSeleccionadaId: Int? = null,
    val alumnos: List<FilaAlumnoGradebook> = emptyList(),
    /** alumnoId -> nota /10 desta proba. Só ten entrada se a consulta
     * ao servidor foi ben (valor null = sen avaliar). */
    val notas: Map<Int, Double?> = emptyMap(),
    val cargandoNotas: Boolean = false,
    val mostrarNova: Boolean = false,
    val gardandoNova: Boolean = false,
    val erroNova: String? = null,
    val folla: FollaDestrezas? = null,
    val cargandoFolla: Boolean = false,
    val gardando: Boolean = false,
    val erroGardar: String? = null
) {
    val columnaSeleccionada: ColumnaOut?
        get() = columnas.firstOrNull { it.id == columnaSeleccionadaId }
}

class DestrezasViewModel(application: Application) : AndroidViewModel(application) {

    var estado by mutableStateOf(DestrezasUiState())
        private set

    // Só en modo offline: a entrada de caché da asignación seleccionada.
    private var entradaOfflineActual: CacheAsignacionEntrada? = null

    init {
        cargarInicial()
    }

    // ---------- Paso 1: materia, trimestre e proba ----------

    private fun cargarInicial() {
        viewModelScope.launch {
            try {
                val propias = ApiClient.servizo.listarAsignacions().filter { it.esMina }
                val trimestres = ApiClient.servizo.listarTrimestres()
                    .filter { it.orde <= 3 }
                    .sortedBy { it.orde }
                estado = estado.copy(
                    modoFoiraDeLina = false,
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
                Log.w("Destrezas", "Sen servidor accesible, probando modo fóra do colexio", e)
                cargarInicialOffline()
            }
        }
    }

    // ---------- Modo "fóra do colexio" ----------

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
        // A diferencia da Actitude, as Destrezas SON por materia (non
        // compartidas entre materias do mesmo grupo): unha entrada por
        // asignación, sen deduplicar.
        val asignacions = entradas.map { it.comoAsignacion() }.ordenadosPorNome { it.grupo }
        val asignacion = asignacions.firstOrNull()

        estado = estado.copy(
            modoFoiraDeLina = true,
            asignacions = asignacions,
            asignacionSeleccionada = asignacion,
            trimestres = TRIMESTRES_OFFLINE,
            trimestreSeleccionado = TRIMESTRES_OFFLINE.firstOrNull(),
            erro = null
        )

        if (asignacion != null) {
            recargarColumnasOffline(asignacion, entradas, null)
        } else {
            estado = estado.copy(cargando = false)
        }
    }

    private fun recargarColumnasOffline(asignacion: Asignacion, entradas: List<CacheAsignacionEntrada>, columnaASeleccionar: Int?) {
        val entrada = entradas.firstOrNull { it.asignacion_id == asignacion.id }
        val trimestre = estado.trimestreSeleccionado
        if (entrada == null || trimestre == null) {
            estado = estado.copy(cargando = false, erro = "Non se atopan datos preparados para esta materia.")
            return
        }
        entradaOfflineActual = entrada
        val probas = entrada.columnasDestrezas(trimestre.nome).map { it.comoColumnaOut(entrada.asignacion_id) }
        val elixida = columnaASeleccionar
            ?: estado.columnaSeleccionadaId?.takeIf { id -> probas.any { it.id == id } }
            ?: probas.singleOrNull()?.id
        estado = estado.copy(
            cargando = false,
            columnas = probas,
            columnaSeleccionadaId = elixida,
            alumnos = construirAlumnosOffline(entrada).ordenadosPorNome { it.nome_completo }
        )
        cargarNotasOffline()
    }

    private fun cargarNotasOffline() {
        val entrada = entradaOfflineActual
        val columna = estado.columnaSeleccionada
        if (entrada == null || columna == null) {
            estado = estado.copy(notas = emptyMap())
            return
        }
        val trimestreNome = estado.trimestreSeleccionado?.nome ?: return
        estado = estado.copy(notas = lerNotasDestrezasOffline(entrada, trimestreNome, columna.id), cargandoNotas = false)
    }

    // ---------- Comúns ----------

    fun seleccionarAsignacion(asignacion: Asignacion) {
        estado = estado.copy(asignacionSeleccionada = asignacion, columnaSeleccionadaId = null, notas = emptyMap())
        if (estado.modoFoiraDeLina) {
            viewModelScope.launch {
                recargarColumnasOffline(asignacion, CacheOffline.listarAsignacionsCacheadas(getApplication()), null)
            }
        } else {
            recargarColumnas()
        }
    }

    fun seleccionarTrimestre(trimestre: Trimestre) {
        estado = estado.copy(trimestreSeleccionado = trimestre, columnaSeleccionadaId = null, notas = emptyMap())
        if (estado.modoFoiraDeLina) {
            val asignacion = estado.asignacionSeleccionada ?: return
            viewModelScope.launch {
                recargarColumnasOffline(asignacion, CacheOffline.listarAsignacionsCacheadas(getApplication()), null)
            }
        } else {
            recargarColumnas()
        }
    }

    fun seleccionarColumna(id: Int) {
        estado = estado.copy(columnaSeleccionadaId = id, notas = emptyMap())
        if (estado.modoFoiraDeLina) cargarNotasOffline() else cargarNotas()
    }

    private fun recargarColumnas(columnaASeleccionar: Int? = null) {
        val asignacion = estado.asignacionSeleccionada
        val trimestre = estado.trimestreSeleccionado
        if (asignacion == null || trimestre == null) {
            estado = estado.copy(columnas = emptyList(), alumnos = emptyList(), columnaSeleccionadaId = null, notas = emptyMap())
            return
        }
        estado = estado.copy(cargandoColumnas = true, erro = null)
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.verGradebookTrimestre(asignacion.id, trimestre.id)
                if (estado.asignacionSeleccionada?.id != asignacion.id || estado.trimestreSeleccionado?.id != trimestre.id) return@launch

                val probas = resposta.columnas.filter { it.tipo == "destrezas" }
                val elixida = columnaASeleccionar
                    ?: estado.columnaSeleccionadaId?.takeIf { id -> probas.any { it.id == id } }
                    ?: probas.singleOrNull()?.id
                estado = estado.copy(
                    cargandoColumnas = false,
                    columnas = probas,
                    columnaSeleccionadaId = elixida,
                    alumnos = resposta.filas.ordenadosPorNome { it.nome_completo }
                )
                cargarNotas()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Destrezas", "Erro cargando probas", e)
                estado = estado.copy(cargandoColumnas = false, erro = "Non se puideron cargar as probas deste trimestre.")
            }
        }
    }

    private fun cargarNotas() {
        val columnaId = estado.columnaSeleccionadaId
        val alumnos = estado.alumnos
        if (columnaId == null || alumnos.isEmpty()) {
            estado = estado.copy(notas = emptyMap(), cargandoNotas = false)
            return
        }
        estado = estado.copy(notas = emptyMap(), cargandoNotas = true)
        viewModelScope.launch {
            try {
                val resultados = coroutineScope {
                    alumnos.map { alumno ->
                        async {
                            try {
                                Triple(alumno.alumno_id, true, ApiClient.servizo.verDestreza(columnaId, alumno.alumno_id).valor)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e("Destrezas", "Erro cargando nota de ${alumno.alumno_id}", e)
                                Triple(alumno.alumno_id, false, null)
                            }
                        }
                    }.awaitAll()
                }
                if (estado.columnaSeleccionadaId != columnaId) return@launch
                val correctos = resultados.filter { it.second }
                val fallos = resultados.size - correctos.size
                estado = estado.copy(
                    notas = correctos.associate { it.first to it.third },
                    cargandoNotas = false,
                    erro = if (fallos > 0) "Non se puideron cargar $fallos nota(s). Comproba a conexión." else null
                )
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    // ---------- Nova proba ----------

    fun abrirNova() {
        estado = estado.copy(mostrarNova = true, erroNova = null)
    }

    fun pecharNova() {
        estado = estado.copy(mostrarNova = false, erroNova = null)
    }

    /** `palabras` xa sen liñas baleiras. As tres porcentaxes deben
     * sumar 100, coma no backend. `data` en formato AAAA-MM-DD ou null. */
    fun crearProba(
        nome: String,
        data: String?,
        palabras: List<String>,
        pctListening: Double,
        pctReading: Double,
        pctSpeaking: Double
    ) {
        val asignacion = estado.asignacionSeleccionada ?: return
        if (nome.isBlank()) {
            estado = estado.copy(erroNova = "Escribe un nome para a proba.")
            return
        }
        if (palabras.isEmpty()) {
            estado = estado.copy(erroNova = "Engade polo menos unha palabra.")
            return
        }
        val suma = pctListening + pctReading + pctSpeaking
        if (abs(suma - 100.0) > 0.01) {
            estado = estado.copy(erroNova = "A ponderación debe sumar 100 (agora suma ${"%.0f".format(suma)}).")
            return
        }
        if (estado.gardandoNova) return

        if (estado.modoFoiraDeLina) {
            crearProbaOffline(asignacion, nome, data, palabras, pctListening, pctReading, pctSpeaking)
            return
        }

        val trimestre = estado.trimestreSeleccionado ?: return
        estado = estado.copy(gardandoNova = true, erroNova = null)
        viewModelScope.launch {
            try {
                val nova = ApiClient.servizo.crearColumna(
                    asignacion.id,
                    ColumnaCreate(
                        trimestre_id = trimestre.id,
                        tipo = "destrezas",
                        nome = nome.trim(),
                        data = data,
                        palabras = palabras,
                        pct_listening = pctListening,
                        pct_reading = pctReading,
                        pct_speaking = pctSpeaking
                    )
                )
                estado = estado.copy(mostrarNova = false, gardandoNova = false)
                recargarColumnas(columnaASeleccionar = nova.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Destrezas", "Erro creando proba", e)
                val mensaxe = if (e is HttpException && e.code() == 403) {
                    "Non tes permiso para crear probas nesta materia."
                } else {
                    "Non se puido crear a proba. Comproba a conexión e proba de novo."
                }
                estado = estado.copy(gardandoNova = false, erroNova = mensaxe)
            }
        }
    }

    private fun crearProbaOffline(
        asignacion: Asignacion, nome: String, data: String?, palabras: List<String>,
        pctListening: Double, pctReading: Double, pctSpeaking: Double
    ) {
        val entrada = entradaOfflineActual ?: return
        val trimestreNome = estado.trimestreSeleccionado?.nome ?: return
        estado = estado.copy(gardandoNova = true, erroNova = null)
        viewModelScope.launch {
            val entradaNova = crearProbaDestrezasOffline(entrada, trimestreNome, nome.trim(), data, palabras, pctListening, pctReading, pctSpeaking)
            gardarLocalPendente(getApplication(), entradaNova)
            entradaOfflineActual = entradaNova
            val novaColumnaId = entradaNova.columnasDestrezas(trimestreNome).lastOrNull { it.nome == nome.trim() }?.let { idColumnaOffline(it) }
            estado = estado.copy(mostrarNova = false, gardandoNova = false, erroNova = null)
            recargarColumnasOffline(asignacion, listOf(entradaNova), novaColumnaId)
        }
    }

    // ---------- Panel de avaliación (bottom sheet) ----------

    fun abrirFolla(alumnoId: Int) {
        val columna = estado.columnaSeleccionada ?: return
        val indice = estado.alumnos.indexOfFirst { it.alumno_id == alumnoId }
        if (indice < 0 || estado.cargandoFolla) return

        if (estado.modoFoiraDeLina) {
            abrirFollaOffline(indice, columna)
            return
        }

        estado = estado.copy(cargandoFolla = true, erroGardar = null)
        viewModelScope.launch {
            try {
                // Sempre se le do servidor: así non se machuca unha
                // avaliación existente ao gardar.
                val existente = ApiClient.servizo.verDestreza(columna.id, alumnoId)
                mostrarFolla(indice, existente.acertos_listening, existente.acertos_reading, existente.acertos_speaking)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Destrezas", "Erro abrindo alumno $alumnoId", e)
                val mensaxe = "Non se puido cargar a avaliación deste alumno."
                estado = if (estado.folla != null) {
                    estado.copy(cargandoFolla = false, erroGardar = mensaxe)
                } else {
                    estado.copy(cargandoFolla = false, erro = mensaxe)
                }
            }
        }
    }

    private fun abrirFollaOffline(indice: Int, columna: ColumnaOut) {
        val entrada = entradaOfflineActual ?: return
        val trimestreNome = estado.trimestreSeleccionado?.nome ?: return
        val alumno = estado.alumnos[indice]
        val (apelidos, nome) = separarNomeCompleto(alumno.nome_completo)
        val (idsListening, idsReading, idsSpeaking) = lerAcertosOffline(entrada, trimestreNome, columna.id, nome, apelidos)
        val verdes = buildSet {
            idsListening.forEach { add(Destreza.LISTENING to it) }
            idsReading.forEach { add(Destreza.READING to it) }
            idsSpeaking.forEach { add(Destreza.SPEAKING to it) }
        }
        estado = estado.copy(
            folla = FollaDestrezas(alumno.alumno_id, alumno.nome_completo, indice + 1, estado.alumnos.size, verdes),
            erroGardar = null
        )
    }

    private fun mostrarFolla(indice: Int, listening: List<Int>, reading: List<Int>, speaking: List<Int>) {
        val alumno = estado.alumnos[indice]
        val verdes = buildSet {
            listening.forEach { add(Destreza.LISTENING to it) }
            reading.forEach { add(Destreza.READING to it) }
            speaking.forEach { add(Destreza.SPEAKING to it) }
        }
        estado = estado.copy(
            cargandoFolla = false,
            folla = FollaDestrezas(
                alumnoId = alumno.alumno_id,
                nome = alumno.nome_completo,
                posicion = indice + 1,
                total = estado.alumnos.size,
                verdes = verdes
            ),
            erroGardar = null
        )
    }

    fun pecharFolla() {
        estado = estado.copy(folla = null, erroGardar = null)
    }

    fun alternarCela(destreza: Destreza, palabraId: Int) {
        val folla = estado.folla ?: return
        val clave = destreza to palabraId
        val novos = if (clave in folla.verdes) folla.verdes - clave else folla.verdes + clave
        estado = estado.copy(folla = folla.copy(verdes = novos))
    }

    /** Nota /10 en directo: media ponderada da fracción de acertos de
     * cada destreza. Null se non hai ningún acerto marcado. */
    fun notaEnDirecto(folla: FollaDestrezas): Double? {
        val columna = estado.columnaSeleccionada ?: return null
        val total = columna.palabras.size
        if (total == 0) return null
        val pl = columna.pct_listening ?: 0.0
        val pr = columna.pct_reading ?: 0.0
        val ps = columna.pct_speaking ?: 0.0
        val totalPct = pl + pr + ps
        if (totalPct <= 0.0 || folla.verdes.isEmpty()) return null
        val nl = folla.verdes.count { it.first == Destreza.LISTENING }
        val nr = folla.verdes.count { it.first == Destreza.READING }
        val ns = folla.verdes.count { it.first == Destreza.SPEAKING }
        val acumulado = (nl.toDouble() / total) * pl + (nr.toDouble() / total) * pr + (ns.toDouble() / total) * ps
        return minOf(acumulado / totalPct, 1.0) * 10.0
    }

    fun gardar(paraSeguinte: Boolean) {
        val folla = estado.folla ?: return
        if (estado.gardando) return

        if (estado.modoFoiraDeLina) {
            gardarOffline(folla, paraSeguinte)
            return
        }

        val columnaId = estado.columnaSeleccionadaId ?: return
        estado = estado.copy(gardando = true, erroGardar = null)
        viewModelScope.launch {
            try {
                val resposta = ApiClient.servizo.gardarDestrezaPalabras(
                    DestrezaPalabrasUpdate(
                        columna_id = columnaId,
                        alumno_id = folla.alumnoId,
                        acertos_listening = folla.verdes.filter { it.first == Destreza.LISTENING }.map { it.second },
                        acertos_reading = folla.verdes.filter { it.first == Destreza.READING }.map { it.second },
                        acertos_speaking = folla.verdes.filter { it.first == Destreza.SPEAKING }.map { it.second }
                    )
                )
                estado = estado.copy(
                    gardando = false,
                    notas = estado.notas + (folla.alumnoId to resposta.valor)
                )
                // `posicion` empeza en 1, así que coincide co índice do seguinte.
                val seguinte = estado.alumnos.getOrNull(folla.posicion)
                if (paraSeguinte && seguinte != null) abrirFolla(seguinte.alumno_id) else pecharFolla()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Destrezas", "Erro gardando avaliación", e)
                val mensaxe = if (e is HttpException && e.code() == 403) {
                    "Non tes permiso para avaliar nesta materia."
                } else {
                    "Non se puido gardar. Comproba a conexión e proba de novo."
                }
                estado = estado.copy(gardando = false, erroGardar = mensaxe)
            }
        }
    }

    private fun gardarOffline(folla: FollaDestrezas, paraSeguinte: Boolean) {
        val entrada = entradaOfflineActual ?: return
        val trimestreNome = estado.trimestreSeleccionado?.nome ?: return
        val columna = estado.columnaSeleccionada ?: return
        val (apelidos, nome) = separarNomeCompleto(folla.nome)

        estado = estado.copy(gardando = true, erroGardar = null)
        viewModelScope.launch {
            val (entradaNova, valor) = gardarDestrezaOffline(
                entrada = entrada, trimestreNome = trimestreNome, columnaId = columna.id,
                alumnoNome = nome, alumnoApelidos = apelidos,
                idsListening = folla.verdes.filter { it.first == Destreza.LISTENING }.map { it.second }.toSet(),
                idsReading = folla.verdes.filter { it.first == Destreza.READING }.map { it.second }.toSet(),
                idsSpeaking = folla.verdes.filter { it.first == Destreza.SPEAKING }.map { it.second }.toSet()
            )
            gardarLocalPendente(getApplication(), entradaNova)
            entradaOfflineActual = entradaNova
            estado = estado.copy(
                gardando = false,
                notas = estado.notas + (folla.alumnoId to valor),
                erroGardar = "Gardado no móbil ás ${horaActualCurta()} — pendente de sincronizar"
            )
            val seguinte = estado.alumnos.getOrNull(folla.posicion)
            if (paraSeguinte && seguinte != null) abrirFolla(seguinte.alumno_id) else pecharFolla()
        }
    }

    /** "Apelidos, Nome" -> (apelidos, nome), igual có que compón
     * construirAlumnosOffline(). */
    private fun separarNomeCompleto(nomeCompleto: String): Pair<String, String> {
        val partes = nomeCompleto.split(", ", limit = 2)
        return if (partes.size == 2) partes[0] to partes[1] else nomeCompleto to ""
    }
}
