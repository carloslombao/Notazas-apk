package com.notazas.mobil.datos.rede

import com.notazas.mobil.datos.modelos.Asignacion
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.ActitudeSemanalIn
import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AsignacionManualExameIn
import com.notazas.mobil.datos.modelos.AsignacionManualIn
import com.notazas.mobil.datos.modelos.CanxearQrIn
import com.notazas.mobil.datos.modelos.CanxearQrOut
import com.notazas.mobil.datos.modelos.CargaTraballosOut
import com.notazas.mobil.datos.modelos.ColumnaCreate
import com.notazas.mobil.datos.modelos.ColumnaOut
import com.notazas.mobil.datos.modelos.DestrezaOut
import com.notazas.mobil.datos.modelos.DestrezaPalabrasUpdate
import com.notazas.mobil.datos.modelos.EscaneoExameOut
import com.notazas.mobil.datos.modelos.FicheiroColumnaOut
import com.notazas.mobil.datos.modelos.SincronizacionServidorOut
import com.notazas.mobil.datos.modelos.FicheiroSubidoOut
import com.notazas.mobil.datos.modelos.GradebookTrimestreOut
import com.notazas.mobil.datos.modelos.LoginRequest
import com.notazas.mobil.datos.modelos.MateriaDetalleTrimestreOut
import com.notazas.mobil.datos.modelos.NotaOut
import com.notazas.mobil.datos.modelos.NotaSimpleOut
import com.notazas.mobil.datos.modelos.NotaUpdateIn
import com.notazas.mobil.datos.modelos.PerfilAlumnoOut
import com.notazas.mobil.datos.modelos.ResultadoSegmentoExame
import com.notazas.mobil.datos.modelos.TokenResponse
import com.notazas.mobil.datos.modelos.Trimestre
import com.notazas.mobil.datos.modelos.UsuarioSelector
import com.notazas.mobil.datos.offline.CacheAsignacionEntrada
import com.notazas.mobil.datos.offline.CacheCentroResposta
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming

// Reflicte os endpoints existentes do backend (auth.py, gradebook.py,
// trimestres.py). Engadiremos aquí o resto (alumnos, actitude,
// exames, traballos...) nas seguintes fases.
interface NotazasApiService {

    @GET("auth/usuarios")
    suspend fun listarUsuarios(): List<UsuarioSelector>

    // Sen autenticación: o código do QR (xerado dende o perfil na web)
    // é a credencial. Ver aprovisionamento_qr_movil.md.
    @POST("nextcloud/centro/qr-aprovisionamento/canxear")
    suspend fun canxearQrAprovisionamento(@Body datos: CanxearQrIn): CanxearQrOut

    @POST("auth/login")
    suspend fun login(@Body datos: LoginRequest): TokenResponse

    @GET("gradebook/asignacions")
    suspend fun listarAsignacions(): List<Asignacion>

    @GET("trimestres")
    suspend fun listarTrimestres(): List<Trimestre>

    @GET("gradebook/{asignacionId}/{trimestreId}")
    suspend fun verGradebookTrimestre(
        @Path("asignacionId") asignacionId: Int,
        @Path("trimestreId") trimestreId: Int
    ): GradebookTrimestreOut

    @GET("alumnos/{alumnoId}/perfil")
    suspend fun verPerfilAlumno(@Path("alumnoId") alumnoId: Int): PerfilAlumnoOut

    // Desglose columna-a-columna (exames/traballos/destrezas) dun
    // trimestre, en TODAS as materias do grupo dese alumno (mesmo
    // criterio "ve todo" ca web); cada materia marca `es_mina` para
    // saber se o profesor actual pode editala.
    @GET("alumnos/{alumnoId}/notas-detalle/{trimestreId}")
    suspend fun verNotasDetalleTrimestre(
        @Path("alumnoId") alumnoId: Int,
        @Path("trimestreId") trimestreId: Int
    ): List<MateriaDetalleTrimestreOut>

    @GET("actitude/aspectos")
    suspend fun listarAspectosActitude(): List<AspectoActitudeOut>

    @GET("actitude/evolucion/{alumnoId}")
    suspend fun verEvolucionActitude(@Path("alumnoId") alumnoId: Int): List<ActitudeSemanalOut>

    // O backend devolve `null` (200) se ese alumno non ten avaliación
    // nesa semana. O converter non decodifica "null" a un tipo non
    // anulable, así que recibimos JsonElement e interpretámolo nós
    // (ver ActitudeViewModel).
    @GET("actitude/semanal-por-codigo/{asignacionId}/{alumnoId}/{codigoSemana}")
    suspend fun verSemanaConcreta(
        @Path("asignacionId") asignacionId: Int,
        @Path("alumnoId") alumnoId: Int,
        @Path("codigoSemana") codigoSemana: Int
    ): JsonElement

