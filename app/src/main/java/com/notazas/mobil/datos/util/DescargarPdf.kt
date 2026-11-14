package com.notazas.mobil.datos.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException

/** Garda o PDF descargado na caché (cacheDir/pdfs/) e devolve o ficheiro. Úsao o visor de PDF
 * da app (ui/componentes/VisorPdf.kt). O cartafol é o que declara res/xml/file_paths.xml, así que
 * o mesmo ficheiro pode compartirse despois cun visor externo con [intentAbrirPdf]. */
suspend fun descargarPdfACache(contexto: Context, corpo: ResponseBody, nomeFicheiro: String): File =
    withContext(Dispatchers.IO) {
        val cartafol = File(contexto.cacheDir, "pdfs").apply { mkdirs() }
        val destino = File(cartafol, nomeFicheiro)
        try {
            corpo.byteStream().use { entrada ->
                destino.outputStream().use { saida -> entrada.copyTo(saida) }
            }
        } catch (e: IOException) {
            throw IOException("Non se puido gardar o PDF temporalmente", e)
        }
        destino
    }

/** Intent para abrir un PDF da caché cun visor EXTERNO (a través do FileProvider). */
fun intentAbrirPdf(contexto: Context, ficheiro: File): Intent {
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", ficheiro)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Garda o PDF dun exame pendente na caché e devolve un Intent para
 * abrilo cun visor externo. Usa FileProvider (ver AndroidManifest.xml e
 * res/xml/file_paths.xml), así que o ficheiro ten que vivir dentro de
 * cacheDir/pdfs/. */
suspend fun gardarEAbrirPdf(contexto: Context, corpo: ResponseBody, nomeFicheiro: String): Intent =
    intentAbrirPdf(contexto, descargarPdfACache(contexto, corpo, nomeFicheiro))
