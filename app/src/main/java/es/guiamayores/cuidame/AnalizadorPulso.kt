package es.guiamayores.cuidame

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * DE UN DEDO SOBRE LA CAMARA A UN NUMERO DE PULSACIONES
 * =====================================================
 *
 * COMO ES POSIBLE ESTO
 *
 * Con la linterna encendida y el dedo tapando la camara, la luz atraviesa
 * la carne y vuelve a la lente. Cada vez que el corazon late, manda un
 * golpe de sangre al dedo; esa sangre absorbe algo mas de luz, y la
 * imagen se oscurece una pizca. Es un cambio minusculo -invisible a
 * simple vista- pero la camara lo mide. Contando esos oscurecimientos se
 * cuentan los latidos. Se llama fotopletismografia y es lo mismo que hace
 * la pinza del dedo en un hospital, con peor precision.
 *
 * DE DONDE SALE LO DEL ESTRES
 *
 * No de las pulsaciones. Un corazon sano NO late como un metronomo: entre
 * un latido y el siguiente hay pequeñas diferencias, y esa irregularidad
 * es buena señal. Cuando el cuerpo esta en tension, el sistema nervioso
 * pisa el acelerador y los latidos se vuelven mas regulares, mas
 * "maquina". Midiendo cuanto varian los intervalos se estima el nivel de
 * activacion. La medida se llama RMSSD.
 *
 * HASTA DONDE LLEGA ESTO, DICHO CLARO
 *
 * La camara da unas 30 imagenes por segundo, o sea una cada 33
 * milisegundos. Para contar pulsaciones sobra. Para el estres se queda
 * justa, porque ahi importan diferencias de pocos milisegundos: por eso
 * la posicion exacta de cada latido se afina calculando entre imagenes
 * (ver afinarPico). Aun asi el numero de estres es orientativo, sirve
 * para comparar CONSIGO MISMO en dias distintos, y no es un diagnostico
 * de nada.
 */
class AnalizadorPulso {

    private val valores = ArrayList<Double>()
    private val tiempos = ArrayList<Long>()

    /**
     * Minimo de segundos antes de dar un resultado con sentido.
     *
     * Para contar pulsaciones bastarian 15. Son 30 por lo del ritmo: para
     * decir si el corazon late a destiempo hacen falta bastantes latidos
     * seguidos, porque con pocos, una respiracion profunda o un par de
     * latidos adelantados ya parecen un desorden. Medio minuto de dedo
     * quieto es poco pedir para lo que se saca a cambio.
     */
    val segundosNecesarios = 30

    fun limpiar() {
        valores.clear()
        tiempos.clear()
    }

    fun añadir(valor: Double, cuando: Long) {
        valores.add(valor)
        tiempos.add(cuando)
        // No dejamos crecer la memoria sin limite: con 60 segundos basta.
        if (valores.size > 2400) {
            valores.removeAt(0)
            tiempos.removeAt(0)
        }
    }

    fun segundosGrabados(): Int {
        if (tiempos.size < 2) return 0
        return ((tiempos.last() - tiempos.first()) / 1000L).toInt()
    }

    /** Como de regular late el corazon. Ver analizarRitmo. */
    enum class Ritmo { REGULAR, DUDOSO, IRREGULAR, POCOS_DATOS }

    data class Resultado(
        val pulsaciones: Int,
        val rmssd: Double,
        val latidos: Int,
        val calidad: Double,
        val ritmo: Ritmo,
        val irregularidad: Double,   // RMSSD relativo al intervalo medio
        val saltos: Double           // proporcion de latidos que "dan un salto"
    )

