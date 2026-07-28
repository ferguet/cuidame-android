package es.guiamayores.cuidame

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.sqrt

/**
 * LA PRUEBA DEL EQUILIBRIO
 * ========================
 *
 * POR QUE ESTA PRUEBA Y NO OTRA
 *
 * Todo lo demas que hace esta app llega TARDE: la caida ya ha pasado y lo
 * unico que se puede hacer es pedir ayuda deprisa. Esto es lo contrario.
 * El equilibrio se deteriora meses antes de la primera caida, y quien no
 * aguanta de pie en tandem tiene bastantes mas papeletas de caerse que
 * quien aguanta. Medirlo permite hacer algo ANTES: fisioterapia, revisar
 * las medicinas que dan mareo, quitar alfombras, poner un asidero.
 *
 * Y no es un invento mio. Es la parte de equilibrio del SPPB (Short
 * Physical Performance Battery), la prueba que se usa en geriatria desde
 * hace treinta años: tres posturas cada vez mas dificiles, diez segundos
 * cada una, y una puntuacion de 0 a 4. La ventaja de usar una escala que
 * ya existe es que el numero significa algo fuera de esta app: un medico
 * sabe leerlo sin que nadie le explique nada.
 *
 * QUE APORTA EL MOVIL QUE NO APORTA UN CRONOMETRO
 *
 * El SPPB solo mira si aguanta o no aguanta. El movil, ademas, mide el
 * BALANCEO: cuanto se bambolea el cuerpo mientras aguanta. Y ese dato es
 * mas fino, porque cambia mucho antes que el "aguanta o no aguanta".
 * Alguien puede seguir aguantando los diez segundos durante años mientras
 * su balanceo se dobla; eso no se ve a ojo y aqui si.
 *
 * LO QUE MAS ME PREOCUPA DE ESTA PANTALLA
 *
 * Es la unica prueba de la app que PIDE A UNA PERSONA MAYOR QUE SE PONGA
 * EN UNA POSTURA INESTABLE. O sea, que la propia prueba puede provocar
 * exactamente lo que la app intenta evitar. Por eso el aviso de seguridad
 * no es un parrafito al final: es una pantalla entera que hay que pasar,
 * dice que se ponga al lado de algo donde agarrarse, y repite que agarrarse
 * no es hacer trampa. Una prueba de equilibrio que acaba en una caida no
 * mide nada, solo hace daño.
 */
class EquilibrioActivity : AppCompatActivity(), SensorEventListener {

    private class Postura(
        val nombre: String,
        val explicacion: String,
        val dibujo: Int,
        val segundos: Int
    )

    private val posturas = listOf(
        Postura(
            "Pies juntos",
            "Ponga los dos pies juntos, uno al lado del otro, tocándose.",
            0, 10
        ),
        Postura(
            "Un pie medio adelantado",
            "Adelante un pie hasta que su talón toque el lado del dedo gordo del otro.",
            1, 10
        ),
        Postura(
            "Un pie delante del otro",
            "Ponga un pie justo delante del otro, en línea: el talón de delante tocando la punta de detrás.",
            2, 10
        )
    )

    private lateinit var sensores: SensorManager
    private var acelerometro: Sensor? = null
    private var voz: TextToSpeech? = null
    private val reloj = Handler(Looper.getMainLooper())

    private lateinit var raiz: LinearLayout

    private var fase = 0            // 0 aviso, 1 preparado, 2 midiendo, 3 final
    private var indice = 0          // que postura toca
    private var midiendo = false
    private var inicioMedida = 0L
    private var aguantados = IntArray(3) { -1 }   // decimas aguantadas
    private var balanceos = FloatArray(3) { -1f }

    // Filtro para separar la gravedad del movimiento propio.
    private val gravedad = FloatArray(3)
    private var hayGravedad = false
    private var sumaBalanceo = 0.0
    private var muestrasBalanceo = 0
    private var seguidasFuertes = 0

