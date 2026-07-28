package es.guiamayores.cuidame

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * DETECTOR DE CAIDAS
 * ==================
 *
 * COMO ES UNA CAIDA, VISTA POR UN ACELEROMETRO
 *
 * Una persona de pie tiene el movil midiendo mas o menos 9,8 (la gravedad
 * de toda la vida). Cuando alguien se cae pasan tres cosas SEGUIDAS, y es
 * esa secuencia -no cada pieza suelta- lo que distingue una caida de
 * cualquier otro trajin:
 *
 *   1. CAIDA LIBRE: durante un instante el movil casi no siente nada.
 *   2. GOLPE: el suelo para el cuerpo de golpe. Pico alto.
 *   3. QUIETUD: quien se cae y esta bien se levanta y se mueve; quien se
 *      ha hecho daño se queda quieto. Si hay movimiento normal despues
 *      del golpe, NO se avisa.
 *
 * POR QUE NO VALE CON DETECTAR EL GOLPE
 *
 * Sentarse fuerte, dejar el movil en la mesa o guardarlo en el bolso dan
 * picos parecidos. Un detector que se equivoca mucho es PEOR que no tener
 * ninguno, porque genera confianza falsa: la familia silencia los avisos
 * y el dia que pasa de verdad no lo ve nadie.
 *
 * LOS UMBRALES, Y POR QUE ESTOS
 *
 * La primera version pedia un golpe de 25 (unos 2,5 veces la gravedad) y
 * no saltaba en las pruebas. El motivo es que 25 es el pico de una caida
 * contra un suelo duro; una caida sobre moqueta, sobre la cama o con el
 * movil en un bolsillo acolchado se queda bastante por debajo, y esas
 * caidas son igual de importantes.
 *
 * Ahora se pide 19 (unas 2 veces la gravedad). Se detectan mas cosas que
 * no son caidas -sentarse de golpe, por ejemplo- y eso es DELIBERADO:
 * para eso esta el minuto de confirmacion. Preferimos preguntar de mas
 * que callarnos de menos, porque el coste de preguntar es una molestia y
 * el coste de callarse es una persona en el suelo sin ayuda.
 */
class DetectorCaida {

    /** Por debajo de esto consideramos que el movil va en caida libre. */
    private val umbralCaidaLibre = 4.0f

    /** Por encima de esto consideramos que ha habido un golpe. */
    var umbralGolpe = 19f

    /**
     * Cuanto se vigila la quietud despues del golpe.
     *
     * Empezo en 4,5 segundos, bajo a 2,5 y sigue siendo 1,8 porque en
     * pruebas reales se seguia haciendo largo. Y con razon: los segundos
     * que uno pasa tirado en el suelo esperando a que el movil reaccione
     * no se parecen en nada a los segundos mirando un reloj.
     *
     * Es un equilibrio, no un numero magico. Cuanto mas corto, antes
     * avisa pero mas facil es confundir un golpe cualquiera con una
     * caida. 1,8 segundos siguen bastando para lo esencial: quien se cae
     * y esta bien se remueve enseguida -se queja, se apoya, se
     * incorpora-, y ese movimiento aparece en el primer segundo.
     *
     * Y si aun asi nos equivocamos, para eso esta el minuto de
     * confirmacion. Ese es el motivo de que se pueda ser agresivo aqui:
     * equivocarse cuesta una pregunta; tardar cuesta tiempo en el suelo.
     */
    private val msVigilandoQuietud = 1800L

    /** Cuanto se puede mover y seguir considerandose "quieto". */
    private val umbralQuieto = 2.2f

    /**
     * EL MINIMO DE MOVIMIENTO QUE DEMUESTRA QUE HAY UNA PERSONA DEBAJO
     * ================================================================
     *
     * Este es el arreglo del fallo mas gordo que ha tenido el detector:
     * dejar el movil en la mesa o soltarlo en el sofa disparaba el aviso.
     *
     * Y visto el codigo, tenia que pasar. Yo solo comprobaba que despues
     * del golpe el movil NO se moviera mucho. Un movil soltado en una mesa
     * cumple eso mejor que nadie: da su golpecito y se queda absolutamente
     * inmovil. Para el detector era la caida perfecta.
     *
     * Lo que faltaba es la otra mitad de la comprobacion. Un movil encima
     * de un mueble esta MUERTO: lo unico que mide es el ruido electrico de
     * su propio sensor, del orden de 0,01-0,03. Un movil encima de una
     * persona, aunque este tirada en el suelo sin poder levantarse, NUNCA
     * esta muerto: la respiracion mueve el pecho, el pulso se transmite, la
     * mano tiembla. Eso deja una señal pequeña pero clarisima, de 0,05 para
     * arriba, varias veces por encima del ruido.
     *
     * Asi que ahora la quietud tiene suelo ademas de techo: para que cuente
     * como caida, el movil tiene que estar quieto PERO VIVO. Y si esta
     * demasiado quieto -mas quieto de lo que puede estar algo apoyado en un
     * cuerpo humano-, se descarta: es un mueble, no una persona.
     */
    var umbralVida = 0.05f

