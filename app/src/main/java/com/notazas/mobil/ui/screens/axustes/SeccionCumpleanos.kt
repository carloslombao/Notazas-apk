package com.notazas.mobil.ui.screens.axustes

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notazas.mobil.datos.cumpleanos.AlmacenCumpleanos
import com.notazas.mobil.datos.cumpleanos.AxustesCumpleanos
import com.notazas.mobil.datos.cumpleanos.ConfigCumpleanos
import com.notazas.mobil.datos.cumpleanos.ModoAviso
import com.notazas.mobil.datos.cumpleanos.NotificacionsCumpleanos
import com.notazas.mobil.datos.cumpleanos.PlanificadorCumpleanos
import kotlinx.coroutines.launch

/** Axustes > avisos de aniversario do alumnado: activar, mesmo día ou día anterior, e hora. */
@Composable
fun SeccionCumpleanos() {
    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()
    var config by remember { mutableStateOf(AxustesCumpleanos.ler(contexto)) }
    var alumnosConData by remember { mutableStateOf<Int?>(null) }
    var mensaxe by remember { mutableStateOf<String?>(null) }

    /** Garda a configuración e (re)programa ou cancela a alarma diaria. */
    fun aplicar(nova: ConfigCumpleanos) {
        config = nova
        AxustesCumpleanos.gardar(contexto, nova)
        PlanificadorCumpleanos.reprogramar(contexto)
    }

    /** Recalcula a lista a partir dos snapshots de BoxAbalar gardados no móbil (sen servidor). */
    suspend fun refrescarDatas() {
        alumnosConData = AlmacenCumpleanos.actualizarDesdeCache(contexto)
        mensaxe = if (alumnosConData == 0) {
            "Non hai datas de nacemento nos datos do móbil: preme Sincronizar. Se segue igual, abre Notazas no ordenador e sincroniza alí (sube as datas a BoxAbalar)."
        } else null
    }

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (!concedido) {
            mensaxe = "Sen o permiso de notificacións o móbil non mostrará os avisos. Podes darllo nos axustes do sistema."
        }
    }

    LaunchedEffect(config.activado) {
        if (config.activado) refrescarDatas()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Avisos de aniversario", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "Recibe unha notificación cando un alumno dos teus grupos cumpre anos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = config.activado,
                    onCheckedChange = { activar ->
                        if (activar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificacionsCumpleanos.podeNotificar(contexto)) {
                            pedirPermiso.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        aplicar(config.copy(activado = activar))
                        if (!activar) mensaxe = null
                    }
                )
            }

            if (config.activado) {
                Spacer(Modifier.height(16.dp))
                Text("Cando avisar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FichaModo("O mesmo día", config.modo == ModoAviso.MESMO_DIA) { aplicar(config.copy(modo = ModoAviso.MESMO_DIA)) }
                    FichaModo("O día anterior", config.modo == ModoAviso.DIA_ANTERIOR) { aplicar(config.copy(modo = ModoAviso.DIA_ANTERIOR)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Exemplo: «Daniel Martínez González (4ºA) cumpre 8 anos ${if (config.modo == ModoAviso.MESMO_DIA) "hoxe" else "mañá"}». " +
                        "Se o aviso cae en sábado ou domingo, adiántase ao venres anterior («…cumpre 8 anos o sábado»).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                Text("Hora do aviso", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    TimePickerDialog(
                        contexto,
                        { _, hora, minuto -> aplicar(config.copy(hora = hora, minuto = minuto)) },
                        config.hora, config.minuto, true
                    ).show()
                }) {
                    Text(String.format("%02d:%02d", config.hora, config.minuto))
                }

                Spacer(Modifier.height(8.dp))
                alumnosConData?.let {
                    Text(
                        if (it == 1) "1 alumno con data de nacemento." else "$it alumnos con data de nacemento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "As datas veñen de BoxAbalar (cifradas), gárdanse cifradas neste móbil e bórranse ao pechar sesión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Android 12+ pode denegar as alarmas exactas por defecto: sen elas o aviso
                // pode chegar uns minutos tarde. Ofrécese o atallo ao axuste do sistema.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !podeAlarmasExactas(contexto)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Para que chegue á hora exacta (e non uns minutos tarde), permite «Alarmas e recordatorios» a Notazas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        contexto.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${contexto.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Abrir axuste") }
                }

                TextButton(onClick = {
                    if (NotificacionsCumpleanos.podeNotificar(contexto)) {
                        NotificacionsCumpleanos.mostrarDeProba(contexto)
                        mensaxe = null
                    } else {
                        mensaxe = "As notificacións están desactivadas para Notazas no sistema."
                    }
                }) { Text("Enviar unha de proba") }
            }

            mensaxe?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun podeAlarmasExactas(contexto: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (contexto.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    } else true

@Composable
private fun FichaModo(texto: String, seleccionada: Boolean, onClick: () -> Unit) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = if (seleccionada) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