    /**
     * A partir de aqui se considera que ha tenido que mover el pie o
     * agarrarse. Un cuerpo que se bambolea de pie no pasa de 1,5 m/s2 ni
     * de lejos; dar un paso o echar la mano a la pared da un tiron muy por
     * encima. Se piden tres lecturas seguidas para que un golpe suelto
     * -alguien que roza el movil- no corte la prueba.
     */
    private val umbralPaso = 3.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensores = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensores.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        voz = TextToSpeech(this) { estado ->
            if (estado == TextToSpeech.SUCCESS) {
                try { voz?.language = Locale("es", "ES") } catch (e: Exception) {}
            }
        }

        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 56, 48, 56)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101828"))
            addView(raiz)
        })

        pintarAviso()
    }

    override fun onPause() {
        super.onPause()
        pararMedida()
        try { sensores.unregisterListener(this) } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        reloj.removeCallbacksAndMessages(null)
        try { voz?.stop(); voz?.shutdown() } catch (e: Exception) {}
    }

    // ---------------------------------------------------------------
    //  PANTALLA 1: EL AVISO DE SEGURIDAD
    // ---------------------------------------------------------------

    private fun pintarAviso() {
        raiz.removeAllViews()
        raiz.addView(texto("Prueba del equilibrio", 34f, Color.WHITE, true))
        raiz.addView(texto(
            "Aguantar de pie en tres posturas, diez segundos cada una. " +
            "El móvil mide cuánto se balancea.",
            18f, Color.parseColor("#9AA4B2")
        ))

        raiz.addView(hueco(24))
        raiz.addView(tarjeta(
            "⚠️  ANTES DE EMPEZAR\n\n" +
            "Póngase al lado de una pared, de una mesa o del respaldo de una silla, " +
            "algo firme donde poder agarrarse.\n\n" +
            "Si nota que se va, AGÁRRESE. No es hacer trampa: la prueba se para sola " +
            "y eso ya es un resultado.\n\n" +
            "Mejor si hay alguien en casa mientras la hace.",
            "#7A3B10", 19f
        ))

        raiz.addView(hueco(20))
        raiz.addView(texto(
            "Métase el móvil en el bolsillo del pantalón, o sujételo contra el pecho " +
            "con una mano, dejando la otra libre para agarrarse.",
            17f, Color.parseColor("#9AA4B2")
        ))

        raiz.addView(boton("EMPEZAR", "#0B7A3B") {
            if (acelerometro == null) {
                raiz.addView(texto(
                    "Este móvil no tiene acelerómetro: no puede medir el equilibrio.",
                    18f, Color.parseColor("#F87171")
                ))
                return@boton
            }
            indice = 0
            aguantados = IntArray(3) { -1 }
            balanceos = FloatArray(3) { -1f }
            pintarPreparado()
        })

        raiz.addView(hueco(20))
        raiz.addView(texto(
            "Esto no diagnostica nada. Es una forma de ver si el equilibrio empeora " +
            "con el tiempo, que es la señal que avisa de las caídas antes de que ocurran.",
            14f, Color.parseColor("#6C7689")
        ))
    }

    // ---------------------------------------------------------------
    //  PANTALLA 2: COLOCARSE
    // ---------------------------------------------------------------

    private fun pintarPreparado() {
        fase = 1
        val p = posturas[indice]
        raiz.removeAllViews()

        raiz.addView(texto("Postura ${indice + 1} de 3", 15f, Color.parseColor("#9AA4B2")))
        raiz.addView(texto(p.nombre, 30f, Color.WHITE, true))

        raiz.addView(DibujoPies(this, p.dibujo).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 520
            ).apply { topMargin = 24; bottomMargin = 12 }
        })

        raiz.addView(texto(p.explicacion, 21f, Color.WHITE))
        raiz.addView(texto(
            "Los brazos, sueltos a los lados. Mire a un punto fijo de la pared, de frente.",
            17f, Color.parseColor("#9AA4B2")
        ))

        hablar("${p.nombre}. ${p.explicacion}")

        raiz.addView(boton("YA ESTOY COLOCADO", "#0B7A3B") { arrancarMedida() })
        raiz.addView(boton("No puedo con esta postura", "#7A1A15") {
            aguantados[indice] = 0
            balanceos[indice] = -1f
            terminarSerie()
        })
    }

    // ---------------------------------------------------------------
    //  PANTALLA 3: LOS DIEZ SEGUNDOS
    // ---------------------------------------------------------------

    private lateinit var cuenta: TextView
    private lateinit var pista: TextView

    private fun arrancarMedida() {
        fase = 2
        val p = posturas[indice]
        raiz.removeAllViews()

        raiz.addView(texto(p.nombre, 22f, Color.parseColor("#9AA4B2")))
        raiz.addView(texto("Quieto, sin moverse", 30f, Color.WHITE, true))

        cuenta = texto("${p.segundos}", 130f, Color.parseColor("#4ADE80"), true).apply {
            gravity = Gravity.CENTER
        }
        raiz.addView(cuenta)

        pista = texto("Si se va, agárrese: la prueba se para sola.", 18f,
            Color.parseColor("#9AA4B2"))
        raiz.addView(pista)

        raiz.addView(boton("PARAR", "#7A1A15") { cortar(true) })

        hayGravedad = false
        sumaBalanceo = 0.0
        muestrasBalanceo = 0
        seguidasFuertes = 0
        midiendo = true
        inicioMedida = System.currentTimeMillis()

        acelerometro?.let {
            sensores.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        vibrar(120)
        hablar("Empiece")
        reloj.post(tic)
    }

    private val tic = object : Runnable {
        override fun run() {
            if (!midiendo) return
            val p = posturas[indice]
            val pasado = System.currentTimeMillis() - inicioMedida
            val quedan = p.segundos - (pasado / 1000f)

            if (quedan <= 0f) {
                cortar(false)
                return
            }
            cuenta.text = String.format("%.0f", kotlin.math.ceil(quedan))
            reloj.postDelayed(this, 100)
        }
    }

    /** @param fallo true si ha tenido que moverse o agarrarse. */
    private fun cortar(fallo: Boolean) {
        if (!midiendo) return
        pararMedida()

        val decimas = ((System.currentTimeMillis() - inicioMedida) / 100L).toInt()
        val tope = posturas[indice].segundos * 10
        aguantados[indice] = if (fallo) minOf(decimas, tope) else tope
        balanceos[indice] =
            if (muestrasBalanceo >= 20) sqrt(sumaBalanceo / muestrasBalanceo).toFloat() else -1f

        vibrar(if (fallo) 500 else 250)
        hablar(if (fallo) "Ya está, no pasa nada" else "Muy bien")
        terminarSerie()
    }

    private fun pararMedida() {
        midiendo = false
        reloj.removeCallbacks(tic)
        try { sensores.unregisterListener(this) } catch (e: Exception) {}
    }

    /**
     * DECIDE SI SE SIGUE O SE PARA.
     *
     * El SPPB no hace las tres posturas siempre: si alguien no aguanta la
     * facil, ponerle la dificil solo sirve para arriesgarse a que se caiga
     * delante de uno. Asi que si falla una, se para ahi. Eso ademas no
     * pierde informacion: fallar la facil ya determina la puntuacion.
     */
    private fun terminarSerie() {
        val aguantoEntero = aguantados[indice] >= posturas[indice].segundos * 10
        if (!aguantoEntero || indice >= posturas.size - 1) {
            pintarResultado()
        } else {
            indice++
            pintarPreparado()
        }
    }

    // ---------------------------------------------------------------
    //  PANTALLA 4: EL RESULTADO
    // ---------------------------------------------------------------

    private fun pintarResultado() {
        fase = 3
        raiz.removeAllViews()

        val puntos = puntuar()
        val balanceo = balanceos.filter { it >= 0f }.minOrNull() ?: -1f

        raiz.addView(texto("Resultado", 32f, Color.WHITE, true))

        val (color, titulo, explicacion) = when (puntos) {
            4 -> Triple("#0B7A3B", "Equilibrio bueno",
                "Ha aguantado las tres posturas, incluida la más difícil, los diez segundos.")
            3 -> Triple("#0B7A3B", "Equilibrio aceptable",
                "Aguanta bien las dos primeras y algo la difícil. Es lo normal a cierta edad.")
            2 -> Triple("#B45309", "Equilibrio algo justo",
                "Aguanta las posturas fáciles pero la difícil se le va enseguida.")
            1 -> Triple("#B45309", "Equilibrio flojo",
                "Solo aguanta con los pies juntos. Conviene comentarlo en la próxima consulta.")
            else -> Triple("#7A1A15", "Equilibrio muy flojo",
                "No ha aguantado ni con los pies juntos. Esto conviene contarlo al médico, " +
                "y merece la pena revisar la casa: alfombras, cables, luz por la noche.")
        }

        raiz.addView(tarjeta("$puntos de 4\n\n$titulo", color, 30f))
        raiz.addView(texto(explicacion, 19f, Color.WHITE))

        if (balanceo >= 0f) {
            raiz.addView(hueco(20))
            raiz.addView(texto("CUÁNTO SE HA BALANCEADO", 13f, Color.parseColor("#9AA4B2")))
            raiz.addView(texto(String.format("%.2f", balanceo), 40f, Color.WHITE, true))
            raiz.addView(texto(
                "Este número no se compara con nadie: se compara consigo mismo. " +
                "Lo que importa es si dentro de unos meses ha subido.",
                16f, Color.parseColor("#9AA4B2")
            ))
        }

        raiz.addView(hueco(16))
        raiz.addView(texto(detalleTexto(), 16f, Color.parseColor("#9AA4B2")))

        // AL HISTORIAL SOLO SI LA PRUEBA VALE.
        //
        // Si no llego a medirse balanceo en ninguna postura es que la
        // prueba se corto enseguida o el movil no estaba encima. Guardar
        // eso ensuciaria el historial y podria hacer creer que el
        // equilibrio ha empeorado cuando lo que fallo fue la medicion.
        val valida = aguantados.any { it > 0 }
        if (valida) {
            Historial.añadir(
                this, "Equilibrio", "$puntos de 4 · $titulo",
                detalleTexto().replace("\n", " · ") +
                    (if (balanceo >= 0f) " · balanceo " + String.format("%.2f", balanceo) else "")
            )
            raiz.addView(texto("✅ Guardado en Mis mediciones", 16f, Color.parseColor("#4ADE80")))
        } else {
            raiz.addView(texto(
                "No se guarda: la prueba se cortó demasiado pronto para medir nada.",
                16f, Color.parseColor("#FBBF24")
            ))
        }

        raiz.addView(boton("REPETIR LA PRUEBA", "#1D4ED8") { pintarAviso() })
        raiz.addView(boton("SALIR", "#334155") { finish() })

        hablar("$puntos de 4. $titulo")
    }

    /**
     * LA PUNTUACION DEL SPPB, TAL CUAL.
     *
     * No la he inventado ni la he ajustado a ojo: son los cortes de la
     * escala original. Respetarlos es lo que hace que el numero se pueda
     * enseñar en una consulta y signifique lo mismo que alli.
     */
    private fun puntuar(): Int {
        val juntos = aguantados[0] >= 100
        val semi = aguantados[1] >= 100
        val tandem = aguantados[2]

        if (!juntos) return 0
        if (!semi) return 1
        return when {
            tandem >= 100 -> 4
            tandem >= 30 -> 3
            else -> 2
        }
    }

    private fun detalleTexto(): String {
        val sb = StringBuilder()
        for (i in posturas.indices) {
            val d = aguantados[i]
            if (d < 0) continue
            sb.append(posturas[i].nombre).append(": ")
            sb.append(String.format("%.1f", d / 10f)).append(" s")
            if (d >= posturas[i].segundos * 10) sb.append(" (completo)")
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    // ---------------------------------------------------------------
    //  EL SENSOR
    // ---------------------------------------------------------------

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onSensorChanged(e: SensorEvent?) {
        if (e == null || !midiendo) return

        // PRIMERO SE SEPARA LA GRAVEDAD DEL MOVIMIENTO.
        //
        // El acelerometro mide las dos cosas mezcladas, y la gravedad es
        // veinte veces mayor que el bamboleo que buscamos. Un filtro lento
        // deja pasar solo la gravedad (que apenas cambia), y restandola
        // queda el movimiento del cuerpo limpio. Sin esto, el numero
        // dependeria sobre todo de la inclinacion del bolsillo.
        if (!hayGravedad) {
            gravedad[0] = e.values[0]; gravedad[1] = e.values[1]; gravedad[2] = e.values[2]
            hayGravedad = true
            return
        }
        val a = 0.92f
        for (i in 0..2) gravedad[i] = a * gravedad[i] + (1 - a) * e.values[i]

        val lx = e.values[0] - gravedad[0]
        val ly = e.values[1] - gravedad[1]
        val lz = e.values[2] - gravedad[2]
        val magnitud = sqrt(lx * lx + ly * ly + lz * lz)

        // Se descarta el primer medio segundo: el gesto de soltar el movil
        // o de terminar de colocarse todavia esta dentro.
        if (System.currentTimeMillis() - inicioMedida > 500L) {
            sumaBalanceo += (magnitud * magnitud).toDouble()
            muestrasBalanceo++
        }

        if (magnitud > umbralPaso) {
            seguidasFuertes++
            if (seguidasFuertes >= 3) cortar(true)
        } else {
            seguidasFuertes = 0
        }
    }

    // ---------------------------------------------------------------

    private fun hablar(s: String) {
        try {
            voz?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "eq")
        } catch (e: Exception) {}
    }

    private fun vibrar(ms: Long) {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, 255))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        } catch (e: Exception) {}
    }

    // ---------------------------------------------------------------

    private fun texto(t: String, tam: Float, color: Int, negrita: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = tam; setTextColor(color)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }

    private fun tarjeta(t: String, colorHex: String, tam: Float) = TextView(this).apply {
        text = t
        textSize = tam
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(34, 34, 34, 34)
        background = GradientDrawable().apply {
            setColor(Color.parseColor(colorHex)); cornerRadius = 26f
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 }
    }

    private fun boton(t: String, colorHex: String, alPulsar: () -> Unit) = Button(this).apply {
        text = t
        textSize = 21f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(Color.parseColor(colorHex)); cornerRadius = 26f
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 180
        ).apply { topMargin = 26 }
        setOnClickListener { alPulsar() }
    }

    private fun hueco(alto: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, alto)
    }
}
