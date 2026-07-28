package es.guiamayores.cuidame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * PREGUNTARLE AL MOVIL DONDE ESTA
 * ===============================
 *
 * POR QUE HACIA FALTA ESTO
 *
 * Todo lo demas de esta app espera a que pase algo malo: una caida, unas
 * horas sin moverse, un frenazo. Eso sirve para alguien que se cae, pero
 * no para alguien que se PIERDE.
 *
 * Una persona con demencia no se cae ni deja de moverse: sale a la calle
 * a una hora normal, camina con paso normal, y no sabe volver. Para los
 * sensores no esta pasando nada raro. Y no va a pulsar ningun boton de
 * ayuda, porque en ese momento no cree estar perdida. Esperar a que la app
 * detecte algo, en ese caso, es esperar a que ya sea grave.
 *
 * Lo que hace falta es lo contrario: que la familia pueda PREGUNTAR, en
 * cualquier momento, sin que la persona tenga que hacer nada.
 *
 * COMO FUNCIONA
 *
 * El contacto manda un SMS que diga "donde". El movil contesta solo con
 * el sitio, si se esta moviendo y cuanta bateria le queda.
 *
 * Por SMS y no por internet a proposito, igual que el aviso: no hace falta
 * datos, ni cuenta, ni servidor, ni que nadie pague nada, ni que el movil
 * de la persona mayor tenga wifi configurado. Y funciona AUNQUE LA
 * VIGILANCIA ESTE APAGADA, que es justo lo que planteabas: no se puede
 * depender de que alguien acordara activar nada.
 *
 * LO QUE ME PREOCUPA DE ESTO, Y COMO ESTA ATADO
 *
 * Esto es, sin adornos, una forma de localizar a una persona en silencio.
 * Puesto en las manos equivocadas es una herramienta de control. Asi que
 * lleva tres candados y ninguno es opcional:
 *
 *   1. SOLO responde al numero de contacto guardado. A nadie mas. Un
 *      mensaje con la palabra correcta desde otro numero se ignora.
 *   2. CADA consulta queda apuntada en el historial del movil, con la
 *      hora. Nunca es invisible para quien lleva el telefono.
 *   3. CADA consulta saca un aviso en la pantalla diciendo a quien se le
 *      ha mandado la ubicacion.
 *
 * Una funcion asi puede existir. Lo que no puede es ser secreta.
 */
class ReceptorSms : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val mensajes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } else null
            if (mensajes == null || mensajes.isEmpty()) return

            val remitente = mensajes[0].originatingAddress ?: return
            val texto = mensajes.joinToString("") { it.messageBody ?: "" }

            if (!esElContacto(contexto, remitente)) return
            if (!pidePosicion(texto)) return

            // El trabajo de verdad no se hace aqui: un receptor de estos
            // tiene unos segundos de vida y coger una posicion de GPS
            // nueva puede tardar mas. Se arranca un servicio que se toma
            // su tiempo y se apaga solo al terminar.
            val i = Intent(contexto, ServicioConsulta::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                contexto.startForegroundService(i)
            } else {
                contexto.startService(i)
            }
        } catch (e: Exception) {}
    }

    /**
     * Compara solo las ultimas nueve cifras.
     *
     * El mismo telefono llega escrito de maneras distintas segun quien lo
     * mande: con +34, sin el, con espacios, con guiones. Comparar el texto
     * tal cual haria que la funcion fallara justo cuando hace falta, y
     * ademas de una forma silenciosa e imposible de entender desde fuera.
     */
    private fun esElContacto(contexto: Context, remitente: String): Boolean {
        val guardado = Ajustes(contexto).telefonoContacto.filter { it.isDigit() }
        val quienLlama = remitente.filter { it.isDigit() }
        if (guardado.length < 9 || quienLlama.length < 9) return false
        return guardado.takeLast(9) == quienLlama.takeLast(9)
    }

    /**
     * Se acepta cualquier forma normal de preguntarlo.
     *
     * Quien manda esto es un hijo preocupado, no un informatico, y puede
     * escribir "donde estas", "¿DÓNDE?", "ubicacion" o "localiza". Exigir
     * una palabra clave exacta significaria que el dia que hace falta el
     * mensaje no hace nada y nadie entiende por que.
     */
    private fun pidePosicion(texto: String): Boolean {
        val limpio = texto.lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u')
        return limpio.contains("donde") ||
               limpio.contains("ubicacion") ||
               limpio.contains("localiza")
    }
}
