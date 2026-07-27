package es.guiamayores.cuidame

import android.graphics.Color
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * LA VENTANA DE CONFIRMACION
 * ==========================
 *
 * Esta pantalla es lo que separa una app util de una que acaba
 * silenciada. Ningun detector acierta siempre: el movil se cae de la
 * mesa, alguien se sienta de golpe, se guarda el telefono de mala
 * manera. Si cada uno de esos casos mandara un aviso a la familia, en
 * tres dias nadie haria caso de los mensajes, y el dia que la persona se
 * cayera de verdad ese aviso se perderia entre el ruido.
 *
 * Por eso entre "creo que se ha caido" y "aviso a su hija" hay SIEMPRE un
 * minuto en el que la persona puede decir que esta bien.
 *
 * COMO ESTA PENSADA, PARA ALGUIEN MAYOR Y ASUSTADO
 *
 *   - Un solo boton, ocupando media pantalla. Nada de elegir entre
 *     opciones ni leer parrafos: quien acaba de caerse no esta para
 *     interpretar una interfaz.
 *   - Habla en voz alta. Si el movil se ha quedado a dos metros, la
 *     persona oye lo que esta pasando aunque no vea la pantalla.
 *   - Suena y vibra fuerte, aunque el movil este en silencio. Una alarma
 *     silenciosa no es una alarma.
 *   - Dice claramente cuanto queda. Sin sorpresas ni cuentas ocultas.
 *
 * Y si nadie contesta, el mensaje sale solo. Ese es el objetivo.
 */
class AlarmaActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_MOTIVO = "motivo"
        const val EXTRA_PRUEBA = "prueba"
        private const val SEGUNDOS = 60

        /**
         * Si la alarma esta ahora mismo en pantalla.
         *
         * El servicio necesita saberlo para no lanzar una alarma encima
         * de otra. Antes esto se resolvia con un temporizador fijo de 90
         * segundos, y era un fallo serio: aunque la persona pulsara
         * "estoy bien" al instante, la app se quedaba sorda minuto y
         * medio. Una segunda caida dentro de ese rato -que es justo
         * cuando mas probable es, porque quien se acaba de caer esta
         * mareado o inestable- no se detectaba.
         *
         * Con esto, en cuanto se cierra la pantalla vuelve a vigilar.
         */
        @Volatile
        var visible = false
            private set
    }

    private var voz: TextToSpeech? = null
    private var vozLista = false
    private var cuenta: CountDownTimer? = null
    private var vibrador: Vibrator? = null
    private var tono: android.media.Ringtone? = null
    private var yaResuelto = false

    private lateinit var titulo: TextView
    private lateinit var reloj: TextView
    private lateinit var explicacion: TextView

    private val motivo: String get() = intent.getStringExtra(EXTRA_MOTIVO) ?: "puede haberse caído"
    private val esPrueba: Boolean get() = intent.getBooleanExtra(EXTRA_PRUEBA, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        visible = true
        mostrarAunqueEsteBloqueado()

        voz = TextToSpeech(this, this)
        construirPantalla()
        empezarRuido()
        empezarCuenta()
    }

    /** Que salga con la pantalla apagada y el movil bloqueado en el bolsillo. */
    private fun mostrarAunqueEsteBloqueado() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun construirPantalla() {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#B3261E"))
            setPadding(40, 60, 40, 60)
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        titulo = TextView(this).apply {
            text = if (esPrueba) "ESTO ES UNA PRUEBA" else "¿Está usted bien?"
            textSize = 40f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        explicacion = TextView(this).apply {
            text = if (esPrueba)
                "No se va a avisar a nadie.\nAsí es como se vería de verdad."
            else
                "Si no contesta, avisaré a ${Ajustes(this@AlarmaActivity).nombreContacto.ifBlank { "su contacto" }}."
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }

        reloj = TextView(this).apply {
            text = SEGUNDOS.toString()
            textSize = 72f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        // El boton grande: ocupa media pantalla a proposito. Alguien que
        // acaba de caerse no puede afinar la punteria con el dedo.
        val estoyBien = Button(this).apply {
            text = "ESTOY BIEN"
            textSize = 34f
            setTextColor(Color.parseColor("#0B7A3B"))
            setBackgroundColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 420
            ).apply { topMargin = 40 }
            setOnClickListener { estoyBien() }
        }

        val avisarYa = Button(this).apply {
            text = "Avisar ahora"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7A1A15"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 160
            ).apply { topMargin = 30 }
            setOnClickListener { avisar() }
        }

        raiz.addView(titulo)
        raiz.addView(explicacion)
        raiz.addView(reloj)
        raiz.addView(estoyBien)
        raiz.addView(avisarYa)
        setContentView(raiz)
    }

    private fun empezarRuido() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            tono = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                }
                play()
            }
            // Sube el volumen de alarma: si el movil esta en silencio, una
            // alarma que no se oye no sirve para nada.
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
            )
        } catch (e: Exception) {}

        try {
            vibrador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val patron = longArrayOf(0, 600, 400, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrador?.vibrate(VibrationEffect.createWaveform(patron, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrador?.vibrate(patron, 0)
            }
        } catch (e: Exception) {}
    }

    private fun empezarCuenta() {
        cuenta = object : CountDownTimer(SEGUNDOS * 1000L, 1000L) {
            override fun onTick(quedan: Long) {
                val s = (quedan / 1000).toInt()
                reloj.text = s.toString()
                // Se repite la pregunta en voz alta cada 15 segundos: si la
                // persona esta aturdida, una sola vez no basta.
                if (s % 15 == 0 && s > 0) hablar("¿Está usted bien? Toque el botón blanco.")
            }
            override fun onFinish() { avisar() }
        }.start()
    }

    private fun estoyBien() {
        if (yaResuelto) return
        yaResuelto = true
        parar()
        hablar("De acuerdo. No aviso a nadie. Me quedo vigilando.")
        titulo.text = "Muy bien"
        explicacion.text = "No he avisado a nadie.\nSigo aquí."
        reloj.text = ""
        android.os.Handler(mainLooper).postDelayed({ finish() }, 2600)
    }

    private fun avisar() {
        if (yaResuelto) return
        yaResuelto = true
        parar()

        if (esPrueba) {
            titulo.text = "Fin de la prueba"
            explicacion.text = "Aquí es donde habría salido el mensaje.\nNo se ha enviado nada."
            reloj.text = ""
            hablar("Fin de la prueba. No he avisado a nadie.")
            android.os.Handler(mainLooper).postDelayed({ finish() }, 3500)
            return
        }

        val texto = Avisador.mensajeEmergencia(this, motivo)
        val fallo = Avisador.enviar(this, texto)

        if (fallo == null) {
            val a = Ajustes(this)
            titulo.text = "Aviso enviado"
            explicacion.text = "He avisado a ${a.nombreContacto.ifBlank { "su contacto" }}.\nLa ayuda va en camino."
            hablar("He avisado a su contacto. Quédese tranquilo, la ayuda va en camino.")
        } else {
            titulo.text = "No he podido avisar solo"
            explicacion.text = "$fallo\n\nLe abro los mensajes con el aviso ya escrito: " +
                               "solo tiene que pulsar enviar. Si no puede, llame al 112."
            hablar("No he podido mandar el mensaje solo. Le abro los mensajes con el aviso " +
                   "escrito: pulse enviar. Si no puede, llame al ciento doce.")
            // Plan B: al menos dejarlo escrito y listo para enviar.
            android.os.Handler(mainLooper).postDelayed({
                Avisador.abrirMensajeria(this, texto)
            }, 3000)
        }
        reloj.text = ""
        android.os.Handler(mainLooper).postDelayed({ finish() }, 12000)
    }

    private fun parar() {
        cuenta?.cancel()
        try { tono?.stop() } catch (e: Exception) {}
        try { vibrador?.cancel() } catch (e: Exception) {}
    }

    private fun hablar(frase: String) {
        if (!vozLista) return
        voz?.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "alarma")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = voz?.setLanguage(Locale("es", "ES"))
            vozLista = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            voz?.setSpeechRate(0.9f)
            hablar(
                if (esPrueba) "Esto es una prueba. ¿Está usted bien? Toque el botón blanco."
                else "¿Está usted bien? Toque el botón blanco si está bien."
            )
        }
    }

    /** El boton de atras no cierra esto: seria la forma mas facil de
     *  cancelar un aviso sin querer. Hay que decir "estoy bien". */
    override fun onBackPressed() { }

    override fun onDestroy() {
        visible = false
        parar()
        voz?.stop(); voz?.shutdown()
        super.onDestroy()
    }
}
