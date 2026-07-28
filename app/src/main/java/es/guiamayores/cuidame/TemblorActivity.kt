package es.guiamayores.cuidame

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MEDIR EL TEMBLOR DE LAS MANOS
 * =============================
 *
 * ¿PUEDE UN MOVIL MEDIR ESTO DE VERDAD? SI, Y CON MARGEN DE SOBRA.
 *
 * Merece la pena el numero, porque aqui no hay que fiarse de la intuicion.
 * La aceleracion de un movimiento que oscila vale amplitud por la
 * frecuencia al cuadrado (por dos pi). Un temblor de 5 veces por segundo
 * y solo 5 milimetros de recorrido da:
 *
 *     0,005 x (2 x 3,1416 x 5)^2  =  casi 5 m/s²
 *
 * O sea, MEDIA GRAVEDAD. El acelerometro de cualquier movil distingue
 * centesimas de m/s². No es que llegue justo: es que le sobran dos
 * ordenes de magnitud. Incluso un temblor de medio milimetro se mide sin
 * problema.
 *
 * Y en velocidad de muestreo tambien sobra: los temblores humanos van
 * entre 3 y 12 veces por segundo, y el sensor da 50 o mas. Hace falta el
 * doble de la frecuencia a medir, asi que vamos holgados.
 *
 * Esto no pasa siempre -con la muesca del pulso nos quedamos cortos por
 * culpa de los 30 fotogramas de la camara-, por eso conviene hacer la
 * cuenta antes de prometer nada.
 *
 * DOS POSTURAS, Y AHI ESTA LO INTERESANTE
 *
 * Medir "cuanto tiembla" a secas dice poco: casi todo el mundo tiembla un
 * poco, y mas con cafe, nervios, cansancio o segun que pastillas. Lo que
 * si distingue cosas es CUANDO tiembla:
 *
 *   - En reposo, con la mano muerta sobre las piernas.
 *   - Con el brazo estirado, sosteniendo el movil en el aire.
 *
 * El temblor que aparece sobre todo EN REPOSO y se calma al usar la mano
 * sigue un patron distinto del que aparece al estirar el brazo y no se ve
 * con la mano quieta. Los medicos usan justo esa diferencia. Nosotros no
 * diagnosticamos nada -eso necesita una exploracion entera, no un
 * telefono-, pero medir las dos posturas y enseñar cual sale peor es un
 * dato util que llevar a la consulta.
 */
class TemblorActivity : AppCompatActivity(), SensorEventListener, TextToSpeech.OnInitListener {

    private lateinit var sensores: SensorManager
    private var acelerometro: Sensor? = null

    private val muestras = ArrayList<Float>()
    private val instantes = ArrayList<Long>()

    private lateinit var titulo: TextView
    private lateinit var instruccion: TextView
    private lateinit var progreso: TextView
    private lateinit var resultado: TextView
    private lateinit var boton: Button

    private var voz: TextToSpeech? = null
    private var vozLista = false

    private val SEGUNDOS = 15
    private var fase = 0            // 0 parado, 1 reposo, 2 brazo estirado
    private var arrancado = 0L
    private var repReposo: Analisis? = null

