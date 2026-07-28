package es.guiamayores.cuidame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * RESPIRACIÓN GUIADA
 * ==================
 *
 * Es lo primero de toda la app que HACE algo por la persona en vez de
 * limitarse a contarle como esta. Todo lo demas mide; esto trata.
 *
 * QUE HACE Y POR QUE FUNCIONA
 *
 * Marca el ritmo de una respiracion lenta, unas seis por minuto, muy por
 * debajo de las quince o veinte habituales. Respirar asi unos minutos
 * activa el freno del sistema nervioso -el nervio vago- y el cuerpo baja
 * revoluciones: el pulso se calma y la variabilidad entre latidos sube,
 * que es justo lo que mide la pantalla del pulso.
 *
 * EL ACIERTO ESTA EN GUIARLO CON VIBRACION
 *
 * Una persona mayor no va a seguir una animacion en la pantalla mientras
 * cierra los ojos y se relaja, ni va a estar leyendo instrucciones. Pero
 * seguir una vibracion en la mano no requiere ver, ni leer, ni entender
 * nada: el movil avisa cuando coger aire y cuando soltarlo. Se puede
 * hacer con los ojos cerrados, que es como se hace bien.
 *
 * Y SE PUEDE COMPROBAR
 *
 * Como el pulso y la variabilidad ya se miden en la otra pantalla, la
 * persona puede medirse antes y despues y ver su propio numero mejorar.
 * Eso convierte un consejo vago -"relajese"- en algo con resultado
 * visible, que es lo que hace que alguien repita.
 */
class RespiracionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // 4 segundos cogiendo aire y 6 soltando: ciclo de 10 segundos, o sea
    // seis respiraciones por minuto. Se suelta mas despacio de lo que se
    // coge a proposito, porque es la espiracion larga la que activa el
    // freno del sistema nervioso.
    private val msCoger = 4000L
    private val msSoltar = 6000L
    private val minutos = 3

    private lateinit var circulo: CirculoRespiracion
    private lateinit var instruccion: TextView
    private lateinit var restante: TextView
    private lateinit var boton: Button

    private var vibrador: Vibrator? = null
    private var voz: TextToSpeech? = null
    private var vozLista = false

    private var enMarcha = false
    private var arrancado = 0L
    private var ciclos = 0
    private val manos = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        voz = TextToSpeech(this, this)
        construir()
    }

    private fun construir() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 48, 44, 44)
            setBackgroundColor(Color.parseColor("#0B1220"))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        col.addView(t("Respiración guiada", 30f, Color.WHITE, true))
        col.addView(t(
            "Siéntese cómodo y sujete el móvil en la mano. Vibrará para decirle " +
            "cuándo coger aire y cuándo soltarlo. Puede cerrar los ojos: solo tiene " +
            "que seguir la vibración.",
            17f, Color.parseColor("#9AA4B2")
        ))

        circulo = CirculoRespiracion(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 620
            ).apply { topMargin = 20 }
        }
        col.addView(circulo)

        instruccion = t("", 34f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        col.addView(instruccion)

        restante = t("", 20f, Color.parseColor("#9AA4B2")).apply { gravity = Gravity.CENTER }
        col.addView(restante)

        boton = Button(this).apply {
            text = "Empezar"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0B7A3B"))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 190
            ).apply { topMargin = 24 }
            setOnClickListener { if (enMarcha) parar() else empezar() }
        }
        col.addView(boton)

        col.addView(t(
            "Si en algún momento se marea o se agobia, pare y respire normal. " +
            "Esto es un ejercicio de calma, no una prueba: no hay que aguantar nada.",
            14f, Color.parseColor("#6C7689")
        ))

        setContentView(android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1220"))
            addView(col)
        })
    }

    private fun empezar() {
        enMarcha = true
        ciclos = 0
        arrancado = System.currentTimeMillis()
        boton.text = "Parar"
        boton.setBackgroundColor(Color.parseColor("#7A1A15"))
        vibrador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        hablar("Vamos a respirar despacio. Coja aire cuando note la vibración " +
               "y suéltelo despacio cuando vibre dos veces.")
        manos.postDelayed({ cogerAire() }, 3500)
    }

    private fun cogerAire() {
        if (!enMarcha) return
        if (System.currentTimeMillis() - arrancado > minutos * 60_000L) { terminar(); return }

        ciclos++
        instruccion.text = "Coja aire…"
        instruccion.setTextColor(Color.parseColor("#60A5FA"))
        circulo.animar(true, msCoger)
        vibrar(longArrayOf(0, 350))
        // Solo se habla en los dos primeros ciclos: despues estorba mas
        // que ayuda, la idea es que se relaje, no que la escuche.
        if (ciclos <= 2) hablar("Coja aire")
        actualizarRestante()
        manos.postDelayed({ soltarAire() }, msCoger)
    }

    private fun soltarAire() {
        if (!enMarcha) return
        instruccion.text = "Suelte despacio…"
        instruccion.setTextColor(Color.parseColor("#4ADE80"))
        circulo.animar(false, msSoltar)
        vibrar(longArrayOf(0, 200, 150, 200))
        if (ciclos <= 2) hablar("Suelte despacio")
        manos.postDelayed({ cogerAire() }, msSoltar)
    }

    private fun actualizarRestante() {
        val quedan = minutos * 60 - ((System.currentTimeMillis() - arrancado) / 1000).toInt()
        restante.text = if (quedan > 0) "Quedan ${quedan / 60} min ${quedan % 60} s" else ""
    }

    private fun terminar() {
        enMarcha = false
        manos.removeCallbacksAndMessages(null)
        circulo.animar(false, 800)
        instruccion.text = "Muy bien"
        instruccion.setTextColor(Color.WHITE)
        restante.text = "Ahora vuelva a medirse el pulso y compare:\n" +
                        "suele bajar y la variabilidad suele subir."
        boton.text = "Empezar otra vez"
        boton.setBackgroundColor(Color.parseColor("#0B7A3B"))
        hablar("Muy bien. Ya está. Si quiere, vuelva a medirse el pulso y compare.")
    }

    private fun parar() {
        enMarcha = false
        manos.removeCallbacksAndMessages(null)
        try { vibrador?.cancel() } catch (e: Exception) {}
        circulo.animar(false, 600)
        instruccion.text = ""
        restante.text = ""
        boton.text = "Empezar"
        boton.setBackgroundColor(Color.parseColor("#0B7A3B"))
    }

    private fun vibrar(patron: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrador?.vibrate(VibrationEffect.createWaveform(patron, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrador?.vibrate(patron, -1)
            }
        } catch (e: Exception) {}
    }

    private fun hablar(f: String) {
        if (!vozLista) return
        voz?.speak(f, TextToSpeech.QUEUE_FLUSH, null, "resp")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = voz?.setLanguage(Locale("es", "ES"))
            vozLista = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            voz?.setSpeechRate(0.85f)
        }
    }

    override fun onPause() {
        super.onPause()
        if (enMarcha) parar()
    }

    override fun onDestroy() {
        manos.removeCallbacksAndMessages(null)
        voz?.stop(); voz?.shutdown()
        super.onDestroy()
    }

    private fun t(s: String, tam: Float, color: Int, negrita: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = tam; setTextColor(color)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 14, 0, 14)
        }
}

