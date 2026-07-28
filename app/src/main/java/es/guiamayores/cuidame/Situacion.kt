package es.guiamayores.cuidame

import kotlin.math.abs

/**
 * DESCRIBIR CON PALABRAS DONDE HA QUEDADO EL MOVIL
 * ================================================
 *
 * DE DONDE SALE ESTA IDEA
 *
 * Salio de proponer que la app hiciera una foto y la interpretara ella
 * -suelo, techo, cielo, alguien cerca- en vez de mandar la imagen. Lo
 * importante de esa idea no es la camara: es darse cuenta de que quien
 * recibe el aviso necesita SABER QUE HA PASADO, no solo unas coordenadas.
 * "Se ha caido" y un punto en un mapa dejan al que lo lee adivinando.
 *
 * Y hay una cosa que ese aviso no decia y que lo cambia todo: si el movil
 * esta encima de la persona o no. Porque el mapa solo vale si estan
 * juntos. Si el movil se ha quedado en la mesa de la cocina, ese punto
 * señala la mesa, no a la persona, y quien lo lee no tiene forma de
 * saberlo. Eso se puede decir, y con bastante seguridad.
 *
 * POR QUE CON ESTOS SENSORES Y NO CON LA CAMARA
 *
 * Cada cosa que se quiere saber tiene un sensor que la sabe mejor:
 *
 *   - Si esta tumbado o de pie: el acelerometro lo dice exacto. Una foto
 *     oscura podria ser el suelo, pero tambien un bolsillo, un bolso, un
 *     cajon o una habitacion de noche.
 *   - Si esta a oscuras: el sensor de luz. Es literalmente para eso.
 *   - Si esta pegado a algo: el de proximidad. Es lo mas cerca que se
 *     puede estar de saber "esta contra un cuerpo o contra una tela".
 *   - Si hay alguien debajo: el propio movimiento fino, la señal de vida
 *     que ya usamos para no confundir un mueble con una persona. Ese dato
 *     ya lo tenemos y es el mas fiable de todos.
 *
 * Ninguno de estos sensores puede grabar nada. No hay imagen que guardar,
 * que perder ni que enviar por error.
 */
object Situacion {

    /**
     * La frase para el mensaje, o null si no se sabe nada.
     *
     * Se construye solo con lo que se puede afirmar. Preferimos decir tres
     * cosas seguras que cinco con una inventada: en un aviso de
     * emergencia, un dato dudoso es peor que ninguno, porque manda a
     * alguien a buscar donde no es.
     */
    fun describir(): String? {
        val trozos = mutableListOf<String>()

        // ---- ¿Esta encima de la persona? Lo primero, porque decide si
        //      el mapa sirve de algo. ----
        val vida = ServicioVigilancia.ultimaVida
        if (vida != null) {
            trozos.add(
                if (vida > 0.05f) "el móvil está encima de la persona (nota su respiración), " +
                                  "así que el sitio del mapa es donde está ella"
                else "OJO: el móvil parece estar solo, apoyado en un mueble o en el suelo. " +
                     "El sitio del mapa es donde está el móvil, puede que no la persona"
            )
        }

        // ---- Postura ----
        val z = ServicioVigilancia.ultimoEjeZ
        if (z != null) {
            trozos.add(
                when {
                    z > 8f -> "está tumbado boca arriba"
                    z < -8f -> "está tumbado boca abajo"
                    abs(z) < 4f -> "está de pie o de canto, como en un bolsillo"
                    else -> "está inclinado"
                }
            )
        }

        // ---- Luz ----
        //
        // El numero es en lux. Menos de 10 es una habitacion a oscuras o
        // el interior de un bolsillo; mas de 1000 es luz de dia al aire
        // libre. Los tramos son anchos a proposito: afinar mas seria
        // fingir una precision que este sensor no tiene.
        val luz = ServicioVigilancia.ultimaLuz
        if (luz != null) {
            trozos.add(
                when {
                    luz < 10f -> "está a oscuras (de noche, o tapado, o en un bolsillo)"
                    luz > 1500f -> "le da luz fuerte, seguramente esté al aire libre"
                    else -> "hay luz normal de casa"
                }
            )
        }

        // ---- Proximidad ----
        val prox = ServicioVigilancia.ultimaProximidad
        if (prox != null && prox < 3f) {
            trozos.add("tiene algo pegado encima: ropa, un bolsillo o un cuerpo")
        }

        if (trozos.isEmpty()) return null
        return "Cómo está el móvil: " + trozos.joinToString("; ") + "."
    }
}
