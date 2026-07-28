package es.guiamayores.cuidame

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * CONTESTAR A "¿DONDE ESTA?"
 * ==========================
 *
 * Se enciende cuando llega el mensaje del contacto, busca una posicion
 * NUEVA, contesta y se apaga. No deja nada corriendo.
 *
 * POR QUE SE BUSCA UNA POSICION NUEVA Y NO LA ULTIMA CONOCIDA
 *
 * En el aviso de caida usamos la ultima conocida a proposito, porque
 * mandarla YA vale mas que mandarla exacta un minuto despues. Aqui es al
 * reves y por un motivo claro: si alguien pregunta donde esta su madre es
 * porque no lo sabe, y la ultima posicion guardada puede ser de cuando
 * salio de casa hace tres horas. Contestar eso no es un dato incompleto,
 * es mandar a alguien al sitio equivocado.
 *
 * Se espera hasta 50 segundos a que el GPS enganche. Si no lo consigue, se
 * contesta con lo ultimo que hubiera PERO DICIENDO DE CUANDO ES. Una
 * posicion vieja etiquetada como vieja sigue sirviendo; sin etiquetar,
 * engaña.
 */
class ServicioConsulta : Service(), LocationListener {

    private val reloj = Handler(Looper.getMainLooper())
    private var lm: LocationManager? = null
    private var yaContestado = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Android exige aviso visible para usar el GPS desde segundo
        // plano, y aqui viene de perlas: la persona ve en su pantalla que
        // le estan preguntando donde esta. Esto no debe poder hacerse a
        // escondidas.
        startForeground(ID, avisoTrabajando())
        buscarPosicion()
        // Red de seguridad: pase lo que pase, en 50 segundos se contesta
        // con lo que haya y se cierra. Un servicio que se queda colgado
        // esperando un GPS que no engancha se come la bateria del movil
        // de la persona a la que intentamos cuidar.
        reloj.postDelayed({ contestar(null) }, 50_000L)
    }

    @SuppressLint("MissingPermission")
    private fun buscarPosicion() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) { contestar(null); return }
        try {
            val m = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
                try { m.requestLocationUpdates(p, 1000L, 0f, this) } catch (e: Exception) {}
            }
            lm = m
        } catch (e: Exception) { contestar(null) }
    }

    override fun onLocationChanged(sitio: Location) {
        // Se acepta la primera posicion decente. Esperar a la perfecta es
        // otra forma de llegar tarde.
        if (!sitio.hasAccuracy() || sitio.accuracy < 100f) contestar(sitio)
    }

    private fun contestar(sitio: Location?) {
        if (yaContestado) return
        yaContestado = true
        reloj.removeCallbacksAndMessages(null)
        try { lm?.removeUpdates(this) } catch (e: Exception) {}

        val texto = Avisador.mensajeConsulta(this, sitio)
        val fallo = Avisador.enviar(this, texto)

        val aQuien = Ajustes(this).nombreContacto.ifBlank { "su contacto" }

        // QUEDA APUNTADO SIEMPRE, HAYA SALIDO BIEN O MAL.
        //
        // Este es el candado que hace que esta funcion sea aceptable. Una
        // app capaz de decir donde esta alguien sin que se entere solo
        // puede existir si queda constancia en el propio movil de cada vez
        // que lo hace, con la hora y a quien.
        Historial.añadir(
            this, "Consulta de ubicación",
            if (fallo == null) "respondida a $aQuien" else "no se pudo responder",
            if (sitio != null) "posición nueva" else "sin posición nueva"
        )
        avisarEnPantalla(aQuien, fallo == null)

        stopForeground(true)
        stopSelf()
    }

    private fun avisoTrabajando() =
        NotificationCompat.Builder(this, ServicioVigilancia.CANAL)
            .setContentTitle("Cuídame")
            .setContentText("Su contacto ha preguntado dónde está usted")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun avisarEnPantalla(aQuien: String, bien: Boolean) {
        try {
            val abrir = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(this, ServicioVigilancia.CANAL)
                .setContentTitle(if (bien) "Se ha enviado su ubicación" else "No se pudo enviar su ubicación")
                .setContentText("$aQuien preguntó dónde estaba usted.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setAutoCancel(true)
                .setContentIntent(abrir)
                .build()
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID + 1, n)
        } catch (e: Exception) {}
    }

    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
    @Deprecated("Obligatorio en Android 7 y 8")
    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}

    companion object { const val ID = 77 }
}
