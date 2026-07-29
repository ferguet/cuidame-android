package es.guiamayores.cuidame

import android.content.Context
import java.util.Calendar

/**
 * EL QUE MIRA EL HISTORIAL POR TI
 * ===============================
 *
 * EL AGUJERO QUE TAPA
 *
 * La app lleva semanas guardando pulso, respiracion, equilibrio, temblor
 * y caidas. Y hasta ahora todo eso se quedaba ahi dentro esperando a que
 * alguien lo abriera. La persona mayor no va a interpretar sus propios
 * numeros -ni tiene por que-, y el hijo no va a entrar cada semana a leer
 * una lista de fechas. O sea que teniamos el dato y no servia de nada.
 *
 * Esto lo mira solo, una vez al dia, y avisa al contacto SOLO cuando hay
 * un patron que merece que alguien pregunte que tal.
 *
 * LAS TRES REGLAS QUE LO HACEN UTIL EN VEZ DE MOLESTO
 *
 * 1. NUNCA POR UNA MEDICION SUELTA. Esta es la leccion que aprendimos a
 *    base de darle un susto a un chaval sano: una lectura aislada no
 *    significa nada. Todo lo de aqui exige que se repita.
 *
 * 2. NUNCA POR UN SOLO DATO. Hace falta o bien una señal fuerte y
 *    repetida, o dos señales distintas a la vez. Un pulso alto un par de
 *    veces puede ser el cafe; un pulso alto MAS la respiracion acelerada
 *    MAS el equilibrio peor es otra cosa.
 *
 * 3. COMO MUCHO UN MENSAJE A LA SEMANA. Un aviso que llega todos los dias
 *    deja de leerse al tercero, y entonces el dia que importa se pierde
 *    entre los demas. Es el mismo motivo por el que la alarma de caida
 *    tiene un minuto de confirmacion.
 *
 * LO QUE ESTE MENSAJE NO ES
 *
 * No es una urgencia y lo dice en su propio texto. No diagnostica nada ni
 * nombra ninguna enfermedad: cuenta lo que se ha medido y sugiere
 * preguntar. La diferencia entre "su madre tiene una insuficiencia
 * cardiaca" y "lleva tres semanas andando la mitad y con el pulso alto,
 * pregúntele que tal se encuentra" es la diferencia entre asustar y
 * ayudar.
 */
object Vigia {

    private class Señal(val texto: String, val fuerte: Boolean)

    /**
     * LA PULSERA MANDA, Y LAS PRUEBAS DE LA APP ACOMPAÑAN.
     *
     * Este orden importa mas que cualquier umbral, y lo tenia del reves.
     *
     * Todo lo que mide la app con la pantalla -el dedo en la camara, el
     * equilibrio, el temblor, la foto del ojo- exige que la persona sepa y
     * pueda hacerlo. En la practica eso significa que solo se mide el dia
     * que va el hijo o la cuidadora: una vez al mes, con suerte. Con esa
     * frecuencia no se detecta nada a tiempo.
     *
     * La pulsera no exige ninguna habilidad. Solo hay que llevarla puesta.
     * Mide todas las noches, sin que nadie se acuerde de nada, y da
     * exactamente lo que hace falta para ver una tendencia: muchos dias
     * seguidos del mismo dato.
     *
     * Asi que la vigilancia se apoya en la pulsera y las pruebas manuales
     * pasan a ser refuerzo: cuando existen suman, y cuando no existen no
     * se echa nada en falta.
     */
    suspend fun revisar(c: Context): String? {
        val señales = mutableListOf<Señal>()

        // ---- 1. LO PRIMERO: ¿SIGUE LLEGANDO ALGO? ----
        //
        // Va antes que ningun dato de salud porque si esto falla, todo lo
        // demas es silencio y el silencio se confunde con "va todo bien".
        val horas = Pulsera.horasDesdeElUltimoDato(c)
        if (horas != null && horas > 36) {
            señales.add(Señal(
                "Hace ${horas.toInt()} horas que la pulsera no manda nada. O se la ha quitado, " +
                "o se quedó sin batería, o la app de la pulsera ha dejado de sincronizar. " +
                "Mientras tanto no se está vigilando nada de la pulsera.", true
            ))
        }

        // ---- 2. LA PULSERA, COMPARADA CONSIGO MISMA ----
        val cambios = try { Pulsera.comparar(c) } catch (e: Exception) { emptyList() }
        for (cambio in cambios) señales.add(Señal(cambio.quePasa, false))

        // Pulso en reposo subido MAS andar menos: esa pareja es la que de
        // verdad avisa. Por separado cada una puede ser cualquier cosa;
        // juntas son el patron de alguien que se esta encontrando mal y
        // todavia no lo ha dicho.
        if (cambios.size >= 2) {
            señales.add(Señal(
                "Han cambiado varias cosas a la vez, y eso pesa más que cualquiera por separado.",
                true
            ))
        }

        // ---- 3. LAS PRUEBAS DE LA APP, SI LAS HAY ----
        val hist = Historial.leer(c)
        pulsoRaro(hist)?.let { señales.add(it) }
        ritmoRepetido(hist)?.let { señales.add(it) }
        respiracionAlta(hist)?.let { señales.add(it) }
        equilibrioPeor(hist)?.let { señales.add(it) }
        caidasRepetidas(hist)?.let { señales.add(it) }

        val hayFuerte = señales.any { it.fuerte }
        if (!hayFuerte && señales.size < 2) return null

        val quien = Ajustes(c).nombrePersona.ifBlank { "La persona" }
        val sb = StringBuilder()
        sb.append("CUIDAME. Esto NO es una urgencia.\n\n")
        sb.append("Mirando las mediciones de $quien de las últimas semanas, hay ")
        sb.append(if (señales.size == 1) "algo" else "un par de cosas")
        sb.append(" que conviene que sepa:\n\n")
        for (s in señales) sb.append("• ").append(s.texto).append("\n")
        sb.append("\nNo dice que le pase nada: son medidas hechas con un móvil, no con ")
        sb.append("un aparato médico. Pero merece la pena llamarle y preguntarle qué tal ")
        sb.append("se encuentra, y comentarlo en la próxima consulta.")
        return sb.toString()
    }