    /**
     * Devuelve null si todavia no hay datos suficientes o si la señal no
     * sirve. Devolver un numero inventado seria peor que no dar ninguno.
     */
    fun calcular(): Resultado? {
        if (valores.size < 100) return null

        // 1. QUITAR LA DERIVA.
        // La luz de fondo cambia poco a poco -el dedo se mueve, se calienta
        // la lente-, y ese vaiven lento tapa el latido, que es rapido y
        // pequeño. Restando la media movil se queda solo lo que oscila
        // deprisa, que es justo lo que buscamos.
        val VENTANA = 20
        val limpio = DoubleArray(valores.size)
        for (i in valores.indices) {
            var suma = 0.0
            var n = 0
            for (j in maxOf(0, i - VENTANA)..minOf(valores.size - 1, i + VENTANA)) {
                suma += valores[j]; n++
            }
            limpio[i] = valores[i] - (suma / n)
        }

        // 2. ¿HAY SEÑAL SIQUIERA?
        // Si el dedo no esta puesto, o esta flojo, esto es casi plano.
        val media = limpio.average()
        val desv = sqrt(limpio.sumOf { (it - media) * (it - media) } / limpio.size)
        if (desv < 0.05) return null

        // 3. BUSCAR LOS LATIDOS.
        // Un maximo local que ademas destaque sobre el ruido, y separado
        // del anterior al menos 0,3 segundos: mas de 200 pulsaciones por
        // minuto no es un latido, es ruido.
        val umbral = desv * 0.6
        val picos = ArrayList<Double>()   // instante de cada latido, en ms
        var ultimo = -1L
        for (i in 1 until limpio.size - 1) {
            if (limpio[i] <= umbral) continue
            if (limpio[i] < limpio[i - 1] || limpio[i] < limpio[i + 1]) continue
            val t = afinarPico(limpio, tiempos, i)
            if (ultimo < 0 || t - ultimo > 300) {
                picos.add(t.toDouble())
                ultimo = t
            }
        }
        if (picos.size < 6) return null

        // 4. LOS INTERVALOS ENTRE LATIDOS.
        val intervalos = ArrayList<Double>()
        for (i in 1 until picos.size) {
            val d = picos[i] - picos[i - 1]
            // Fuera lo imposible: menos de 300 ms son 200 pulsaciones, mas
            // de 1800 son menos de 33. Cualquiera de las dos, en alguien
            // sentado midiendose el pulso, es un fallo de lectura.
            if (d in 300.0..1800.0) intervalos.add(d)
        }
        if (intervalos.size < 5) return null

        val medio = intervalos.average()
        val bpm = (60000.0 / medio).toInt()
        if (bpm < 35 || bpm > 200) return null

        // 5. LA VARIABILIDAD (RMSSD).
        // Cuanto cambia cada intervalo respecto al siguiente.
        var suma = 0.0
        for (i in 1 until intervalos.size) {
            val d = intervalos[i] - intervalos[i - 1]
            suma += d * d
        }
        val rmssd = sqrt(suma / (intervalos.size - 1))

        // Calidad: si los intervalos son muy dispares entre si, la lectura
        // es poco de fiar (dedo moviendose, luz colandose).
        val dispersion = intervalos.map { abs(it - medio) }.average() / medio
        val calidad = (1.0 - dispersion * 2).coerceIn(0.0, 1.0)

        // 6. ¿LATE REGULAR O A TROMPICONES?
        val irregularidad = rmssd / medio
        var saltos = 0
        for (i in 1 until intervalos.size) {
            if (abs(intervalos[i] - intervalos[i - 1]) > 50.0) saltos++
        }
        val propSaltos = saltos.toDouble() / (intervalos.size - 1)
        val ritmo = analizarRitmo(irregularidad, propSaltos, intervalos.size, calidad)

        return Resultado(
            bpm, rmssd, intervalos.size + 1, calidad,
            ritmo, irregularidad, propSaltos
        )
    }

