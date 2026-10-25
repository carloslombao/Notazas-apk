package com.notazas.mobil.datos.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException

/** Garda o PDF dun exame pendente na caché e devolve un Intent para
 * abrilo cun visor externo (a app non ten visor de PDF propio). Usa
 * FileProvider (ver AndroidManifest.xml e res/xml/file_paths.xml),
 * así que o ficheiro ten que vivir dentro de cacheDir/pdfs/. */
suspend fun gardarEAbrirPdf(contexto: Context, corpo: ResponseBody, nomeFicheiro: String): Intent =
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

        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", destino)
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
