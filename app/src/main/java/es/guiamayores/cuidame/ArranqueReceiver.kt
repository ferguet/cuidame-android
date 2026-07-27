package es.guiamayores.cuidame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Vuelve a vigilar sola despues de reiniciar el movil.
 *
 * Sin esto pasaria lo peor que le puede pasar a una app de seguridad:
 * que la persona crea que esta protegida cuando no lo esta. Los moviles
 * se reinician solos con las actualizaciones, y nadie se acuerda de
 * volver a abrir una app que se supone que va sola.
 */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val ajustes = Ajustes(contexto)
        if (ajustes.vigilanciaActiva && ajustes.estaConfigurada()) {
            ServicioVigilancia.arrancar(contexto)
        }
    }
}
