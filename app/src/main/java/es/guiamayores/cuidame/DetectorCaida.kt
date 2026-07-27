package es.guiamayores.cuidame

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * DETECTOR DE CAIDAS
 * ==================
 *
 * Lee el acelerometro y decide si lo que acaba de pasar parece una caida.
 *
 * COMO ES UNA CAIDA, VISTA POR UN ACELEROMETRO
 *
 * Una persona de pie tiene el movil midiendo mas o menos 9,8 (la gravedad
 * de toda la vida). Cuando alguien se cae pasan tres cosas SEGUIDAS, y es
 * esa secuencia -no cada pieza suelta- lo que distingue una caida de
 * cualquier otro trajin:
 *
 *   1. CAIDA LIBRE: durante un instante el movil casi no siente nada,
 *      porque va cayendo con la persona. La medida baja hacia cero.
 *   2. GOLPE: el suelo para el cuerpo de golpe. Pico muy alto.
 *   3. QUIETUD: y esto es lo importante. Alguien que se cae y esta bien
 *      se levanta, se queja, se mueve. Alguien que se ha hecho daño de
 *      verdad se queda quieto. Si despues del golpe hay movimiento
 *      normal, NO se avisa.
 *
 * POR QUE NO VALE CON DETECTAR EL GOLPE
 *
 * Sentarse fuerte en una silla, dejar caer el movil en la mesa o meterlo
 * en el bolso dan picos parecidos a una caida. Si avisaramos con solo
 * eso, la familia recibiria falsas alarmas todos los dias, silenciaria
 * los avisos en tres dias, y el dia que pasara de verdad no lo veria
 * nadie. Un detector que se equivoca mucho es PEOR que no tener ninguno,
 * porque genera confianza falsa.
 *
 * Por eso hay dos filtros mas: la quietud posterior, y sobre todo la
 * ventana de confirmacion que hay despues (ver AlarmaActivity), donde la
 * persona tiene un minuto largo para decir "estoy bien".
 *
 * LO QUE ESTE DETECTOR NO PUEDE HACER, Y HAY QUE DECIRLO
 *
 * El movil tiene que ir ENCIMA de la persona. En un bolsillo detecta
 * razonablemente; en un bolso colgado, peor; encima de la mesa, nada de
 * nada. Un reloj en la muñeca lo hace mejor que cualquier movil. Esto es
 * una ayuda mas, no una garantia, y la app lo dice por escrito.
 */
class DetectorCaida {

    /** Por debajo de esto consideramos que el movil va en caida libre. */
    private val umbralCaidaLibre = 3.5f

    /** Por encima de esto consideramos que ha habido un golpe fuerte. */
    private val umbralGolpe = 25f

    /** Cuanto se vigila la quietud despues del golpe. */
    private val msVigilandoQuietud = 5000L

    /** Cuanto se puede mover y seguir considerandose "quieto". */
    private val umbralQuieto = 1.8f

    /** Ventana hacia atras donde buscamos la caida libre previa al golpe. */
    private val msVentanaCaidaLibre = 1200L

    private val gravedad = 9.81f

    // Historial corto de medidas recientes, para poder mirar hacia atras
    private class Medida(val cuando: Long, val fuerza: Float)
    private val recientes = ArrayDeque<Medida>()

    private var vigilandoDesde = 0L
    private var huboCaidaLibre = false
    private var movimientoAcumulado = 0f
    private var muestrasQuietud = 0

    /** Ultimo instante en que la persona se movio de verdad. */
    var ultimoMovimiento = System.currentTimeMillis()
        private set

    /**
     * Se llama con cada lectura del acelerometro.
     * Devuelve true SOLO cuando hay sospecha seria de caida.
     */
    fun procesar(x: Float, y: Float, z: Float, ahora: Long = System.currentTimeMillis()): Boolean {
        val fuerza = sqrt(x * x + y * y + z * z)
        val desviacion = abs(fuerza - gravedad)

        // Movimiento normal de una persona viva: andar, darse la vuelta,
        // levantarse. Sirve para la vigilancia de inmovilidad prolongada.
        if (desviacion > 2.0f) ultimoMovimiento = ahora

        recientes.addLast(Medida(ahora, fuerza))
        while (recientes.isNotEmpty() && ahora - recientes.first().cuando > msVentanaCaidaLibre) {
            recientes.removeFirst()
        }

        // ---- Fase 2: ya hubo golpe, ahora miramos si se queda quieta ----
        if (vigilandoDesde > 0L) {
            movimientoAcumulado += desviacion
            muestrasQuietud++

            if (ahora - vigilandoDesde >= msVigilandoQuietud) {
                val movimientoMedio = if (muestrasQuietud > 0) movimientoAcumulado / muestrasQuietud else 99f
                val seQuedoQuieta = movimientoMedio < umbralQuieto
                // Si hubo caida libre antes del golpe la sospecha es mucho
                // mayor; si no la hubo, exigimos una quietud mas clara.
                val sospecha = if (huboCaidaLibre) seQuedoQuieta
                               else seQuedoQuieta && movimientoMedio < umbralQuieto / 2

                reiniciar()
                return sospecha
            }
            return false
        }

        // ---- Fase 1: buscamos el golpe ----
        if (fuerza > umbralGolpe) {
            huboCaidaLibre = recientes.any { it.fuerza < umbralCaidaLibre }
            vigilandoDesde = ahora
            movimientoAcumulado = 0f
            muestrasQuietud = 0
        }
        return false
    }

    /** Para cuando la persona ya ha dicho que esta bien. */
    fun reiniciar() {
        vigilandoDesde = 0L
        huboCaidaLibre = false
        movimientoAcumulado = 0f
        muestrasQuietud = 0
    }

    /** Para el boton de prueba: marca que acaba de haber movimiento. */
    fun marcarMovimiento(ahora: Long = System.currentTimeMillis()) {
        ultimoMovimiento = ahora
    }
}
