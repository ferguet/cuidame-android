package es.guiamayores.cuidame

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PULSO Y TENSIÓN CON LA CÁMARA
 * =============================
 *
 * Se enciende la linterna, la persona apoya el dedo en la camara, y de
 * los cambios de luz que atraviesan la carne salen los latidos. La
 * explicacion de la parte matematica esta en AnalizadorPulso.
 *
 * DOS DECISIONES QUE PARECEN DETALLES Y NO LO SON
 *
 * 1. SE BLOQUEA LA EXPOSICION AUTOMATICA. La camara, por su cuenta,
 *    intenta compensar cualquier cambio de luz. O sea que trabaja
 *    activamente para BORRAR justo lo que queremos medir. Por eso se
 *    espera unos segundos a que se estabilice con el dedo puesto y
 *    despues se le prohibe seguir ajustando.
 *
 * 2. NO SE ENSEÑA LA IMAGEN. No hace falta, y ademas es una camara
 *    apuntando a una persona: mejor que no haya ninguna vista previa que
 *    inquiete ni ningun sitio donde algo pueda quedarse guardado. De cada
 *    imagen solo se saca UN numero -el brillo medio- y la imagen se tira
 *    inmediatamente. No se graba nada, ni se guarda, ni se manda a
 *    ningun lado.
 */
class PulsoActivity : AppCompatActivity() {

    private val analizador = AnalizadorPulso()
    private var camara: CameraDevice? = null
    private var sesion: CameraCaptureSession? = null
    private var lector: ImageReader? = null
    private var hilo: HandlerThread? = null
    private var manos: Handler? = null

    private lateinit var titulo: TextView
    private lateinit var instruccion: TextView
    private lateinit var progreso: TextView
    private lateinit var resultado: TextView
    private lateinit var botonar: Button
    private lateinit var grafica: GraficaPulso
    private lateinit var aviso: TextView

    private var midiendo = false
    private var arrancado = 0L
    private var ultimoDibujo = 0L
    private var ultimoTexto = 0L
    private var exposicionBloqueada = false
    private var peticion: CaptureRequest.Builder? = null

