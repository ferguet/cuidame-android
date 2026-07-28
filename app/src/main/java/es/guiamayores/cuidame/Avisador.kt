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
            // PRIMERO EN PALABRAS, DESPUES EL ENLACE.
            //
            // Quien recibe esto lo lee de un vistazo y con el susto en el
            // cuerpo. "Está en casa" y "está FUERA de casa" llevan a hacer
            // cosas distintas -subir a mirar, o salir a buscar- y se
            // entienden sin abrir nada. El mapa sigue estando debajo para
            // el sitio exacto.
            when (enCasa(contexto, donde)) {
                true -> texto.append("Está EN CASA. ")
                false -> texto.append("Está FUERA de casa. ")
                null -> {}
            }
            texto.append("Sitio: ")
            texto.append("https://maps.google.com/?q=${donde.latitude},${donde.longitude}")
        } else {
            texto.append("No se ha podido saber dónde está.")
        }

        // Y por ultimo, en que situacion ha quedado el movil, contado con
        // palabras. Va al final a proposito: lo urgente -quien, que, donde-
        // tiene que caber en la primera pantalla del movil de quien lo
        // recibe, sin tener que desplegar nada.
        Situacion.describir()?.let { texto.append(" "); texto.append(it) }

        return texto.toString()
    }

    /**
     * LA RESPUESTA A "¿DONDE ESTA?"
     *
     * Pensada para leerse de un tiron por alguien que esta preocupado y
     * andando por la calle. Primero si se esta moviendo -que es lo que
     * dice si esta bien y en marcha o parada en un sitio-, luego donde, y
     * al final la bateria, que decide cuanto tiempo se va a poder seguir
     * preguntando.
     *
     * @param nueva la posicion recien cogida, o null si no dio tiempo.
     */
    fun mensajeConsulta(contexto: Context, nueva: Location?): String {
        val a = Ajustes(contexto)
        val quien = a.nombrePersona.ifBlank { "La persona" }
        val hora = SimpleDateFormat("HH:mm", Locale("es", "ES")).format(Date())

        val t = StringBuilder()
        t.append("CUIDAME. $quien, a las $hora. ")

        // ---- ¿Se esta moviendo? ----
        //
        // Es lo primero porque es lo que mas tranquiliza o mas alarma. Un
        // punto en un mapa no distingue entre "va andando por la calle" y
        // "lleva dos horas sin moverse en ese sitio", y esas dos cosas son
        // completamente distintas para quien lee esto.
        val velocidad = if (ServicioVigilancia.enCoche) ServicioVigilancia.velocidadCoche else 0f
        val quieta = System.currentTimeMillis() - ServicioVigilancia.ultimoMovimientoConocido
        t.append(
            when {
                velocidad > 8f -> "Va en un vehículo, a ${velocidad.toInt()} km/h. "
                !ServicioVigilancia.activo -> "(La vigilancia está apagada, no sé si se mueve.) "
                quieta < 3 * 60_000L -> "Se está moviendo ahora mismo. "
                else -> "Lleva ${quieta / 60_000L} minutos sin moverse. "
            }
        )

        // ---- ¿Donde? ----
        val donde = nueva ?: ubicacion(contexto)
        if (donde != null) {
            when (enCasa(contexto, donde)) {
                true -> t.append("Está EN CASA. ")
                false -> t.append("Está FUERA de casa. ")
                null -> {}
            }
            // LA EDAD DE LA POSICION NO ES UN DETALLE.
            //
            // Una posicion de hace tres horas mandada sin fecha manda a
            // quien la lee al sitio donde la persona estaba, no donde
            // esta. Es peor que no mandar nada, porque ademas da
            // confianza.
            val minutos = (System.currentTimeMillis() - donde.time) / 60_000L
            if (nueva != null && minutos < 3) t.append("Sitio AHORA MISMO: ")
            else t.append("Última posición, de hace $minutos min: ")
            t.append("https://maps.google.com/?q=${donde.latitude},${donde.longitude} ")
        } else {
            t.append("No he podido saber dónde está. ")
        }

        bateria(contexto)?.let { t.append("Batería del móvil: $it%.") }
        return t.toString()
    }

    private fun bateria(contexto: Context): Int? = try {
        val bm = contexto.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 1..100 }
    } catch (e: Exception) { null }

    /** El aviso de que el movil se queda sin bateria y dejara de vigilar. */
    fun mensajeBateria(contexto: Context): String {
        val quien = Ajustes(contexto).nombrePersona.ifBlank { "La persona" }
        return "AVISO DE CUIDAME. Al móvil de $quien le queda muy poca batería. " +
               "Cuando se apague dejará de vigilar las caídas. Conviene recordarle que lo cargue."
    }

    /**
     * ¿Esta el movil en el sitio donde suele dormir?
     *
     * Devuelve null si todavia no se sabe donde es la casa. Se admiten 150
     * metros de margen a proposito: una posicion cogida dentro de un piso,
     * sin ver el cielo, se apoya en las antenas y en el wifi y se va
     * facilmente cien metros. Apretar mas el margen solo conseguiria decir
     * "esta fuera de casa" a alguien que esta en su cocina, que es
     * exactamente el error que no nos podemos permitir.
     */
    private fun enCasa(contexto: Context, donde: Location): Boolean? {
        val a = Ajustes(contexto)
        if (!a.sabeDondeEsLaCasa()) return null
        val salida = FloatArray(1)
        return try {
            Location.distanceBetween(
                a.latitudCasa.toDouble(), a.longitudCasa.toDouble(),
                donde.latitude, donde.longitude, salida
            )
            salida[0] < 150f
        } catch (e: Exception) { null }
    }

    /**
     * Guarda donde duerme el movil. Se llama de madrugada.
     *
     * No hay media ni historial: se guarda el ultimo sitio conocido de
     * noche y punto. Si la persona se muda, en una noche el dato ya es el
     * nuevo, sin que nadie tenga que ir a cambiar nada en ningun ajuste.
     */
    fun aprenderCasa(contexto: Context) {
        val donde = ubicacion(contexto) ?: return
        val a = Ajustes(contexto)
        a.latitudCasa = donde.latitude.toFloat()
        a.longitudCasa = donde.longitude.toFloat()
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
