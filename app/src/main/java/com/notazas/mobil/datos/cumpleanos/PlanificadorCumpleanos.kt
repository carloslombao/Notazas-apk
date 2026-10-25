package com.notazas.mobil.datos.cumpleanos

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.notazas.mobil.MainActivity
import com.notazas.mobil.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private const val TAG = "Cumpleanos"

/**
 * Programa UNHA alarma ao día á hora que elixiu o profesor. Cando salta,
 * CumpleanosReceiver mira que alumnos cumpren e lanza unha notificación
 * por alumno, e volve programar a seguinte. Úsase alarma exacta se o
 * sistema o permite (Android 12+ pode negala) e, se non, unha
 * "aproximada" que pode chegar uns minutos tarde en modo aforro.
 */
object PlanificadorCumpleanos {
    const val ACCION_AVISO = "com.notazas.mobil.CUMPLEANOS_AVISO"
    private const val CODIGO_PETICION = 4711

    /** Seguinte momento no que hai que avisar: hoxe á hora elixida se aínda non pasou, senón mañá. */
    fun proximoDisparo(config: ConfigCumpleanos, agora: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val hoxeAHora = agora.toLocalDate().atTime(config.hora, config.minuto)
        return if (hoxeAHora.isAfter(agora)) hoxeAHora else hoxeAHora.plusDays(1)
    }

    private fun intentDaAlarma(contexto: Context): PendingIntent {
        val intent = Intent(contexto, CumpleanosReceiver::class.java).setAction(ACCION_AVISO)
        return PendingIntent.getBroadcast(
            contexto, CODIGO_PETICION, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** (Re)programa ou cancela a alarma segundo a configuración gardada. */
    fun reprogramar(contexto: Context) {
        val app = contexto.applicationContext
        val config = AxustesCumpleanos.ler(app)
        val alarmas = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendente = intentDaAlarma(app)
        alarmas.cancel(pendente)
        if (!config.activado) return

        val momento = proximoDisparo(config).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmas.canScheduleExactAlarms()) {
                alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pendente)
            } else {
                alarmas.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pendente)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Sen permiso para alarmas exactas; uso unha aproximada", e)
            alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pendente)
        }
        Log.i(TAG, "Seguinte aviso de aniversarios programado: ${proximoDisparo(config)}")
    }
}

/** Recibe a alarma diaria e os eventos do sistema que a fan perder (reinicio, cambio de hora...). */
class CumpleanosReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val pendente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == PlanificadorCumpleanos.ACCION_AVISO) {
                    lanzarAvisosDeHoxe(contexto.applicationContext)
                }
                // En calquera caso (alarma, reinicio, actualización da app, cambio de hora ou
                // de fuso) déixase programada a seguinte.
                PlanificadorCumpleanos.reprogramar(contexto.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Erro lanzando os avisos de aniversario", e)
            } finally {
                pendente.finish()
            }
        }
    }

    private suspend fun lanzarAvisosDeHoxe(contexto: Context) {
        val config = AxustesCumpleanos.ler(contexto)
        if (!config.activado) return
        val hoxe = LocalDate.now()
        val avisos = avisosDoDia(hoxe, AlmacenCumpleanos.cargar(contexto), config.modo)
        Log.i(TAG, "Avisos de aniversario de hoxe ($hoxe): ${avisos.size}")
        avisos.forEach { NotificacionsCumpleanos.mostrar(contexto, it, hoxe) }
    }
}

object NotificacionsCumpleanos {
    private const val CANAL = "cumpleanos"

    private fun crearCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(CANAL, "Aniversarios do alumnado", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisos cando un alumno cumpre anos"
        }
        (contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(canal)
    }

    /** true se o sistema deixa mostrar notificacións (en Android 13+ hai que ter o permiso concedido). */
    fun podeNotificar(contexto: Context): Boolean = NotificationManagerCompat.from(contexto).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // comprobado con areNotificationsEnabled()
    fun mostrar(contexto: Context, aviso: AvisoCumpleanos, dia: LocalDate) {
        if (!podeNotificar(contexto)) {
            Log.w(TAG, "Notificacións desactivadas no sistema: non se mostra o aviso")
            return
        }
        crearCanal(contexto)
        val abrirApp = PendingIntent.getActivity(
            contexto, 0,
            Intent(contexto, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_cumpleanos)
            .setContentTitle(aviso.titulo)
            .setContentText(aviso.texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(aviso.texto))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .build()
        // Id estable por alumno e día: se salta dúas veces, substitúese en vez de duplicarse.
        NotificationManagerCompat.from(contexto).notify("cumpleanos_${dia}_${aviso.alumnoId}".hashCode(), notificacion)
    }

    /** Notificación de exemplo co formato real, para comprobar permisos e aspecto. */
    fun mostrarDeProba(contexto: Context) {
        val hoxe = LocalDate.now()
        mostrar(
            contexto,
            AvisoCumpleanos(
                alumnoId = -1, titulo = "🎂 Aniversario",
                texto = "Daniel Martínez González (4ºA) cumpre 8 anos hoxe", cumpleanos = hoxe
            ),
            hoxe
        )
    }
}