    // -----------------------------------------------------------------
    //  CADA SEÑAL, Y POR QUE ESE CORTE Y NO OTRO
    // -----------------------------------------------------------------

    /**
     * Pulso en reposo claramente fuera de sitio, y repetido.
     *
     * Por debajo de 45 o por encima de 110 en reposo ya no es variacion
     * normal. Se exigen tres de las ultimas cinco para que no cuente una
     * tarde de cafes ni una medicion hecha despues de subir la escalera.
     */
    private fun pulsoRaro(hist: List<Historial.Entrada>): Señal? {
        val pulsos = hist.filter { it.tipo == "Pulso" }.take(5)
            .mapNotNull { Regex("(\\d+) ppm").find(it.resumen)?.groupValues?.get(1)?.toIntOrNull() }
        if (pulsos.size < 4) return null
        val bajos = pulsos.count { it < 45 }
        val altos = pulsos.count { it > 110 }
        return when {
            bajos >= 3 -> Señal("El pulso le sale muy bajo varias veces (${pulsos.joinToString(", ")}).", true)
            altos >= 3 -> Señal("El pulso le sale muy alto varias veces (${pulsos.joinToString(", ")}).", true)
            else -> null
        }
    }

    /** Ritmo desigual repetido: la misma regla que usa la pantalla del pulso. */
    private fun ritmoRepetido(hist: List<Historial.Entrada>): Señal? {
        val ultimos = hist.filter { it.tipo == "Pulso" }.take(6)
        if (ultimos.size < 4) return null
        val malos = ultimos.count {
            it.resumen.contains("IRREGULAR") || it.resumen.contains("desigual")
        }
        return if (malos >= 3)
            Señal("El corazón le late desigual en $malos de las últimas ${ultimos.size} mediciones. " +
                  "Esto sí conviene enseñárselo al médico y pedir un electrocardiograma.", true)
        else null
    }

    /**
     * Respiracion acelerada en reposo.
     *
     * De todas las constantes que mide esta app, la respiracion es la que
     * mas se ignora y la que antes cambia: se acelera dias antes de que se
     * note una neumonia o de que un corazon se descompense, cuando la
     * persona todavia dice que esta bien.
     */
    private fun respiracionAlta(hist: List<Historial.Entrada>): Señal? {
        val resp = hist.filter { it.tipo == "Pulso" }.take(4)
            .mapNotNull { Regex("respiración (\\d+)").find(it.detalle)?.groupValues?.get(1)?.toIntOrNull() }
        if (resp.size < 3) return null
        return if (resp.count { it > 22 } >= 2)
            Señal("Respira más deprisa de lo normal estando en reposo (${resp.joinToString(", ")} por minuto).", false)
        else null
    }

    /** El equilibrio ha bajado respecto a como estaba. */
    private fun equilibrioPeor(hist: List<Historial.Entrada>): Señal? {
        val notas = hist.filter { it.tipo == "Equilibrio" }.take(4)
            .mapNotNull { Regex("^(\\d) de 4").find(it.resumen)?.groupValues?.get(1)?.toIntOrNull() }
        if (notas.size < 2) return null
        val ahora = notas.first()
        val antes = notas.drop(1).max()
        return if (ahora <= 2 && antes - ahora >= 2)
            Señal("El equilibrio ha empeorado: antes sacaba $antes de 4 y ahora $ahora. " +
                  "Vale la pena revisar la casa (alfombras, cables, luz de noche).", false)
        else null
    }

    /**
     * Dos caidas en un mes.
     *
     * Es de los datos con mas peso que hay: quien se ha caido dos veces
     * tiene bastantes mas papeletas de tener una caida grave que quien no
     * se ha caido ninguna. Y es justo el dato que se pierde, porque las
     * caidas de las que uno se levanta no se cuentan a nadie.
     */
    private fun caidasRepetidas(hist: List<Historial.Entrada>): Señal? {
        val hace30 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
        val caidas = hist.count { it.tipo == "Caída" && it.cuando > hace30 }
        return if (caidas >= 2)
            Señal("Se ha caído $caidas veces este mes. Aunque se levantara bien, " +
                  "repetirse es lo que de verdad avisa.", true)
        else null
    }
}
