package es.guiamayores.cuidame

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * PANTALLA DE SENSORES EN VIVO
 * ============================
 *
 * Esta pantalla nace de un problema concreto: la deteccion de caidas no
 * saltaba en las pruebas y no habia forma de saber por que. ¿El golpe se
 * quedaba corto? ¿El movil seguia moviendose despues? ¿El sensor ni
 * siquiera estaba llegando? Con un aviso que sale o no sale, es imposible
 * distinguir esas tres cosas, y ajustar los numeros a ciegas es perder el
 * tiempo.
 *
 * Aqui se ve todo lo que el detector ve. Se deja el movil sobre la cama,
 * se tira al suelo, se hace lo que sea, y la pantalla dice exactamente
 * que fuerza ha medido y por que ha decidido lo que ha decidido.
 *
 * Es una herramienta de taller, no una pantalla para la persona mayor:
 * esta escondida detras de un boton en la pantalla de casa.
 */
class DiagnosticoActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensores: SensorManager
    private var acelerometro: Sensor? = null
    private val detector = DetectorCaida()

    private lateinit var fuerza: TextView
    private lateinit var pico: TextView
    private lateinit var estado: TextView
    private lateinit var detalle: TextView
    private lateinit var resultado: TextView
    private lateinit var umbral: TextView

    private var ultimoRefresco = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensores = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensores.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 56, 44, 44)
            setBackgroundColor(Color.parseColor("#101828"))
        }

        col.addView(t("Sensores en vivo", 30f, Color.WHITE, true))
        col.addView(t(
            "Deje el móvil quieto: debería marcar unos 9,8. Tírelo o cáigase y mire el pico.",
            15f, Color.parseColor("#9AA4B2")
        ))

        col.addView(t("FUERZA AHORA", 13f, Color.parseColor("#9AA4B2")))
        fuerza = t("—", 46f, Color.WHITE, true)
        col.addView(fuerza)

        col.addView(t("PICO MÁXIMO MEDIDO", 13f, Color.parseColor("#9AA4B2")))
        pico = t("—", 46f, Color.parseColor("#FBBF24"), true)
        col.addView(pico)

        // El umbral se calcula contra lo que ESTE movil puede medir, no
        // contra un numero fijo. Ver DetectorCaida.ajustarAlSensor.
        val rango = acelerometro?.maximumRange ?: 0f
        detector.ajustarAlSensor(rango)

        umbral = t("", 13f, Color.parseColor("#6C7689"))
        col.addView(umbral)

        col.addView(hueco(16))
        col.addView(t("LO MÁXIMO QUE PUEDE MEDIR ESTE MÓVIL", 13f, Color.parseColor("#9AA4B2")))
        col.addView(t(
            if (rango > 0f) String.format("%.1f", rango) + "   (unas " +
                String.format("%.1f", rango / 9.81f) + " veces la gravedad)"
            else "no se ha podido saber",
            24f,
            if (rango in 0.1f..25f) Color.parseColor("#F87171") else Color.parseColor("#4ADE80"),
            true
        ))
        col.addView(t(
            if (rango in 0.1f..25f)
                "Este sensor se topa pronto: por muy fuerte que sea el golpe, nunca dará " +
                "más de ese número. Por eso el umbral se ha bajado solo."
            else
                "Rango de sobra para medir un golpe fuerte.",
            14f, Color.parseColor("#6C7689")
        ))

        // ---- QUE SENSORES TIENE ESTE MOVIL ----
        //
        // Los moviles que usan las personas mayores suelen ser baratos o
        // viejos, y no todos traen los mismos sensores. Dar por hecho que
        // estan es justo el error que hace que una app "funcione en mi
        // movil" y no en el de la persona a la que iba dirigida. Peor
        // aun: la app parecería estar cuidando a alguien sin poder
        // hacerlo. Aqui se ve de un vistazo, sin instalar nada mas.
        col.addView(hueco(28))
        col.addView(t("QUÉ SENSORES TIENE ESTE MÓVIL", 13f, Color.parseColor("#9AA4B2")))
        col.addView(t(inventarioSensores(), 16f, Color.WHITE))
        col.addView(t(
            "Solo el acelerómetro es imprescindible. Los demás no hacen falta hoy, " +
            "pero saber si están dice qué se le puede pedir a este móvil más adelante.",
            14f, Color.parseColor("#6C7689")
        ))

        col.addView(hueco(24))
        col.addView(t("QUÉ ESTÁ HACIENDO EL DETECTOR", 13f, Color.parseColor("#9AA4B2")))
        estado = t("esperando", 20f, Color.WHITE, true)
        col.addView(estado)

        detalle = t("", 15f, Color.parseColor("#9AA4B2"))
        col.addView(detalle)

        resultado = t("Caídas detectadas: 0", 19f, Color.parseColor("#4ADE80"), true).apply {
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#14301F"))
        }
        col.addView(hueco(20))
        col.addView(resultado)

        col.addView(Button(this).apply {
            text = "Poner a cero"
            textSize = 19f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 150
            ).apply { topMargin = 26 }
            setOnClickListener {
                detector.reiniciarPico()
                detector.reiniciar()
                pintar()
            }
        })

        col.addView(t(
            "Si el pico no pasa de " + detector.umbralGolpe.toInt() +
            " al caerse, es que el golpe llega amortiguado y hay que bajar el umbral. " +
            "Si pasa pero pone \"se siguió moviendo\", es que hay que dejar el móvil " +
            "más quieto después del golpe, como estaría una persona que no puede levantarse.",
            14f, Color.parseColor("#6C7689")
        ))

        setContentView(android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101828"))
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        acelerometro?.let {
            sensores.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (acelerometro == null) {
            estado.text = "ESTE MÓVIL NO TIENE ACELERÓMETRO"
            estado.setTextColor(Color.parseColor("#F87171"))
        }
    }

    override fun onPause() {
        super.onPause()
        try { sensores.unregisterListener(this) } catch (e: Exception) {}
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent?) {
        if (e == null || e.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        detector.procesar(e.values[0], e.values[1], e.values[2])

        // La pantalla se refresca 10 veces por segundo, no 200: si no,
        // se pasa el rato dibujando y va a tirones.
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoRefresco > 100) {
            ultimoRefresco = ahora
            pintar()
        }
    }

    private fun pintar() {
        umbral.text = "HACE FALTA LLEGAR A " + String.format("%.1f", detector.umbralGolpe) +
                      " PARA QUE CUENTE COMO GOLPE"
        fuerza.text = String.format("%.1f", detector.fuerzaActual)
        pico.text = String.format("%.1f", detector.pico)
        estado.text = detector.estado

        val q = detector.quietudUltimoIntento
        detalle.text = buildString {
            if (detector.picoUltimoGolpe > 0f) {
                append("Último golpe: ")
                append(String.format("%.1f", detector.picoUltimoGolpe))
                append("\n")
            }
            if (q >= 0f) {
                append("Movimiento tras el golpe: ")
                append(String.format("%.2f", q))
                append("  (por debajo de 2,2 cuenta como quieto)")
            }
        }

        resultado.text = "Caídas detectadas: ${detector.caidasDetectadas}" +
            if (detector.intentosDescartados > 0)
                "\nGolpes descartados por movimiento: ${detector.intentosDescartados}"
            else ""
    }

    /**
     * Lista los sensores que interesan y si este movil los tiene.
     *
     * El acelerometro va marcado aparte porque es el unico del que
     * depende la app: sin el, "Cuídame" no puede detectar nada y hay que
     * decirlo sin rodeos en vez de dejar que parezca que vigila.
     */
    private fun inventarioSensores(): String {
        val mirar = listOf(
            Sensor.TYPE_ACCELEROMETER to "Acelerómetro (imprescindible)",
            Sensor.TYPE_GYROSCOPE to "Giroscopio (giro al caer)",
            Sensor.TYPE_PRESSURE to "Barómetro (en qué planta está)",
            Sensor.TYPE_STEP_COUNTER to "Contador de pasos",
            Sensor.TYPE_LIGHT to "Sensor de luz",
            Sensor.TYPE_PROXIMITY to "Sensor de proximidad",
            Sensor.TYPE_MAGNETIC_FIELD to "Brújula"
        )
        val sb = StringBuilder()
        for ((tipo, nombre) in mirar) {
            val hay = sensores.getDefaultSensor(tipo) != null
            sb.append(if (hay) "✅ " else "❌ ").append(nombre).append("\n")
        }
        val acc = sensores.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (acc == null) {
            sb.append("\n⚠️ SIN ACELERÓMETRO ESTA APP NO PUEDE FUNCIONAR.")
        } else {
            sb.append("\nModelo: ").append(acc.name)
        }
        return sb.toString().trim()
    }

    private fun t(s: String, tam: Float, color: Int, negrita: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = tam; setTextColor(color)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }

    private fun hueco(alto: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, alto)
    }
}
