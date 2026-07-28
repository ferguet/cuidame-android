package es.guiamayores.cuidame

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
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

        /** "caida" o "inmovilidad". Cambian el tiempo de espera y si se
         *  puede cancelar sola al mover el movil. */
        const val EXTRA_TIPO = "tipo"
        const val TIPO_INMOVILIDAD = "inmovilidad"

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
    private var yaResuelto = false

    private lateinit var titulo: TextView
    private lateinit var reloj: TextView
    private lateinit var explicacion: TextView
    private lateinit var raiz: LinearLayout
    private lateinit var botonGrande: Button

    private val motivo: String get() = intent.getStringExtra(EXTRA_MOTIVO) ?: "puede haberse caído"
    private val esPrueba: Boolean get() = intent.getBooleanExtra(EXTRA_PRUEBA, false)
    private val esInmovilidad: Boolean
        get() = intent.getStringExtra(EXTRA_TIPO) == TIPO_INMOVILIDAD

    /**
     * CUANTO SE ESPERA ANTES DE AVISAR, SEGUN LO QUE HAYA PASADO.
     *
     * Un minuto para una caida: la persona esta al lado del movil y cada
     * segundo cuenta.
     *
     * Tres minutos si lo que pasa es que el movil lleva horas quieto. Ahi
     * la situacion es completamente distinta: lo mas probable no es que
     * haya pasado algo, sino que el movil se haya quedado olvidado en otra
     * habitacion. La persona tiene que oirlo, levantarse, buscarlo y
     * llegar hasta el. Un minuto no da para eso, y avisar a la familia
     * porque alguien tardo noventa segundos en encontrar su propio movil
     * es la clase de falsa alarma que hace que se desinstale la app.
     */
    private val segundos: Int get() = if (esInmovilidad) 180 else 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        visible = true
        mostrarAunqueEsteBloqueado()

        voz = TextToSpeech(this, this)
        construirPantalla()
        empezarRuido()
        empezarParpadeo()
        if (esInmovilidad) escucharMovimiento()
        empezarCuenta()
    }

    // ---------------------------------------------------------------
    //  LA PANTALLA COMO BALIZA: PARPADEO AZUL Y ROJO
    // ---------------------------------------------------------------
    //
    // De noche, una persona en el suelo de un pasillo a oscuras es
    // practicamente invisible desde la puerta. Una pantalla fija apenas
    // llama la atencion; una que parpadea en azul y rojo se ve desde el
    // otro extremo de la casa, se reconoce al instante como "algo va mal"
    // y sirve de guia para llegar hasta ella. Es exactamente para lo que
    // usan esos dos colores las ambulancias.
    //
    // Va a poco mas de un parpadeo por segundo, y eso es deliberado: las
    // luces que parpadean por encima de tres veces por segundo pueden
    // provocar crisis a personas con epilepsia fotosensible. Una baliza
    // que se ve igual de bien yendo despacio no tiene ninguna razon para
    // ir deprisa.
    //
    // Y sigue parpadeando DESPUES de mandar el aviso, que es cuando de
    // verdad hace falta: para entonces la persona puede estar inconsciente
    // y lo que importa es que quien llegue la encuentre cuanto antes.

    private val pintor = android.os.Handler(android.os.Looper.getMainLooper())
    private var enRojo = true
    private var parpadeando = false

    private val parpadeo = object : Runnable {
        override fun run() {
            if (!parpadeando) return
            enRojo = !enRojo
            raiz.setBackgroundColor(
                if (enRojo) Color.parseColor("#B3261E") else Color.parseColor("#1D4ED8")
            )
            pintor.postDelayed(this, 450)
        }
    }

    private fun empezarParpadeo() {
        parpadeando = true
        // La pantalla al maximo de brillo: una baliza a media luz no se ve
        // desde la puerta, y en ese momento la bateria da igual.
        try {
            val p = window.attributes
            p.screenBrightness = 1f
            window.attributes = p
        } catch (e: Exception) {}
        pintor.postDelayed(parpadeo, 450)
    }

    private fun pararParpadeo(colorFinal: String) {
        parpadeando = false
        pintor.removeCallbacks(parpadeo)
        try { raiz.setBackgroundColor(Color.parseColor(colorFinal)) } catch (e: Exception) {}
    }

    // ---------------------------------------------------------------
    //  SI ALGUIEN COGE EL MOVIL, SE CANCELA SOLO
    // ---------------------------------------------------------------
    //
    // Solo para el aviso por falta de movimiento, y es la pieza que hace
    // que ese aviso sea usable. El movil suena porque lleva horas quieto;
    // si a los veinte segundos alguien lo coge, ya esta todo dicho: la
    // persona esta bien, ha oido el ruido y ha llegado hasta el. Pedirle
    // ademas que acierte a pulsar un boton es poner una barrera donde ya
    // hay una respuesta.
    //
    // En una caida NO se hace esto: ahi el movil se mueve porque la
    // persona esta en el suelo removiendose, y cancelar por eso seria
    // justo lo contrario de lo que hay que hacer. Ahi hay que pulsar.

    private var sensores: android.hardware.SensorManager? = null
    private var oyente: android.hardware.SensorEventListener? = null
    private var seguidas = 0

    private fun escucharMovimiento() {
        try {
            val sm = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager
            val acc = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
            oyente = object : android.hardware.SensorEventListener {
                override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
                override fun onSensorChanged(e: android.hardware.SensorEvent?) {
                    if (e == null || yaResuelto) return
                    val f = kotlin.math.sqrt(
                        e.values[0] * e.values[0] + e.values[1] * e.values[1] +
                        e.values[2] * e.values[2]
                    )
                    // Coger un movil de la mesa da tirones muy por encima de
                    // esto. Se piden varias lecturas seguidas para que un
                    // portazo o un golpe en el mueble no lo cancelen.
                    if (kotlin.math.abs(f - 9.81f) > 3.0f) {
                        seguidas++
                        if (seguidas >= 4) cogido()
                    } else seguidas = 0
                }
            }
            sm.registerListener(oyente, acc, android.hardware.SensorManager.SENSOR_DELAY_GAME)
            sensores = sm
        } catch (e: Exception) {}
    }

    private fun cogido() {
        if (yaResuelto) return
        yaResuelto = true
        parar()
        pararParpadeo("#0B7A3B")
        if (!esPrueba) {
            Historial.añadir(this, "Sin movimiento",
                "sonó el aviso y cogió el móvil", motivo)
        }
        titulo.text = "Todo bien"
        explicacion.text = "Ha cogido el móvil, así que está usted bien.\nNo he avisado a nadie."
        reloj.text = ""
        hablar("Ya le he oído. No aviso a nadie. Sigo vigilando.")
        android.os.Handler(mainLooper).postDelayed({ finish() }, 2600)
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
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#B3261E"))
            setPadding(40, 60, 40, 60)
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        titulo = TextView(this).apply {
            text = when {
                esPrueba -> "ESTO ES UNA PRUEBA"
                esInmovilidad -> "¿Está usted ahí?"
                else -> "¿Está usted bien?"
            }
            textSize = 40f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        explicacion = TextView(this).apply {
            val aQuien = Ajustes(this@AlarmaActivity).nombreContacto.ifBlank { "su contacto" }
            text = when {
                esPrueba -> "No se va a avisar a nadie.\nAsí es como se vería de verdad."
                esInmovilidad ->
                    "El móvil lleva horas sin moverse.\n\n" +
                    "Cójalo, o toque el botón blanco.\n\n" +
                    "Si no, avisaré a $aQuien."
                else -> "Si no contesta, avisaré a $aQuien."
            }
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }

        reloj = TextView(this).apply {
            text = segundos.toString()
            textSize = 72f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        // El boton grande: ocupa media pantalla a proposito. Alguien que
        // acaba de caerse no puede afinar la punteria con el dedo.
        botonGrande = Button(this).apply {
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
        raiz.addView(botonGrande)
        raiz.addView(avisarYa)
        setContentView(raiz)
    }

    /**
     * El ruido lo lleva Sirena, que es compartida con el servicio.
     *
     * Cuando el movil esta bloqueado, el servicio ya la ha puesto a sonar
     * antes de que esta pantalla exista -puede incluso que esta pantalla
     * no llegue a salir-. Aqui solo nos aseguramos de que suene, sin
     * duplicarla: si ya sonaba, Sirena no hace nada.
     */
    private fun empezarRuido() {
        Sirena.sonar(this)
    }

    private fun empezarCuenta() {
        cuenta = object : CountDownTimer(segundos * 1000L, 1000L) {
            override fun onTick(quedan: Long) {
                val s = (quedan / 1000).toInt()
                reloj.text = s.toString()
                // Se repite la pregunta en voz alta cada 15 segundos: si la
                // persona esta aturdida, una sola vez no basta. Y si el
                // movil esta perdido por la casa, la voz repetida es lo que
                // permite ir siguiendo el sonido hasta encontrarlo.
                if (s % 15 == 0 && s > 0) {
                    hablar(
                        if (esInmovilidad) "¿Está usted ahí? Coja el móvil, por favor."
                        else "¿Está usted bien? Toque el botón blanco."
                    )
                }
            }
            override fun onFinish() { avisar() }
        }.start()
    }

    private fun estoyBien() {
        if (yaResuelto) return
        yaResuelto = true
        parar()
        pararParpadeo("#0B7A3B")

        // TREGUA DESPUES DE DECIR "ESTOY BIEN".
        //
        // Esto arregla un fallo que en la practica era insoportable: se
        // pulsaba "estoy bien", y a los pocos segundos volvia a saltar la
        // alarma. Y tenia toda la logica: alguien que acaba de decir que
        // esta bien se esta LEVANTANDO. Apoyarse, incorporarse, dejar el
        // movil en la mesilla... todo eso son golpes seguidos de quietud,
        // que es exactamente lo que el detector busca. La app perseguia a
        // la persona justo mientras se recuperaba.
        //
        // Medio minuto de tregua lo resuelve sin dejar a nadie
        // desprotegido: para que hiciera falta avisar en ese rato tendria
        // que haber una segunda caida en menos de treinta segundos, y aun
        // asi el aviso por falta de movimiento la acabaria cogiendo.
        ServicioVigilancia.tregua = System.currentTimeMillis() + 30_000L
        // Se anota igualmente, aunque diga que esta bien: una caida de la
        // que uno se levanta sigue siendo una caida, y si se repiten es
        // justo lo que hay que contarle al medico. Las caidas repetidas
        // son la señal de alarma, no la primera.
        if (!esPrueba) {
            Historial.añadir(this, "Caída", "detectada, dijo que estaba bien", motivo)
        }
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
            pararParpadeo("#334155")
            titulo.text = "Fin de la prueba"
            explicacion.text = "Aquí es donde habría salido el mensaje.\nNo se ha enviado nada."
            reloj.text = ""
            hablar("Fin de la prueba. No he avisado a nadie.")
            android.os.Handler(mainLooper).postDelayed({ finish() }, 3500)
            return
        }

        val texto = Avisador.mensajeEmergencia(this, motivo)
        val fallo = Avisador.enviar(this, texto)
        Historial.añadir(
            this, "Caída",
            if (fallo == null) "AVISO ENVIADO — no respondió" else "no respondió (no se pudo avisar)",
            motivo
        )

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

        // LA PANTALLA SIGUE PARPADEANDO, Y NO SE CIERRA.
        //
        // Antes esto se apagaba a los doce segundos, y era un error: el
        // momento en que la baliza hace mas falta es justo DESPUES de
        // avisar, mientras alguien viene de camino. Una persona en el
        // suelo de un pasillo a oscuras no se ve desde la puerta; una
        // pantalla parpadeando en azul y rojo se ve desde la calle si hay
        // una ventana, y guia hasta ella.
        //
        // Se queda encendida hasta que alguien la apaga a mano. El gasto
        // de bateria de una pantalla encendida no significa nada al lado
        // de que quien llegue tarde diez minutos en encontrar a la persona.
        //
        // Eso si: la vigilancia se reanuda YA. Dejar la pantalla puesta no
        // puede significar dejar de vigilar, porque entonces una segunda
        // caida mientras se espera a la ambulancia pasaria desapercibida.
        visible = false
        titulo.textSize = 30f
        botonGrande.text = "APAGAR ESTA LUZ"
        botonGrande.textSize = 26f
        botonGrande.setOnClickListener {
            pararParpadeo("#101828")
            finish()
        }
    }

    private fun parar() {
        cuenta?.cancel()
        Sirena.callar()
        // Se quita tambien el aviso de la barra: si no, se queda ahi
        // sonando visualmente cuando ya se ha resuelto.
        try {
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .cancel(ServicioVigilancia.ID_ALARMA)
        } catch (e: Exception) {}
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
        parparadeoFuera()
        parar()
        voz?.stop(); voz?.shutdown()
        super.onDestroy()
    }

    private fun parparadeoFuera() {
        parpadeando = false
        pintor.removeCallbacksAndMessages(null)
        try { oyente?.let { sensores?.unregisterListener(it) } } catch (e: Exception) {}
    }
}