    @POST("actitude/semanal")
    suspend fun gardarActitudeSemanal(@Body datos: ActitudeSemanalIn): ActitudeSemanalOut

    // Todas as semanas avaliadas dun alumno nun trimestre (para o
    // gráfico e o desprazable de medias do Caderno).
    @GET("actitude/semanal/{asignacionId}/{alumnoId}/{trimestreId}")
    suspend fun verHistoricoActitude(
        @Path("asignacionId") asignacionId: Int,
        @Path("alumnoId") alumnoId: Int,
        @Path("trimestreId") trimestreId: Int
    ): List<ActitudeSemanalOut>

    // Ficheiros (fotos de traballos e PDFs de exames) dun alumno.
    @GET("traballos/alumno/{alumnoId}")
    suspend fun listarFicheirosAlumno(@Path("alumnoId") alumnoId: Int): List<FicheiroColumnaOut>

    // Ficheiro orixinal completo. Valida o token por query param (non
    // por cabeceira): ver ApiClient.descargarFicheiro().
    @Streaming
    @GET("traballos/ficheiro/{ficheiroId}/ficheiro")
    suspend fun verFicheiroCompleto(
        @Path("ficheiroId") ficheiroId: Int,
        @Query("t") token: String
    ): ResponseBody

    // Botón "Sincronizar" (cando se chega ao servidor): fusiona BoxAbalar coa
    // base de datos do servidor, nota a nota, para TODAS as asignacións do usuario.
    @POST("nextcloud/sync")
    suspend fun sincronizarServidor(): SincronizacionServidorOut

    @POST("gradebook/{asignacionId}/columnas")
    suspend fun crearColumna(
        @Path("asignacionId") asignacionId: Int,
        @Body datos: ColumnaCreate
    ): ColumnaOut

    // Lote de fotos para UN traballo (columna_id). O servidor le o QR de
    // cada foto: as recoñecidas quedan no perfil do alumno e as demais
    // como "pendentes" ata asignalas a man (asignarManual).
    @Multipart
    @POST("traballos/cargar")
    suspend fun cargarTraballos(
        @Part ficheiros: List<MultipartBody.Part>,
        @Part("columna_id") columnaId: RequestBody
    ): CargaTraballosOut

    @POST("traballos/asignar-manual")
    suspend fun asignarManual(@Body datos: AsignacionManualIn): FicheiroSubidoOut

    @GET("gradebook/notas/destrezas/{columnaId}/{alumnoId}")
    suspend fun verDestreza(
        @Path("columnaId") columnaId: Int,
        @Path("alumnoId") alumnoId: Int
    ): DestrezaOut

    @PUT("gradebook/notas/destrezas/palabras")
    suspend fun gardarDestrezaPalabras(@Body datos: DestrezaPalabrasUpdate): DestrezaOut

    // ---------- Exames escaneados (incl. recuperacións) ----------

    // Sube o PDF único cos exames xa corrixidos e escaneados de todo o
    // grupo. Timeout longo (ver ApiClient): fai OCR+lectura de QR no
    // servidor, igual ca traballos/cargar.
    @Multipart
    @POST("exames/{columnaId}/escanear")
    suspend fun escanearExames(
        @Path("columnaId") columnaId: Int,
        @Part pdf: MultipartBody.Part
    ): EscaneoExameOut

    @POST("exames/{columnaId}/asignar-manual")
    suspend fun asignarManualExame(
        @Path("columnaId") columnaId: Int,
        @Body datos: AsignacionManualExameIn
    ): ResultadoSegmentoExame

    @Streaming
    @GET("exames/pendente/{pendenteId}/pdf")
    suspend fun verExamePendentePdf(@Path("pendenteId") pendenteId: String): ResponseBody

    @GET("gradebook/notas/{columnaId}/{alumnoId}")
    suspend fun verNotaSimple(
        @Path("columnaId") columnaId: Int,
        @Path("alumnoId") alumnoId: Int
    ): NotaSimpleOut

    @PUT("gradebook/notas")
    suspend fun gardarNota(@Body datos: NotaUpdateIn): NotaOut

    // ---------- Modo "fóra do colexio": caché inicial para offline ----------
    // Só se chaman estando en liña (coa API normal), para gardar en local
    // o necesario para seguir traballando sen servidor despois. Ver
    // datos/offline/.

    @GET("nextcloud/cache/asignacions")
    suspend fun verCacheAsignacions(): List<CacheAsignacionEntrada>

    @GET("nextcloud/cache/centro")
    suspend fun verCacheCentro(): CacheCentroResposta
}
