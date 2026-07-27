package es.guiamayores.cuidame

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MANDAR EL AVISO
 * ===============
 *
 * Por SMS y no por WhatsApp, y esto no es un capricho tecnico: WhatsApp
 * no deja que una app envie sola, siempre tiene que haber alguien que
 * pulse "enviar". Para una persona inconsciente eso no vale de nada. El
 * SMS sale solo, no necesita internet -basta con cobertura- y llega
 * aunque el que lo recibe tenga el movil sin datos.
 *
 * QUE LLEVA EL MENSAJE
 *
 * Lo que hace falta para actuar y nada mas: quien, que ha pasado, a que
 * hora y donde. La ubicacion va como enlace de mapa, que se abre de un
 * toque en cualquier movil.
 */
object Avisador {

    /** Devuelve null si todo fue bien, o el motivo del fallo. */
    fun enviar(contexto: Context, mensaje: String): String? {
        val ajustes = Ajustes(contexto)
        val numero = ajustes.telefonoContacto.trim()

        if (numero.length < 9) return "No hay un teléfono de contacto guardado."

        if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Falta el permiso para enviar mensajes."
        }

        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                contexto.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            // Un SMS normal se corta a los 160 caracteres. Con el enlace del
            // mapa nos pasamos casi siempre, asi que se trocea y se manda
            // como mensaje largo: llega entero y en orden.
            val partes = sms.divideMessage(mensaje)
            sms.sendMultipartTextMessage(numero, null, partes, null, null)
            null
        } catch (e: Exception) {
            "No se ha podido enviar: ${e.message}"
        }
    }

    /**
     * PLAN B: abrir la aplicacion de mensajes con el texto ya escrito.
     *
     * No es automatico -alguien tiene que pulsar enviar-, asi que no vale
     * para una persona inconsciente. Pero hay dos casos en los que salva
     * la situacion: cuando Android se niega a dar el permiso de envio
     * automatico (pasa mas de lo que parece, sobre todo en apps que no
     * vienen de la tienda), y cuando la persona esta consciente pero
     * dolorida y no acierta a escribir. Mejor un mensaje que hay que
     * confirmar que ningun mensaje.
     */
    fun abrirMensajeria(contexto: Context, mensaje: String): Boolean {
        val numero = Ajustes(contexto).telefonoContacto.trim()
        return try {
            val i = android.content.Intent(
                android.content.Intent.ACTION_SENDTO,
                android.net.Uri.parse("smsto:$numero")
            ).apply {
                putExtra("sms_body", mensaje)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            contexto.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** El texto del aviso de emergencia. */
    fun mensajeEmergencia(contexto: Context, motivo: String): String {
        val ajustes = Ajustes(contexto)
        val quien = ajustes.nombrePersona.ifBlank { "La persona" }
        val hora = SimpleDateFormat("HH:mm", Locale("es", "ES")).format(Date())

        val texto = StringBuilder()
        texto.append("AVISO DE CUIDAME. ")
        texto.append("$quien $motivo y no responde. ")
        texto.append("Hora: $hora. ")

        val donde = ubicacion(contexto)
        if (donde != null) {
            texto.append("Última ubicación: ")
            texto.append("https://maps.google.com/?q=${donde.latitude},${donde.longitude}")
        } else {
            texto.append("No se ha podido saber dónde está.")
        }
        return texto.toString()
    }

    /**
     * La ultima ubicacion que el movil ya conocia.
     *
     * A proposito NO se enciende el GPS para pedir una posicion nueva:
     * eso puede tardar un minuto largo bajo techo, y en una emergencia
     * ese minuto vale mas que la precision. Una posicion de hace un rato
     * mandada YA es mejor que una exacta que llega tarde.
     */
    @SuppressLint("MissingPermission")
    private fun ubicacion(contexto: Context): Location? {
        val fina = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val gruesa = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fina && !gruesa) return null

        return try {
            val lm = contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val proveedores = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            proveedores.mapNotNull { p ->
                try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
            }.maxByOrNull { it.time }
        } catch (e: Exception) {
            null
        }
    }
}
