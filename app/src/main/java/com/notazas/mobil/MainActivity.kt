package com.notazas.mobil

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.notazas.mobil.datos.local.CredenciaisRemotas
import com.notazas.mobil.datos.local.SesionLocal
import com.notazas.mobil.datos.cumpleanos.AlmacenCumpleanos
import com.notazas.mobil.datos.cumpleanos.AxustesCumpleanos
import com.notazas.mobil.datos.cumpleanos.PlanificadorCumpleanos
import com.notazas.mobil.datos.offline.actualizarCacheOffline
import com.notazas.mobil.datos.offline.sincronizarPendentes
import com.notazas.mobil.ui.navigation.NotazasApp
import com.notazas.mobil.ui.theme.NotazasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// FragmentActivity (non ComponentActivity) porque BiometricPrompt
// precisa dunha FragmentActivity para mostrar o diálogo do sistema.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SesionLocal.iniciar(applicationContext)
        CredenciaisRemotas.iniciar(applicationContext)
        // Se quedara algo gardado sen subir dunha vez anterior (a app
        // pechouse, petou, ou non había conexión), téntase subir xa ao
        // abrir a app. Mellor esforzo: se non hai conexión agora tampouco,
        // simplemente queda pendente para a seguinte vez.
        // Dispatchers.Default: a sincronización e o refresco da caché (cifrado, JSON, rede) NON
        // deben correr no fío principal: bloquearíano os primeiros segundos da app.
        lifecycleScope.launch(Dispatchers.Default) {
            // Avisos de aniversario: asegura a alarma diaria (por se o sistema a perdeu).
            try {
                PlanificadorCumpleanos.reprogramar(applicationContext)
            } catch (e: Exception) {
                // ignorado a propósito
            }
            try {
                sincronizarPendentes(applicationContext)
            } catch (e: Exception) {
                // ignorado a propósito — náda que facer se non hai conexión
            }
            // Mantén fresca a caché do modo "fóra do colexio" (alumnado,
            // probas...) cada vez que se abre a app con conexión, sen que
            // o profesor teña que facer nada — non hai xa botón "Preparar
            // agora" en Axustes.
            try {
                actualizarCacheOffline(applicationContext)
            } catch (e: Exception) {
                // ignorado a propósito
            }
            // Datas de aniversario: saen dos snapshots xa gardados no móbil (BoxAbalar), así que
            // non dependen do servidor. Faise ao final, coa caché xa refrescada.
            try {
                if (AxustesCumpleanos.ler(applicationContext).activado) {
                    AlmacenCumpleanos.actualizarDesdeCache(applicationContext)
                }
            } catch (e: Exception) {
                // ignorado a propósito
            }
        }
        enableEdgeToEdge()
        setContent {
            NotazasTheme {
                NotazasApp()
            }
        }
    }
}
