package com.notazas.mobil.datos.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Reduce unha foto da galería antes de subila: lado maior <= [ladoMax]
 * px, orientación EXIF aplicada (o servidor non a le) e JPEG a
 * [calidade]. As fotos de móbil (8-12 MP) tardan moito por wifi e o QR
 * lese igual con ~2500 px. Devolve un ficheiro temporal na caché que
 * quen o chama debe borrar tras a subida.
 */
suspend fun reducirImaxe(
    contexto: Context,
    uri: Uri,
    ladoMax: Int = 2500,
    calidade: Int = 85
): File = withContext(Dispatchers.IO) {
    val resolver = contexto.contentResolver

    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
    val maior = maxOf(limites.outWidth, limites.outHeight)
    if (maior <= 0) throw IOException("Non se puido ler a imaxe")

    // inSampleSize (potencia de 2) evita cargar a foto enteira en memoria.
    var mostra = 1
    while (maior / (mostra * 2) >= ladoMax) mostra *= 2

    val orixinal = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = mostra })
    } ?: throw IOException("Non se puido abrir a imaxe")

    // Se non hai EXIF legible (PNG, WebP...) asúmese orientación normal.
    val orientacion = runCatching {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val graos = when (orientacion) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    val escala = minOf(1f, ladoMax.toFloat() / maxOf(orixinal.width, orixinal.height))
    val matriz = Matrix().apply {
        if (graos != 0f) postRotate(graos)
        if (escala < 1f) postScale(escala, escala)
    }
    val transformado = if (matriz.isIdentity) {
        orixinal
    } else {
        Bitmap.createBitmap(orixinal, 0, 0, orixinal.width, orixinal.height, matriz, true)
    }

    val cartafol = File(contexto.cacheDir, "traballos").apply { mkdirs() }
    val destino = File.createTempFile("foto_", ".jpg", cartafol)
    try {
        FileOutputStream(destino).use { transformado.compress(Bitmap.CompressFormat.JPEG, calidade, it) }
    } finally {
        if (transformado !== orixinal) transformado.recycle()
        orixinal.recycle()
    }
    destino
}