    class Analisis(val frecuencia: Double, val fuerza: Double)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensores = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensores.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        voz = TextToSpeech(this, this)
        construir()
    }

    private fun construir() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 52, 44, 44)
            setBackgroundColor(Color.parseColor("#101828"))
        }

        titulo = t("Temblor de las manos", 30f, Color.WHITE, true)
        col.addView(titulo)

        instruccion = t(
            "Son dos pruebas de 15 segundos. Sujete el móvil con la mano que quiera " +
            "medir, sin apretar mucho, y siga lo que le vaya diciendo.",
            18f, Color.parseColor("#9AA4B2")
        )
        col.addView(instruccion)

        progreso = t("", 26f, Color.parseColor("#FBBF24"), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }
        col.addView(progreso)

        resultado = t("", 18f, Color.WHITE).apply { setPadding(24, 24, 24, 24) }
        col.addView(resultado)

        boton = Button(this).apply {
            text = "Empezar"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0B7A3B"))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 190
            ).apply { topMargin = 24 }
            setOnClickListener { if (fase == 0) empezarReposo() else parar() }
        }
        col.addView(boton)

        col.addView(t(
            "Temblar un poco es normal, y aumenta con el café, los nervios, el " +
            "cansancio, el frío y bastantes medicamentos. Esto NO diagnostica nada: " +
            "mide un movimiento y guarda el dato. Si le sale marcado varias veces, " +
            "enséñeselo a su médico y que lo valore él.",
            14f, Color.parseColor("#6C7689")
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101828"))
            addView(col)
        })

        if (acelerometro == null) {
            progreso.text = "Este móvil no tiene acelerómetro"
            boton.isEnabled = false
        }
    }

    private fun empezarReposo() {
        fase = 1
        muestras.clear(); instantes.clear()
        resultado.text = ""
        arrancado = System.currentTimeMillis()
        boton.text = "Parar"
        boton.setBackgroundColor(Color.parseColor("#7A1A15"))
        instruccion.text = "1 de 2 — EN REPOSO\n\nApoye la mano con el móvil sobre las " +
                           "piernas y déjela muerta. No lo sujete en el aire."
        hablar("Primera prueba. Apoye la mano con el móvil sobre las piernas " +
               "y déjela suelta, sin hacer fuerza.")
        registrar()
    }

    private fun empezarEstirado() {
        fase = 2
        muestras.clear(); instantes.clear()
        arrancado = System.currentTimeMillis()
        instruccion.text = "2 de 2 — BRAZO ESTIRADO\n\nEstire el brazo hacia delante " +
                           "sujetando el móvil en el aire, con la palma hacia abajo."
        hablar("Ahora estire el brazo hacia delante, sujetando el móvil en el aire.")
        registrar()
    }

    private fun registrar() {
        acelerometro?.let {
            sensores.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, 0)
        }
    }

    private fun parar() {
        fase = 0
        try { sensores.unregisterListener(this) } catch (e: Exception) {}
        boton.text = "Empezar"
        boton.setBackgroundColor(Color.parseColor("#0B7A3B"))
        progreso.text = ""
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onSensorChanged(e: SensorEvent?) {
        if (e == null || fase == 0) return
        val ahora = System.currentTimeMillis()
        val fuerza = sqrt(
            e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]
        )
        muestras.add(fuerza)
        instantes.add(ahora)

        val seg = ((ahora - arrancado) / 1000).toInt()
        progreso.text = "Midiendo… $seg de $SEGUNDOS segundos"

        if (seg >= SEGUNDOS) {
            try { sensores.unregisterListener(this) } catch (ex: Exception) {}
            val a = analizar()
            if (fase == 1) {
                repReposo = a
                progreso.text = "Primera hecha"
                android.os.Handler(mainLooper).postDelayed({ empezarEstirado() }, 3500)
            } else {
                mostrar(repReposo, a)
                parar()
            }
        }
    }

    /**
     * Busca a que velocidad oscila la mano y con cuanta fuerza.
     *
     * Se prueba cada frecuencia entre 3 y 12 veces por segundo y se mira
     * cual "encaja" mejor con lo medido. Es lo mismo que hace el oido al
     * distinguir una nota: buscar que ritmo se repite dentro del ruido.
     */
    private fun analizar(): Analisis {
        val n = muestras.size
        if (n < 100) return Analisis(0.0, 0.0)

        val segundos = (instantes.last() - instantes.first()) / 1000.0
        if (segundos < 5) return Analisis(0.0, 0.0)
        val porSegundo = n / segundos

        // Quitar la gravedad y la deriva lenta: solo interesa la vibracion.
        val media = muestras.average()
        val limpio = DoubleArray(n) { muestras[it] - media }

        var mejorF = 0.0
        var mejorP = 0.0
        var f = 3.0
        while (f <= 12.0) {
            var re = 0.0; var im = 0.0
            for (i in 0 until n) {
                val ang = 2.0 * PI * f * i / porSegundo
                re += limpio[i] * cos(ang)
                im += limpio[i] * sin(ang)
            }
            val poder = sqrt(re * re + im * im) / n
            if (poder > mejorP) { mejorP = poder; mejorF = f }
            f += 0.25
        }
        return Analisis(mejorF, mejorP)
    }

    private fun mostrar(reposo: Analisis?, estirado: Analisis) {
        // Umbral aproximado por debajo del cual es el temblor de fondo que
        // tenemos todos y no significa nada.
        val umbral = 0.08

        val tReposo = reposo?.fuerza ?: 0.0
        val tEstirado = estirado.fuerza
        val hay = tReposo > umbral || tEstirado > umbral

        val sb = StringBuilder()
        if (!hay) {
            sb.append("No se aprecia temblor por encima de lo normal.\n\n")
            sb.append("Todo el mundo tiene un temblor de fondo pequeñísimo; el suyo " +
                      "está dentro de eso.")
        } else {
            val cual = when {
                tReposo > tEstirado * 1.4 -> "Se aprecia más CON LA MANO EN REPOSO."
                tEstirado > tReposo * 1.4 -> "Se aprecia más CON EL BRAZO ESTIRADO."
                else -> "Se aprecia parecido en las dos posturas."
            }
            val f = if (tEstirado > tReposo) estirado.frecuencia else (reposo?.frecuencia ?: 0.0)
            sb.append("Se aprecia temblor.\n\n")
            sb.append(cual)
            sb.append("\n\nVelocidad: unas ${String.format("%.1f", f)} veces por segundo.\n\n")
            sb.append("Qué hacer con esto: repítalo otro día, tranquilo y sin café. " +
                      "Si vuelve a salir, enséñele estos datos a su médico —incluida " +
                      "la postura en la que sale peor, que para él es información útil—. " +
                      "Temblar tiene muchas causas y la mayoría no son graves.")
        }

        sb.append("\n\n(en reposo ${String.format("%.2f", tReposo)}, " +
                  "estirado ${String.format("%.2f", tEstirado)})")
        resultado.text = sb.toString()
        resultado.setBackgroundColor(
            if (hay) Color.parseColor("#3A2A10") else Color.parseColor("#14301F")
        )
        instruccion.text = "Terminado"
        hablar(if (hay) "Ya está. Se aprecia algo de temblor: si le sale varias veces, " +
                        "coménteselo a su médico."
               else "Ya está. No se aprecia temblor por encima de lo normal.")

        // AL HISTORIAL SOLO SI LA MEDICION VALE.
        // Sin las dos posturas hechas, el dato no dice nada util.
        if (reposo != null) {
            val resumen = if (hay)
                "se aprecia (${String.format("%.1f", if (tEstirado > tReposo) estirado.frecuencia else reposo.frecuencia)} veces/s)"
            else "sin temblor apreciable"
            Historial.añadir(
                this, "Temblor", resumen,
                "reposo ${String.format("%.2f", tReposo)} / estirado ${String.format("%.2f", tEstirado)}"
            )
        }
    }

    private fun hablar(f: String) {
        if (vozLista) voz?.speak(f, TextToSpeech.QUEUE_FLUSH, null, "temblor")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = voz?.setLanguage(Locale("es", "ES"))
            vozLista = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            voz?.setSpeechRate(0.88f)
        }
    }

    override fun onPause() {
        super.onPause()
        if (fase != 0) parar()
    }

    override fun onDestroy() {
        try { sensores.unregisterListener(this) } catch (e: Exception) {}
        voz?.stop(); voz?.shutdown()
        super.onDestroy()
    }

    private fun t(s: String, tam: Float, color: Int, negrita: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = tam; setTextColor(color)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }
}
