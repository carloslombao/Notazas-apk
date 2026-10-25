package com.notazas.mobil.ui.screens.login

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.notazas.mobil.datos.modelos.UsuarioSelector
import java.util.concurrent.Executors

/**
 * Escaneo do QR de aprovisionamento (ver aprovisionamento_qr_movil.md):
 * cámara en vivo que, ao detectar un código QR, extrae "ip:porto:codigo"
 * e canxéao por unha sesión.
 */
@Composable
fun EscanearQrScreen(
    onSesionIniciada: (token: String, usuario: UsuarioSelector) -> Unit,
    onCancelar: () -> Unit,
    viewModel: EscanearQrViewModel = viewModel()
) {
    val estado = viewModel.estado
    val contexto = LocalContext.current

    LaunchedEffect(estado.sesionIniciada) {
        estado.sesionIniciada?.let { (token, usuario) ->
            onSesionIniciada(token, usuario)
            viewModel.sesionConsumida()
        }
    }

    var permisoConcedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permisoConcedido = concedido
    }
    LaunchedEffect(Unit) {
        if (!permisoConcedido) pedirPermiso.launch(Manifest.permission.CAMERA)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Escanear QR de acceso",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Xéraeo dende o teu perfil na web, estando conectado á wifi do colexio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.padding(top = 20.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            when {
                !permisoConcedido -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(top = 8.dp))
                    Text(
                        "Precísase permiso de cámara",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { pedirPermiso.launch(Manifest.permission.CAMERA) }) {
                        Text("Conceder permiso")
                    }
                }

                else -> VistaCamara(
                    activa = !estado.procesando,
                    onQrDetectado = viewModel::procesarTextoQr
                )
            }

            if (estado.procesando) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.padding(top = 8.dp))
                        Text("Conectando…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        estado.erro?.let {
            Spacer(Modifier.padding(top = 16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = viewModel::limparErro) { Text("Volver intentar") }
        }

        Spacer(Modifier.padding(top = 16.dp))
        Button(onClick = onCancelar) { Text("Cancelar") }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun VistaCamara(activa: Boolean, onQrDetectado: (String) -> Unit) {
    val contexto = LocalContext.current
    val cicloVida = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val escaner = remember { BarcodeScanning.getClient() }
    // `activa` cambia dentro dun callback de fondo (análise de imaxe);
    // rememberUpdatedState evita capturar un valor "activa" caducado.
    val activaEstado = rememberUpdatedState(activa)
    val onQrDetectadoEstado = rememberUpdatedState(onQrDetectado)

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            escaner.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analise = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analisis ->
                        analisis.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null || !activaEstado.value) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val imaxe = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            escaner.process(imaxe)
                                .addOnSuccessListener { codigos: List<Barcode> ->
                                    codigos.firstOrNull { it.rawValue != null }?.rawValue?.let {
                                        onQrDetectadoEstado.value(it)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(cicloVida, CameraSelector.DEFAULT_BACK_CAMERA, preview, analise)
                } catch (e: Exception) {
                    // Cámara non dispoñible (pouco probable nun móbil real);
                    // a pantalla queda sen vista previa, pero non peta.
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}