/**
 * El circulo que crece al coger aire y mengua al soltarlo.
 *
 * Es un apoyo, no lo principal: quien quiera puede cerrar los ojos y
 * guiarse solo por la vibracion. Pero a quien mire la pantalla le da algo
 * a lo que agarrarse, y ver el ritmo ayuda a cogerlo las primeras veces.
 */
class CirculoRespiracion(contexto: Context) : View(contexto) {

    private var fraccion = 0.25f          // 0 = pequeño, 1 = grande
    private var animando = false
    private var desde = 0.25f
    private var hasta = 0.25f
    private var inicio = 0L
    private var duracion = 1000L

    private val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    fun animar(creciendo: Boolean, ms: Long) {
        desde = fraccion
        hasta = if (creciendo) 1f else 0.25f
        duracion = ms
        inicio = System.currentTimeMillis()
        animando = true
        invalidate()
    }

    override fun onDraw(lienzo: Canvas) {
        super.onDraw(lienzo)
        if (animando) {
            val t = ((System.currentTimeMillis() - inicio).toFloat() / duracion).coerceIn(0f, 1f)
            // Suavizado: ni arranca ni termina de golpe, como la respiracion
            val suave = (1 - kotlin.math.cos(t * Math.PI).toFloat()) / 2f
            fraccion = desde + (hasta - desde) * suave
            if (t >= 1f) animando = false
        }

        val cx = width / 2f
        val cy = height / 2f
        val maximo = minOf(width, height) / 2f - 20f
        val radio = maximo * fraccion

        val creciendo = hasta > desde
        val color = if (creciendo) Color.parseColor("#60A5FA") else Color.parseColor("#4ADE80")

        relleno.color = (color and 0x00FFFFFF) or 0x33000000
        lienzo.drawCircle(cx, cy, radio, relleno)
        borde.color = color
        lienzo.drawCircle(cx, cy, radio, borde)

        if (animando) invalidate()
    }
}
