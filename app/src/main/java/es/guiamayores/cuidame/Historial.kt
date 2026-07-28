package es.guiamayores.cuidame

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EL HISTORIAL
 * ============
 *
 * POR QUE ESTO VALE MAS QUE CUALQUIER MEDICION SUELTA
 *
 * Una lectura aislada casi no dice nada. "Hoy el pulso salio irregular"
 * puede ser un mal apoyo del dedo. Pero "seis de las ultimas diez han
 * salido irregulares" es un hecho con el que un medico hace algo. Lo
 * mismo con todo lo demas: lo que importa no es el numero de hoy, es si
 * se repite y hacia donde va.
 *
 * Y hay una razon practica: en la consulta hay ocho minutos. Llegar con
 * una lista de fechas y numeros vale infinitamente mas que intentar
 * recordar "creo que alguna vez me salio raro".
 *
 * SOLO SE GUARDA LO QUE ESTA BIEN MEDIDO
 *
 * Esta es la regla que decide si el historial sirve o estorba. Una lista
 * llena de lecturas malas -dedo flojo, movimiento, ojo mal encuadrado- no
 * es un historial: es ruido que hace desconfiar de todo lo demas, y
 * ademas puede llevar a conclusiones falsas si alguien cuenta cuantas
 * salieron raras. Cada pantalla decide si su medicion merece guardarse, y
 * ante la duda no se guarda. Mejor cinco datos buenos que treinta dudosos.
 *
 * Todo se queda en el movil, en un fichero de texto normal. Ni nube, ni
 * cuenta, ni nada que se pueda caer.
 */
object Historial {

    private const val FICHERO = "historial.txt"
    private const val SEP = "\u0001"   // caracter de control: nunca
    // aparece en un texto escrito por una persona, asi que no puede
    // romper una linea del fichero. Se escribe como escape y no como
    // el caracter crudo: un byte de control suelto en el codigo fuente
    // es justo lo que se corrompe al pasar por git o por el compilador.

    class Entrada(
        val cuando: Long,
        val tipo: String,
        val resumen: String,
        val detalle: String
    ) {
        fun fecha(): String =
            SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(cuando))
        fun hora(): String =
            SimpleDateFormat("HH:mm", Locale("es", "ES")).format(Date(cuando))
    }

    private fun fichero(c: Context) = File(c.filesDir, FICHERO)

    /** Guarda una medicion. Solo llamar si la medicion es de fiar. */
    fun añadir(c: Context, tipo: String, resumen: String, detalle: String = "") {
        try {
            val limpio = { s: String -> s.replace(SEP, " ").replace("\n", " · ") }
            val linea = listOf(
                System.currentTimeMillis().toString(),
                limpio(tipo), limpio(resumen), limpio(detalle)
            ).joinToString(SEP)
            fichero(c).appendText(linea + "\n")
        } catch (e: Exception) {}
    }

    /** De la mas reciente a la mas antigua. */
    fun leer(c: Context): List<Entrada> {
        return try {
            val f = fichero(c)
            if (!f.exists()) return emptyList()
            f.readLines().mapNotNull { l ->
                val p = l.split(SEP)
                if (p.size < 3) null
                else Entrada(
                    p[0].toLongOrNull() ?: return@mapNotNull null,
                    p[1], p[2], if (p.size > 3) p[3] else ""
                )
            }.sortedByDescending { it.cuando }
        } catch (e: Exception) { emptyList() }
    }

    fun borrar(c: Context) {
        try { fichero(c).delete() } catch (e: Exception) {}
    }

    /**
     * El historial como texto corrido, para mandarlo o enseñarlo.
     *
     * Pensado para que un medico lo lea de un vistazo: lo mas reciente
     * arriba, una linea por medicion, sin adornos.
     */
    fun comoTexto(c: Context): String {
        val lista = leer(c)
        if (lista.isEmpty()) return "Todavía no hay mediciones guardadas."
        val a = Ajustes(c)
        val sb = StringBuilder()
        sb.append("MEDICIONES DE LA APP CUÍDAME\n")
        if (a.nombrePersona.isNotBlank()) sb.append("Persona: ${a.nombrePersona}\n")
        sb.append("Generado el ")
        sb.append(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date()))
        sb.append("\n\nAviso: estas mediciones las hace un teléfono móvil, no un aparato ")
        sb.append("médico. Sirven como orientación y para ver la evolución.\n\n")
        for (e in lista) {
            sb.append("${e.fecha()} ${e.hora()}  —  ${e.tipo}: ${e.resumen}")
            if (e.detalle.isNotBlank()) sb.append("  (${e.detalle})")
            sb.append("\n")
        }
        return sb.toString()
    }
}