    /**
     * ¿EL CORAZON LATE A DESTIEMPO?
     * =============================
     *
     * Esta es, con diferencia, la medida mas util que se puede sacar de un
     * dedo apoyado en una camara, y la razon por la que merece la pena
     * todo lo anterior.
     *
     * QUE SE BUSCA
     *
     * La fibrilacion auricular es una arritmia en la que el corazon late
     * sin ningun compas: no es que vaya rapido o lento, es que cada latido
     * cae donde le da la gana. Importa por tres motivos que la convierten
     * casi en el caso ideal para una app asi:
     *
     *   - Es frecuente en mayores: alrededor de una de cada diez personas
     *     por encima de los ochenta.
     *   - Muy a menudo NO SE NOTA. Mucha gente la tiene sin enterarse.
     *   - Es una de las grandes causas de ictus, y se trata. Detectarla a
     *     tiempo cambia el pronostico de verdad.
     *
     * COMO SE DISTINGUE DE UN CORAZON NORMAL
     *
     * Un corazon sano tambien varia -de eso va la medida de tension- pero
     * varia con suavidad y siguiendo la respiracion. En fibrilacion, los
     * intervalos entre latidos saltan de forma desordenada. Se miran dos
     * cosas a la vez:
     *
     *   1. Cuanto varia en relacion a lo que dura un latido.
     *   2. Que proporcion de latidos "pega un salto" respecto al anterior.
     *
     * SE EXIGEN LAS DOS. Con una sola habria muchas falsas alarmas: unos
     * pocos latidos adelantados -extrasistoles, que tiene medio mundo y
     * casi siempre son inofensivos- disparan la primera pero no la
     * segunda.
     *
     * HASTA DONDE LLEGA, Y POR QUE AUN ASI MERECE LA PENA
     *
     * Esto NO diagnostica nada: eso lo hace un electrocardiograma. Es un
     * cribado. Y en cribado el reparto de errores es muy favorable:
     * decirle a alguien sano "enseñe esto a su medico" cuesta una consulta
     * y un susto pequeño; no avisar a alguien que la tiene puede costar un
     * ictus. Por eso el mensaje NUNCA dice "usted tiene" nada, dice
     * "enseñeselo a su medico y pida un electrocardiograma".
     *
     * Los numeros de corte son aproximados y estan tomados del rango
     * habitual en la literatura de cribado con foto-pulso. No son una
     * verdad exacta.
     */
    private fun analizarRitmo(
        irregularidad: Double,
        saltos: Double,
        nIntervalos: Int,
        calidad: Double
    ): Ritmo {
        // Con pocos latidos o con una lectura sucia no se opina: seria
        // irresponsable dar un susto por un dedo mal apoyado.
        if (nIntervalos < 20 || calidad < 0.5) return Ritmo.POCOS_DATOS

        val muyVariable = irregularidad > 0.12
        val muchosSaltos = saltos > 0.40

        return when {
            muyVariable && muchosSaltos -> Ritmo.IRREGULAR
            muyVariable || muchosSaltos -> Ritmo.DUDOSO
            else -> Ritmo.REGULAR
        }
    }

    /**
     * Afina el instante del latido entre dos imagenes.
     *
     * La camara da una imagen cada 33 milisegundos, asi que decir "el
     * latido fue en la imagen 47" tiene un error de hasta 33 ms. Para
     * contar pulsaciones da igual, pero para el estres no: ahi se comparan
     * diferencias de pocos milisegundos y ese error se comeria la medida.
     *
     * Como el pico real es una curva suave, se ajusta una parabola con el
     * punto mas alto y sus dos vecinos y se calcula donde estaria la cima
     * de verdad, que casi nunca cae justo en una imagen.
     */
    private fun afinarPico(y: DoubleArray, t: List<Long>, i: Int): Long {
        if (i <= 0 || i >= y.size - 1) return t[i]
        val a = y[i - 1]; val b = y[i]; val c = y[i + 1]
        val den = a - 2 * b + c
        if (abs(den) < 1e-9) return t[i]
        val desplazamiento = 0.5 * (a - c) / den      // entre -0,5 y +0,5
        val dt = (t[minOf(i + 1, t.size - 1)] - t[i - 1]) / 2.0
        return (t[i] + desplazamiento * dt).toLong()
    }
}
