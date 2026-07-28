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

    /**
     * COMO DE QUIETO ESTA EL DEDO, AHORA MISMO.
     *
     * Esto no mide salud, mide si la medicion sirve. Y es util justo
     * porque se ve EN DIRECTO: la persona corrige el dedo mientras mide,
     * en vez de enterarse a los treinta segundos de que no valia.
     *
     * Se deduce de la deriva del brillo. El latido es un temblor pequeño y
     * rapido sobre un brillo estable; si el dedo se mueve, el brillo de
     * fondo se va hacia arriba o hacia abajo, y ese desplazamiento es
     * mucho mayor que el propio latido. Comparando la primera mitad del
     * ultimo segundo con la segunda se ve al momento.
     */
    enum class Firmeza { BIEN, REGULAR, MAL, SIN_DEDO }

    /** Un trozo de la onda para dibujar, ya limpia de deriva. */
    class Onda(val puntos: FloatArray, val picos: IntArray)

    /**
     * CUANTAS VECES RESPIRA POR MINUTO
     * ================================
     *
     * Sale del MISMO dedo, sin pedirle nada nuevo a la persona, y esa es
     * la mitad de su gracia.
     *
     * COMO ES POSIBLE
     *
     * Al respirar, el pecho cambia la presion dentro del torax y eso
     * altera un poquito la cantidad de sangre que llega al dedo. El
     * resultado es que el brillo de fondo -no los latidos, el fondo sobre
     * el que cabalgan- sube y baja despacio, al compas de la respiracion.
     *
     * Fijate en lo que hacemos aqui: es EXACTAMENTE lo que tiramos a la
     * basura para medir el pulso. Alli quitabamos el vaiven lento por ser
     * estorbo; aqui el vaiven lento ES el dato y lo que estorba son los
     * latidos. La misma señal contiene las dos cosas, solo hay que mirar
     * a distinta velocidad.
     *
     * POR QUE MERECE LA PENA
     *
     * La respiracion es la constante vital mas desatendida de todas. Sube
     * ANTES que el pulso y antes que la fiebre cuando algo va mal: una
     * neumonia que empieza, un corazon que se descompensa, una infeccion.
     * Los hospitales la usan en sus escalas de alerta precoz justo por
     * eso. Y en casa no la mira nadie, porque contar respiraciones un
     * minuto entero es incomodo y se olvida.
     *
     * Aqui sale sola, de regalo, cada vez que alguien se toma el pulso.
     */
    fun respiracion(): Int? {
        if (valores.size < 400) return null          // menos de ~13 s no da
        val n = valores.size

        // 1. Aplanar los latidos. Una media movil de medio segundo se come
        //    el pulso (que va a mas de un latido por segundo) y deja el
        //    vaiven de la respiracion intacto.
        val v1 = 15
        val fondo = DoubleArray(n)
        for (i in 0 until n) {
            var s = 0.0; var c = 0
            for (j in maxOf(0, i - v1)..minOf(n - 1, i + v1)) { s += valores[j]; c++ }
            fondo[i] = s / c
        }

        // 2. Quitar la deriva muy lenta (el dedo que se calienta, la mano
        //    que cede). Ventana de unos 5 segundos.
        val v2 = 150
        val onda = DoubleArray(n)
        for (i in 0 until n) {
            var s = 0.0; var c = 0
            for (j in maxOf(0, i - v2)..minOf(n - 1, i + v2)) { s += fondo[j]; c++ }
            onda[i] = fondo[i] - s / c
        }

        val media = onda.average()
        val desv = sqrt(onda.sumOf { (it - media) * (it - media) } / n)
        if (desv < 0.02) return null                 // no se aprecia respiracion

        // 3. Contar las subidas. Separacion minima de 1,5 segundos: mas de
        //    40 respiraciones por minuto no lo hace nadie sentado.
        val muestrasPorSegundo = n.toDouble() /
            ((tiempos.last() - tiempos.first()) / 1000.0).coerceAtLeast(1.0)
        val separacionMinima = (1.5 * muestrasPorSegundo).toInt().coerceAtLeast(5)

        var ciclos = 0
        var ultimo = -separacionMinima
        for (i in 1 until n - 1) {
            if (onda[i] > desv * 0.5 && onda[i] >= onda[i - 1] && onda[i] >= onda[i + 1]) {
                if (i - ultimo >= separacionMinima) { ciclos++; ultimo = i }
            }
        }

        val segundos = (tiempos.last() - tiempos.first()) / 1000.0
        if (segundos < 12) return null
        val porMinuto = (ciclos * 60.0 / segundos).toInt()

        // Fuera de este rango, en alguien sentado midiendose el pulso, es
        // un fallo de lectura y no una respiracion rara. Mejor callarse.
        return if (porMinuto in 8..30) porMinuto else null
    }

    /** Como de firme esta el dedo en este instante. */
    fun firmezaActual(): Firmeza {
        if (valores.size < 40) return Firmeza.SIN_DEDO
        val ultimos = valores.takeLast(60)
        val media = ultimos.average()
        val desv = sqrt(ultimos.sumOf { (it - media) * (it - media) } / ultimos.size)

        // Señal casi plana: o no hay dedo, o esta apretando tanto que
        // corta el riego y ya no pasa sangre que medir.
        if (desv < 0.12) return Firmeza.SIN_DEDO

        val mitad = ultimos.size / 2
        val primera = ultimos.take(mitad).average()
        val segunda = ultimos.takeLast(mitad).average()
        val deriva = abs(segunda - primera)

        // SUAVIZADO, PARA QUE EL COLOR NO PARPADEE.
        //
        // Mirando solo los ultimos dos segundos, la deriva salta mucho de
        // una lectura a la siguiente y el semaforo cambiaba de color
        // varias veces por segundo. Eso no informa: marea, y encima hace
        // dudar de todo lo demas que dice la app.
        //
        // Se arrastra el valor anterior con peso: cada lectura nueva solo
        // mueve el resultado un 15%. El color sigue reaccionando a un
        // movimiento de verdad en menos de un segundo, pero deja de
        // temblar con el ruido normal.
        derivaSuave = if (derivaSuave <= 0.0) deriva
                      else derivaSuave * 0.85 + deriva * 0.15

        return when {
            derivaSuave > 3.0 -> Firmeza.MAL
            derivaSuave > 1.0 -> Firmeza.REGULAR
            else -> Firmeza.BIEN
        }
    }

    private var derivaSuave = 0.0

    /**
     * Devuelve el ultimo trozo de onda, ya sin deriva, con los latidos
     * marcados. Es lo que se dibuja en pantalla.
     */
    fun ondaReciente(cuantos: Int = 160): Onda {
        if (valores.size < 20) return Onda(FloatArray(0), IntArray(0))
        val trozo = valores.takeLast(cuantos)

        // Misma idea que en calcular(): quitar el vaiven lento para que se
        // vea el latido, que es lo pequeño y rapido.
        val ventana = 12
        val limpio = FloatArray(trozo.size)
        for (i in trozo.indices) {
            var suma = 0.0; var n = 0
            for (j in maxOf(0, i - ventana)..minOf(trozo.size - 1, i + ventana)) {
                suma += trozo[j]; n++
            }
            limpio[i] = (trozo[i] - suma / n).toFloat()
        }

        val media = limpio.average()
        val desv = sqrt(limpio.sumOf { (it - media) * (it - media) } / limpio.size)
        val umbral = desv * 0.6

        val picos = ArrayList<Int>()
        var ultimo = -99
        for (i in 1 until limpio.size - 1) {
            if (limpio[i] > umbral && limpio[i] >= limpio[i - 1] && limpio[i] >= limpio[i + 1]) {
                // Al menos 8 muestras de separacion (~0,25 s a 30 por segundo)
                if (i - ultimo > 8) { picos.add(i); ultimo = i }
            }
        }
        return Onda(limpio, picos.toIntArray())
    }

    data class Resultado(
        val pulsaciones: Int,
        val rmssd: Double,
        val latidos: Int,
        val calidad: Double,
        val ritmo: Ritmo,
        val irregularidad: Double,   // RMSSD relativo al intervalo medio
        val saltos: Double,          // proporcion de latidos que "dan un salto"
        val latidosPerdidos: Double  // proporcion de huecos que parecen latidos no vistos
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
        // 7. ¿SE ME HAN ESCAPADO LATIDOS?
        //
        // ESTE ES EL FILTRO MAS IMPORTANTE DE TODO EL ANALISIS, y nacio de
        // una lectura real que salio "49 pulsaciones e irregular".
        //
        // Si el dedo esta flojo o se mueve, algun latido se pierde. Y un
        // latido perdido no deja un hueco cualquiera: deja un hueco que
        // mide JUSTO EL DOBLE, porque abarca dos latidos en vez de uno.
        //
        // Eso produce exactamente la firma que se vio: las pulsaciones
        // bajan (se cuentan menos latidos de los que hubo) y la
        // irregularidad se dispara (un hueco doble entre huecos normales
        // parece un desorden tremendo). O sea que un dedo mal apoyado se
        // disfraza de arritmia. Inaceptable en algo que va a decirle a una
        // persona mayor que vaya al medico.
        //
        // Buscando huecos que midan cerca del doble de lo normal se
        // distingue una cosa de la otra: en una arritmia de verdad los
        // intervalos son dispares SIN seguir ese patron de "el doble".
        val ordenados = intervalos.sorted()
        val mediana = ordenados[ordenados.size / 2]
        val sospechosos = intervalos.count { it > mediana * 1.7 && it < mediana * 2.4 }
        val propPerdidos = sospechosos.toDouble() / intervalos.size

        // 8. ¿LA VARIACION ES ORDENADA O ES UN CAOS?
        //
        // ESTE TROZO FALTABA Y ES EL QUE HA DADO UN SUSTO A UNA PERSONA
        // JOVEN Y SANA.
        //
        // Arriba, en el comentario de analizarRitmo, yo mismo escribi que
        // "un corazon sano tambien varia, pero varia con suavidad y
        // siguiendo la respiracion". Lo escribi y no lo programe. El
        // analisis solo miraba CUANTO varia, no COMO varia. Y resulta que
        // eso es justo lo que separa las dos cosas:
        //
        //   - Arritmia por fibrilacion: cada latido cae donde le da la
        //     gana. Los intervalos suben y bajan sin orden ninguno.
        //   - Corazon joven y sano: los intervalos se acortan al coger
        //     aire y se alargan al soltarlo. Sube, sube, sube, baja, baja,
        //     baja. Es una ONDA, no un desorden.
        //
        // Y aqui esta lo grave: esa onda es MAS marcada cuanto mas joven y
        // mejor de salud se esta. Se llama arritmia sinusal respiratoria y
        // es signo de un corazon en forma. O sea que mi codigo cogia la
        // señal de estar sano y la contaba como señal de estar enfermo.
        // Cuanto mas sano, mas alto el numero, mas probable el susto.
        //
        // Contando cuantas veces cambia de sentido la variacion se separan
        // las dos: en una onda de respiracion los cambios de sentido son
        // pocos (dos por respiracion); en el caos, casi en cada latido.
        var cambiosDeSentido = 0
        var comparaciones = 0
        for (i in 2 until intervalos.size) {
            val d1 = intervalos[i - 1] - intervalos[i - 2]
            val d2 = intervalos[i] - intervalos[i - 1]
            if (d1 != 0.0 && d2 != 0.0) {
                comparaciones++
                if ((d1 > 0) != (d2 > 0)) cambiosDeSentido++
            }
        }
        val desorden = if (comparaciones >= 10)
            cambiosDeSentido.toDouble() / comparaciones else -1.0

        val ritmo = analizarRitmo(
            irregularidad, propSaltos, intervalos.size, calidad, propPerdidos, desorden
        )

        return Resultado(
            bpm, rmssd, intervalos.size + 1, calidad,
            ritmo, irregularidad, propSaltos, propPerdidos
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
        calidad: Double,
        latidosPerdidos: Double,
        desorden: Double
    ): Ritmo {
        // Con pocos latidos o con una lectura sucia no se opina: seria
        // irresponsable dar un susto por un dedo mal apoyado.
        if (nIntervalos < 20 || calidad < 0.5) return Ritmo.POCOS_DATOS

        // Y si hay pinta de que se han escapado latidos, TAMPOCO se opina.
        // Preferimos pedir que repita la medicion a mandar a alguien al
        // medico por un dedo flojo. Ver el punto 7 de calcular().
        if (latidosPerdidos > 0.10) return Ritmo.POCOS_DATOS

        val muyVariable = irregularidad > 0.12
        val muchosSaltos = saltos > 0.40

        // SI LA VARIACION SIGUE UNA ONDA, NO ES UNA ARRITMIA.
        //
        // Con la respiracion los intervalos suben varios latidos seguidos
        // y luego bajan varios seguidos: cambia de sentido dos veces por
        // respiracion, o sea en torno a un tercio de los latidos. En una
        // fibrilacion no hay onda ninguna y el sentido cambia casi cada
        // latido, por encima de la mitad.
        //
        // Por debajo de ese medio se considera respiracion y no se avisa
        // de nada, por muy grande que sea la variacion. Es la unica forma
        // de no confundir un corazon joven y sano con uno enfermo.
        val esRespiracion = desorden in 0.0..0.50

        return when {
            esRespiracion -> Ritmo.REGULAR
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