    /**
     * Lo que se deja pasar despues del golpe antes de empezar a medir.
     *
     * El golpe en si y el rebote que viene detras son movimiento brusco. Si
     * entraran en la cuenta, un movil soltado en la mesa parecería lleno de
     * vida durante ese instante y colaria por el suelo nuevo. Se descartan
     * los primeros 300 ms y se mide solo lo que viene despues, que es donde
     * de verdad se distingue un cuerpo de un mueble.
     */
    private val msAsentamiento = 300L

    /** Ventana hacia atras donde buscamos la caida libre previa al golpe. */
    private val msVentanaCaidaLibre = 1200L

    private val gravedad = 9.81f

    private class Medida(val cuando: Long, val fuerza: Float)
    private val recientes = ArrayDeque<Medida>()

    private var vigilandoDesde = 0L
    private var huboCaidaLibre = false
    private var suma = 0.0
    private var sumaCuadrados = 0.0
    private var muestrasQuietud = 0

    // Ventana corta de las ultimas lecturas, para poder enseñar en la
    // pantalla de sensores cuanta "vida" hay AHORA MISMO. Sin esto, ajustar
    // el umbral de vida seria adivinar: con esto se deja el movil en la
    // mesa, se mira el numero, se mete en el bolsillo y se vuelve a mirar.
    private val ultimas = ArrayDeque<Float>()
    private val maxUltimas = 60

    /** Ultimo instante en que la persona se movio de verdad. */
    var ultimoMovimiento = System.currentTimeMillis()
        private set

    // ---- Datos para la pantalla de diagnostico ----
    // Sin esto, cuando el detector no salta no hay forma de saber si es
    // que el golpe se quedo corto, si es que el movil se siguio moviendo,
    // o si el sensor ni siquiera esta llegando. Adivinar sale caro.

    var fuerzaActual = 0f; private set
    var pico = 0f; private set
    var picoUltimoGolpe = 0f; private set
    var quietudUltimoIntento = -1f; private set
    var caidasDetectadas = 0; private set
    var intentosDescartados = 0; private set
    var descartadosPorMueble = 0; private set

    /**
     * Cuanta vida hay ahora mismo, medida sobre el ultimo segundo.
     *
     * Se calcula como la variacion de la fuerza respecto a SU PROPIA media,
     * no respecto a 9,81. La diferencia importa: casi ningun acelerometro
     * marca exactamente 9,81 en reposo -suelen tener una desviacion fija de
     * fabrica-, asi que medir contra 9,81 confundiria un sensor mal
     * calibrado con un movil que se mueve. Contra su propia media, esa
     * desviacion se cancela sola y solo queda el movimiento de verdad.
     */
    fun vidaAhora(): Float {
        if (ultimas.size < 10) return 0f
        var s = 0.0; var s2 = 0.0
        for (v in ultimas) { s += v; s2 += v.toDouble() * v }
        val n = ultimas.size
        val varianza = s2 / n - (s / n) * (s / n)
        return if (varianza <= 0.0) 0f else kotlin.math.sqrt(varianza).toFloat()
    }

    /** "esperando", "comprobando quietud", "caida" o "descartado". */
    var estado = "esperando"; private set

    fun reiniciarPico() {
        pico = 0f
        picoUltimoGolpe = 0f
        quietudUltimoIntento = -1f
        caidasDetectadas = 0
        intentosDescartados = 0
        descartadosPorMueble = 0
        estado = "esperando"
    }