    private val PERMISO_CAMARA = 77

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        construir()
    }

    private fun construir() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 56, 44, 44)
            setBackgroundColor(Color.parseColor("#101828"))
        }

        titulo = t("Pulso y tensión", 32f, Color.WHITE, true)
        col.addView(titulo)

        instruccion = t(
            "Apoye la yema del dedo sobre la cámara de atrás, tapándola entera, " +
            "sin apretar. Se encenderá la linterna. Quédese quieto y respire normal.",
            18f, Color.parseColor("#9AA4B2")
        )
        col.addView(instruccion)

        // La onda en directo. Se ve lo mismo que ve el analizador, y el
        // color del recuadro dice si el dedo esta quieto.
        grafica = GraficaPulso(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 420
            ).apply { topMargin = 24; bottomMargin = 12 }
        }
        col.addView(grafica)

        aviso = t("", 19f, Color.parseColor("#9AA4B2"), true).apply {
            gravity = Gravity.CENTER
            setPadding(20, 18, 20, 18)
        }
        col.addView(aviso)

        progreso = t("", 26f, Color.parseColor("#FBBF24"), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 22)
        }
        col.addView(progreso)

        resultado = t("", 22f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }
        col.addView(resultado)

        botonar = Button(this).apply {
            text = "Empezar"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0B7A3B"))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 190
            ).apply { topMargin = 26 }
            setOnClickListener { if (midiendo) parar(true) else empezar() }
        }
        col.addView(botonar)

        col.addView(t(
            "Esto NO es un aparato médico y no diagnostica nada.\n\n" +
            "El pulso suele salir bastante ajustado. La tensión es orientativa: sirve " +
            "para compararse consigo mismo en días distintos, no con otras personas.\n\n" +
            "Sobre el compás del corazón: es un aviso para que lo mire un médico, nunca " +
            "una conclusión. Puede avisar sin que pase nada, y también puede no avisar " +
            "habiendo algo. Si le sale irregular, no se asuste: pida cita y pida un " +
            "electrocardiograma, que es lo único que lo dice de verdad.\n\n" +
            "Y si en algún momento se encuentra mal —dolor en el pecho, ahogo, mareo—, " +
            "eso no se consulta con una app: llame al 112.",
            14f, Color.parseColor("#6C7689")
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101828"))
            addView(col)
        })
    }

    private fun empezar() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), PERMISO_CAMARA
            )
            return
        }
        analizador.limpiar()
        resultado.text = ""
        resultado.setBackgroundColor(Color.TRANSPARENT)
        exposicionBloqueada = false
        arrancado = System.currentTimeMillis()
        midiendo = true
        botonar.text = "Parar"
        botonar.setBackgroundColor(Color.parseColor("#7A1A15"))
        progreso.text = "Preparando la cámara…"
        abrirCamara()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISO_CAMARA &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) empezar()
    }

    @Suppress("MissingPermission")
    private fun abrirCamara() {
        hilo = HandlerThread("pulso").also { it.start() }
        manos = Handler(hilo!!.looper)

        val cm = getSystemService(CAMERA_SERVICE) as CameraManager
        // Preferimos la camara de atras CON linterna: sin luz no se ve
        // nada a traves del dedo. Si no la hay, se prueba con cualquiera.
        val id = try {
            cm.cameraIdList.firstOrNull { c ->
                val ca = cm.getCameraCharacteristics(c)
                ca.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK &&
                    ca.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cm.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            null
        }

        if (id == null) {
            progreso.text = "Este móvil no tiene una cámara que sirva."
            parar(false)
            return
        }

        // Imagen pequeña a proposito: no queremos calidad, queremos un
        // numero por imagen y que el movil no sude para conseguirlo.
        lector = ImageReader.newInstance(320, 240, android.graphics.ImageFormat.YUV_420_888, 2)
        lector!!.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                // Brillo medio de la imagen. Con el dedo puesto y la
                // linterna encendida, este numero baja una pizca en cada
                // latido: es todo lo que hace falta.
                val plano = img.planes[0]
                val buf = plano.buffer
                var suma = 0L
                var n = 0
                val paso = 7          // no hace falta mirar todos los pixeles
                var i = 0
                while (i < buf.remaining()) {
                    suma += (buf.get(i).toInt() and 0xFF)
                    n++
                    i += paso
                }
                if (n > 0) {
                    val brillo = suma.toDouble() / n
                    runOnUiThread { nuevaMuestra(brillo) }
                }
            } catch (e: Exception) {
            } finally {
                img.close()
            }
        }, manos)

        try {
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(dispositivo: CameraDevice) {
                    camara = dispositivo
                    crearSesion()
                }
                override fun onDisconnected(dispositivo: CameraDevice) { dispositivo.close() }
                override fun onError(dispositivo: CameraDevice, error: Int) {
                    dispositivo.close()
                    runOnUiThread {
                        progreso.text = "No he podido abrir la cámara."
                        parar(false)
                    }
                }
            }, manos)
        } catch (e: Exception) {
            progreso.text = "No he podido abrir la cámara: ${e.message}"
            parar(false)
        }
    }

    @Suppress("DEPRECATION")
    private fun crearSesion() {
        val c = camara ?: return
        val superficie = lector?.surface ?: return
        try {
            c.createCaptureSession(listOf(superficie),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        sesion = s
                        peticion = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(superficie)
                            // La linterna: sin ella no se ve nada a traves del dedo.
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }
                        try {
                            s.setRepeatingRequest(peticion!!.build(), null, manos)
                        } catch (e: Exception) {}
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        runOnUiThread {
                            progreso.text = "No he podido preparar la cámara."
                            parar(false)
                        }
                    }
                }, manos)
        } catch (e: Exception) {
            runOnUiThread { parar(false) }
        }
    }

    /**
     * Bloquea el ajuste automatico de luz.
     *
     * Se hace unos segundos DESPUES de empezar, ya con el dedo puesto:
     * si se bloqueara antes, se quedaria fijada la exposicion de la
     * habitacion y con el dedo encima saldria todo negro.
     */
    private fun bloquearExposicion() {
        val s = sesion ?: return
        val p = peticion ?: return
        try {
            p.set(CaptureRequest.CONTROL_AE_LOCK, true)
            s.setRepeatingRequest(p.build(), null, manos)
            exposicionBloqueada = true
        } catch (e: Exception) {}
    }

    private fun nuevaMuestra(brillo: Double) {
        if (!midiendo) return
        val ahora = System.currentTimeMillis()
        val transcurrido = (ahora - arrancado) / 1000

        // Los primeros 3 segundos son para que la camara se acostumbre al
        // dedo. No se guardan: estarian llenos de los ajustes automaticos.
        if (transcurrido < 3) {
            progreso.text = "Preparando… no mueva el dedo"
            aviso.text = "Ajustando la cámara a su dedo…"
            aviso.setTextColor(Color.parseColor("#94A3B8"))
            return
        }
        if (!exposicionBloqueada) {
            bloquearExposicion()
            analizador.limpiar()
        }

        analizador.añadir(brillo, ahora)
        val seg = analizador.segundosGrabados()

        // LA ONDA SE REDIBUJA 8 VECES POR SEGUNDO, NO 30.
        //
        // Al principio se redibujaba con cada imagen de la camara. Limpiar
        // la onda y buscarle los picos no es gratis, y hacerlo treinta
        // veces por segundo en el mismo hilo que atiende a la camara
        // acababa robandole tiempo: se perdian imagenes y el pulso salia
        // peor justo por culpa del dibujo que servia para vigilarlo.
        //
        // A ocho veces por segundo el movimiento se ve igual de fluido
        // para un ojo humano y la medicion recupera su precision.
        if (ahora - ultimoDibujo > 120) {
            ultimoDibujo = ahora
            val firmeza = analizador.firmezaActual()
            grafica.actualizar(analizador.ondaReciente(), firmeza)
            pintarAviso(firmeza)
        }

        if (seg < analizador.segundosNecesarios) {
            if (ahora - ultimoTexto > 400) {
                ultimoTexto = ahora
                progreso.text = "Midiendo…  $seg de ${analizador.segundosNecesarios} segundos"
            }
            return
        }

        val r = analizador.calcular()
        if (r == null) {
            progreso.text = "No consigo leer el pulso. Pruebe otra vez."
            return
        }
        mostrar(r)
        parar(false)
    }

    /**
     * El semaforo, en palabras ademas de en color.
     *
     * El color solo no basta: hay quien no distingue bien verde de rojo, y
     * hay quien lo ve pero no sabe que hacer con esa informacion. Cada
     * color va acompañado de la instruccion concreta.
     */
    private fun pintarAviso(f: AnalizadorPulso.Firmeza) {
        when (f) {
            AnalizadorPulso.Firmeza.BIEN -> {
                aviso.text = "🟢 Perfecto. No mueva el dedo."
                aviso.setTextColor(Color.parseColor("#22C55E"))
            }
            AnalizadorPulso.Firmeza.REGULAR -> {
                aviso.text = "🟠 Se le mueve un poco el dedo. Apoye la mano en la mesa."
                aviso.setTextColor(Color.parseColor("#F59E0B"))
            }
            AnalizadorPulso.Firmeza.MAL -> {
                aviso.text = "🔴 Se mueve mucho. Esta lectura no valdrá: quédese quieto."
                aviso.setTextColor(Color.parseColor("#EF4444"))
            }
            AnalizadorPulso.Firmeza.SIN_DEDO -> {
                aviso.text = "⚪ No le veo el pulso. Tape la cámara entera con la yema, " +
                             "apoyada pero sin apretar."
                aviso.setTextColor(Color.parseColor("#94A3B8"))
            }
        }
    }

    private fun mostrar(r: AnalizadorPulso.Resultado) {
        progreso.text = ""

        val comentarioPulso = when {
            // Por debajo de 50 conviene decir algo mas concreto, pero sin
            // alarmar: en gente que hace deporte o toma ciertas pastillas
            // para el corazon o la tension es de lo mas normal.
            r.pulsaciones < 50 -> "Es un pulso lento. En personas que hacen deporte, o " +
                                  "que toman pastillas para el corazón o la tensión, es " +
                                  "normal. Si le sale así varias veces y además se marea " +
                                  "o se cansa mucho, coménteselo a su médico."
            r.pulsaciones < 60 -> "Por debajo de lo corriente en reposo. En personas " +
                                  "que hacen ejercicio es normal."
            r.pulsaciones <= 100 -> "Dentro de lo corriente en reposo."
            else -> "Algo alto para estar en reposo. Puede ser por haberse movido, " +
                    "por café, o por nervios."
        }

        // RMSSD: cuanto mas varian los intervalos, mas relajado esta el
        // cuerpo. Los rangos son orientativos y cambian mucho con la edad,
        // asi que se redactan con prudencia a proposito.
        val comentarioTension = when {
            r.rmssd >= 50 -> "Muy relajado"
            r.rmssd >= 30 -> "Tranquilo"
            r.rmssd >= 20 -> "Algo tenso"
            else -> "Tenso"
        }

        val fiabilidad = when {
            r.calidad > 0.7 -> "Lectura buena"
            r.calidad > 0.4 -> "Lectura regular: repítala para asegurarse"
            else -> "Lectura poco fiable: repítala sin mover el dedo"
        }

        // Cuantas de las ultimas seis mediciones guardadas salieron
        // desiguales. Es lo que decide si esto es un aviso o solo una nota.
        val vecesDesigual = Historial.leer(this)
            .filter { it.tipo == "Pulso" }
            .take(6)
            .count { it.resumen.contains("IRREGULAR") || it.resumen.contains("desigual") }

        // El ritmo es lo mas importante de toda esta pantalla, asi que va
        // primero y con las palabras mas claras que se me ocurren. Nunca
        // se afirma que la persona tenga nada: se le manda al medico.
        val textoRitmo = when (r.ritmo) {
            AnalizadorPulso.Ritmo.REGULAR ->
                "❤️ Su corazón late con buen compás."
            AnalizadorPulso.Ritmo.DUDOSO ->
                "🟡 He notado el pulso algo desigual.\n" +
                "Puede no ser nada —basta con moverse un poco o respirar hondo—, " +
                "pero repita la medición un par de veces sentado y quieto. " +
                "Si sigue saliendo así, coménteselo a su médico."
            AnalizadorPulso.Ritmo.IRREGULAR -> {
                // UNA SOLA MEDICION NO PUEDE MANDAR A NADIE AL MEDICO.
                //
                // Esto lo escribi yo mismo en el Historial -"lo que importa
                // no es el numero de hoy, es si se repite"- y luego lo
                // incumpli aqui: con una sola lectura la app soltaba "su
                // corazon late a destiempo, pida cita". A una persona
                // joven y sana eso le da un susto de los que quitan el
                // sueño una semana, y encima por nada.
                //
                // Ahora la primera vez solo se apunta y se pide repetir. Y
                // esto no es ser blando: una arritmia que importa no
                // aparece una tarde y desaparece, sale una y otra vez. Con
                // exigir que se repita no se pierde practicamente nada de
                // lo que hay que detectar, y se quita casi todo el ruido.
                if (vecesDesigual >= 2)
                    "🔴 Van varias veces que el pulso sale desigual.\n\n" +
                    "Esto NO es un diagnóstico y esta app no puede dárselo. " +
                    "Pero repetirse sí es motivo para pedir cita y decirle a su médico: " +
                    "\"me sale el pulso irregular, ¿me pueden hacer un electrocardiograma?\". " +
                    "No es una urgencia si se encuentra bien, pero no lo deje pasar."
                else
                    "🟡 Hoy el pulso ha salido desigual.\n\n" +
                    "Con una sola medición esto no quiere decir nada: en gente joven " +
                    "el pulso cambia mucho al respirar, y en cualquiera lo cambia el " +
                    "café, el móvil mal apoyado o haber subido escaleras.\n\n" +
                    "Repítalo otro día, sentado y tranquilo. Queda apuntado, y si se " +
                    "repite varias veces ya se lo diré claramente."
            }
            AnalizadorPulso.Ritmo.POCOS_DATOS ->
                if (r.latidosPerdidos > 0.10)
                    "⚪ Se me han escapado latidos, así que del compás no opino.\n\n" +
                    "Cuando el dedo está flojo o se mueve, pierdo algún latido y " +
                    "entonces las pulsaciones salen más bajas de lo real y el ritmo " +
                    "parece desordenado sin serlo. Tape la cámara del todo con la yema, " +
                    "apoyada pero sin apretar, y repita sin mover la mano."
                else
                    "⚪ Del compás del corazón no puedo opinar con esta lectura. " +
                    "Repítala con el dedo bien quieto."
        }

        // La respiracion sale del mismo dedo, sin pedir nada mas. Si no se
        // ha podido leer, sencillamente no se enseña: mejor omitirla que
        // dar un numero inventado.
        val resp = analizador.respiracion()
        val textoResp = when {
            resp == null -> ""
            resp > 20 -> "\nRespiración: $resp por minuto — algo acelerada. " +
                         "Si le pasa estando en reposo y sin haberse movido, " +
                         "coménteselo a su médico.\n"
            resp < 10 -> "\nRespiración: $resp por minuto — lenta.\n"
            else -> "\nRespiración: $resp por minuto — normal.\n"
        }

        resultado.text = "$textoRitmo\n\n" +
                         "────────────\n\n" +
                         "${r.pulsaciones} pulsaciones por minuto\n" +
                         "$comentarioPulso\n" +
                         textoResp + "\n" +
                         "Tensión: $comentarioTension\n" +
                         "(variabilidad ${r.rmssd.toInt()} ms, ${r.latidos} latidos)\n\n" +
                         fiabilidad
        resultado.setBackgroundColor(
            when {
                r.ritmo == AnalizadorPulso.Ritmo.IRREGULAR -> Color.parseColor("#4A1512")
                r.ritmo == AnalizadorPulso.Ritmo.DUDOSO -> Color.parseColor("#3A2A10")
                r.calidad > 0.4 -> Color.parseColor("#14301F")
                else -> Color.parseColor("#3A2A10")
            }
        )
        // La voz de aviso, solo cuando ya se ha repetido. Soltarle a
        // alguien "su corazón late a destiempo" en alto, la primera vez y
        // por una lectura suelta, es dar un susto sin ninguna base.
        if (r.ritmo == AnalizadorPulso.Ritmo.IRREGULAR && vecesDesigual >= 2) hablarFuerte()

        // AL HISTORIAL, SOLO SI LA MEDICION ES DE FIAR.
        //
        // Se exige lectura limpia y suficientes latidos. Guardar lecturas
        // dudosas seria peor que no guardar nada: alguien podria contar
        // cuantas salieron irregulares y sacar una conclusion falsa a
        // partir de dedos mal apoyados.
        if (r.calidad > 0.5 && r.latidos >= 20 &&
            r.ritmo != AnalizadorPulso.Ritmo.POCOS_DATOS) {
            val ritmoCorto = when (r.ritmo) {
                AnalizadorPulso.Ritmo.REGULAR -> "ritmo regular"
                AnalizadorPulso.Ritmo.DUDOSO -> "ritmo algo desigual"
                AnalizadorPulso.Ritmo.IRREGULAR -> "RITMO IRREGULAR"
                else -> ""
            }
            val detalle = buildString {
                append("variabilidad ${r.rmssd.toInt()} ms")
                if (resp != null) append(", respiración $resp/min")
            }
            Historial.añadir(this, "Pulso", "${r.pulsaciones} ppm, $ritmoCorto", detalle)
        }
        instruccion.setTextColor(Color.parseColor("#9AA4B2"))
        instruccion.text = "Para comparar de verdad, mídase siempre en las mismas " +
                           "condiciones: sentado, tranquilo y a la misma hora."
    }

    private fun parar(porUsuario: Boolean) {
        midiendo = false
        botonar.text = "Empezar"
        botonar.setBackgroundColor(Color.parseColor("#0B7A3B"))
        if (porUsuario) progreso.text = ""

        try { sesion?.stopRepeating() } catch (e: Exception) {}
        try { sesion?.close() } catch (e: Exception) {}
        try { camara?.close() } catch (e: Exception) {}
        try { lector?.close() } catch (e: Exception) {}
        sesion = null; camara = null; lector = null
        try { hilo?.quitSafely() } catch (e: Exception) {}
        hilo = null; manos = null
    }

    override fun onPause() {
        super.onPause()
        // Si se sale de la pantalla hay que soltar la camara y apagar la
        // linterna SI O SI: dejarla encendida seria alarmante y ademas se
        // comeria la bateria.
        if (midiendo) parar(true)
    }

    /**
     * Lee el aviso en voz alta cuando el pulso sale irregular.
     *
     * Mucha gente mayor ve mal la pantalla y va a leer por encima. Si hay
     * un solo mensaje de toda la app que tiene que llegar entero, es este.
     */
    private fun hablarFuerte() {
        try {
            var tts: android.speech.tts.TextToSpeech? = null
            tts = android.speech.tts.TextToSpeech(this) { estado ->
                if (estado == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts?.language = java.util.Locale("es", "ES")
                    tts?.setSpeechRate(0.9f)
                    tts?.speak(
                        "Atención. Le late el corazón a destiempo. No es una urgencia " +
                        "si se encuentra bien, pero pida cita con su médico y dígale " +
                        "que quiere un electrocardiograma.",
                        android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "ritmo"
                    )
                }
            }
        } catch (e: Exception) {}
    }

    private fun t(s: String, tam: Float, color: Int, negrita: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = tam; setTextColor(color)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }
}