    /**
     * Se llama con cada lectura del acelerometro.
     * Devuelve true SOLO cuando hay sospecha seria de caida.
     */
    fun procesar(x: Float, y: Float, z: Float, ahora: Long = System.currentTimeMillis()): Boolean {
        val fuerza = sqrt(x * x + y * y + z * z)
        val desviacion = abs(fuerza - gravedad)

        fuerzaActual = fuerza
        if (fuerza > pico) pico = fuerza

        if (desviacion > 2.0f) ultimoMovimiento = ahora

        recientes.addLast(Medida(ahora, fuerza))
        while (recientes.isNotEmpty() && ahora - recientes.first().cuando > msVentanaCaidaLibre) {
            recientes.removeFirst()
        }

        ultimas.addLast(fuerza)
        while (ultimas.size > maxUltimas) ultimas.removeFirst()

        // ---- Fase 2: ya hubo golpe, ahora miramos si se queda quieta ----
        if (vigilandoDesde > 0L) {
            // El golpe y su rebote no cuentan: se empieza a medir cuando
            // todo se ha asentado.
            if (ahora - vigilandoDesde >= msAsentamiento) {
                suma += fuerza
                sumaCuadrados += fuerza.toDouble() * fuerza
                muestrasQuietud++
            }

            if (ahora - vigilandoDesde >= msVigilandoQuietud) {
                val movimiento = if (muestrasQuietud >= 10) {
                    val n = muestrasQuietud
                    val varianza = sumaCuadrados / n - (suma / n) * (suma / n)
                    if (varianza <= 0.0) 0f else kotlin.math.sqrt(varianza).toFloat()
                } else 99f
                quietudUltimoIntento = movimiento

                // Si hubo caida libre antes del golpe la sospecha es mayor,
                // asi que se admite algo mas de movimiento posterior.
                val limite = if (huboCaidaLibre) umbralQuieto * 1.4f else umbralQuieto

                // DOS CONDICIONES, NO UNA.
                //   quieto  -> nadie se ha levantado ni se esta moviendo
                //   con vida -> pero hay ALGO debajo: un cuerpo que respira
                // Falta cualquiera de las dos y no se avisa.
                val quieto = movimiento < limite
                val conVida = movimiento > umbralVida

                reiniciar()
                when {
                    quieto && conVida -> {
                        caidasDetectadas++
                        estado = "caida"
                        return true
                    }
                    quieto && !conVida -> {
                        // Demasiado quieto para ser una persona. Es el movil
                        // solo, encima de un mueble.
                        descartadosPorMueble++
                        estado = "descartado: el movil esta solo, no encima de una persona"
                    }
                    else -> {
                        intentosDescartados++
                        estado = "descartado: se siguio moviendo"
                    }
                }
                return false
            }
            return false
        }

        // ---- Fase 1: buscamos el golpe ----
        if (fuerza > umbralGolpe) {
            huboCaidaLibre = recientes.any { it.fuerza < umbralCaidaLibre }
            picoUltimoGolpe = fuerza
            vigilandoDesde = ahora
            suma = 0.0
            sumaCuadrados = 0.0
            muestrasQuietud = 0
            estado = if (huboCaidaLibre) "golpe con caida libre: comprobando"
                     else "golpe: comprobando si se mueve"
        }
        return false
    }

    /**
     * AJUSTA EL UMBRAL A LO QUE ESTE MOVIL PUEDE MEDIR.
     *
     * Esto sale de una prueba real que no cuadraba: el pico maximo medido
     * al simular una caida fue 20,2 y no subia de ahi por mucho que se
     * insistiera. La explicacion mas probable no es que el golpe fuera
     * flojo, sino que el acelerometro SE TOPA: muchos moviles montan un
     * sensor de rango 2g, o sea unos 19,6, y por encima de eso ya no sabe
     * medir. Le pidas lo que le pidas, devuelve su techo.
     *
     * En un movil asi, un umbral de 19 es practicamente inalcanzable, y
     * uno de 25 -el que puse al principio- es literalmente imposible: la
     * app nunca detectaria una caida por fuerte que fuese. Por eso el
     * umbral no puede ser un numero fijo escrito a mano: tiene que
     * calcularse contra lo que cada movil es capaz de ver.
     */
    fun ajustarAlSensor(rangoMaximo: Float) {
        if (rangoMaximo <= 0f) return
        if (rangoMaximo < 25f) {
            // Sensor con poco recorrido: se pide el 80% de su techo.
            umbralGolpe = maxOf(14f, rangoMaximo * 0.80f)
        }
    }

    /** Para cuando la persona ya ha dicho que esta bien. */
    fun reiniciar() {
        vigilandoDesde = 0L
        huboCaidaLibre = false
        suma = 0.0
        sumaCuadrados = 0.0
        muestrasQuietud = 0
    }

    /** Para el boton de prueba: marca que acaba de haber movimiento. */
    fun marcarMovimiento(ahora: Long = System.currentTimeMillis()) {
        ultimoMovimiento = ahora
    }
}
