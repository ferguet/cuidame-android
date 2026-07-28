package es.guiamayores.cuidame

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture
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
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FOTO DEL OJO
 * ============
 *
 * EL PROBLEMA QUE OBLIGO A DISEÑARLA ASI
 *
 * La linterna esta detras y la pantalla delante. Con la camara de atras
 * hay luz pero la persona no ve donde apunta, y sola no acierta al ojo.
 * Con la de delante se ve, pero no hay linterna.
 *
 * La salida es usar LA PROPIA PANTALLA como foco: blanca y a tope de
 * brillo ilumina de sobra a treinta centimetros. Es lo que hacen los
 * moviles cuando te haces un selfie "con flash", porque flash delantero
 * no tienen.
 *
 * Y para esto es MEJOR que la linterna, por un motivo que no es obvio:
 * sabemos exactamente de que color es esa luz, porque la ponemos
 * nosotros. La linterna de cada movil tira mas fria o mas calida y no hay
 * forma de saberlo.
 *
 * El marco blanco grueso alrededor de la ventana de la camara resuelve la
 * ultima pega: ilumina sin tapar, y por el hueco del centro la persona se
 * ve y se coloca.
 *
 * LO QUE DE VERDAD ENTREGA ESTA PANTALLA
 *
 * No es el numero. Es la FOTO guardada para enseñarla en la consulta, y
 * una escala de color al lado para comparar a ojo. Si mi calculo falla,
 * la app sigue siendo util porque ha capturado una imagen que alguien con
 * criterio puede mirar. El numero es el añadido, no el producto.
 */
class FotoOjoActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var camara: CameraDevice? = null
    private var sesion: CameraCaptureSession? = null
    private var lector: ImageReader? = null
    private var hilo: HandlerThread? = null
    private var manos: Handler? = null

    private lateinit var vista: TextureView
    private lateinit var instruccion: TextView
    private lateinit var resultado: TextView
    private lateinit var miniatura: ImageView
    private lateinit var escala: EscalaColor
    private lateinit var disparar: Button

    private var voz: TextToSpeech? = null
    private var vozLista = false
    private var ultimaFoto: File? = null

    private val PERMISO = 88

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Brillo al maximo: la pantalla ES la luz de esta pantalla.
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        voz = TextToSpeech(this, this)
        construir()
    }

    private fun construir() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 36, 30, 36)
            // Fondo BLANCO a proposito: es el foco que ilumina la cara.
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        col.addView(t("Mirar el ojo", 28f, Color.BLACK, true))
        col.addView(t(
            "Acérquese el móvil a unos 25 cm de la cara, con buena luz. " +
            "Con un dedo, bájese un poco el párpado de abajo para que se vea " +
            "la carne rosada de dentro. Encuadre el ojo en el recuadro.",
            17f, Color.parseColor("#334155")
        ))

        // La ventana de la camara, rodeada de blanco.
        vista = TextureView(this)
        val marco = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(48, 48, 48, 48)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 900
            ).apply { topMargin = 12 }
            addView(vista, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        col.addView(marco)

        instruccion = t("", 18f, Color.parseColor("#B45309"), true).apply {
            gravity = Gravity.CENTER
        }
        col.addView(instruccion)

        disparar = Button(this).apply {
            text = "Hacer la foto"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1D4ED8"))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 190
            ).apply { topMargin = 20 }
            setOnClickListener { hacerFoto() }
        }
        col.addView(disparar)

        miniatura = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 520
            ).apply { topMargin = 20 }
            adjustViewBounds = true
            visibility = android.view.View.GONE
        }
        col.addView(miniatura)

        escala = EscalaColor(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 260
            ).apply { topMargin = 16 }
            visibility = android.view.View.GONE
        }
        col.addView(escala)

        resultado = t("", 18f, Color.BLACK).apply {
            setPadding(24, 24, 24, 24)
        }
        col.addView(resultado)

        col.addView(t(
            "Esto NO sustituye a un análisis de sangre ni diagnostica nada. Es una " +
            "orientación: los mejores estudios con este método aciertan alrededor de " +
            "tres de cada cuatro veces.\n\n" +
            "Lo más útil de esta pantalla es la foto: guárdela y enséñesela a su " +
            "médico. Él lo ve en dos segundos sin necesitar ningún cálculo.",
            14f, Color.parseColor("#64748B")
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(col)
        })

        vista.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) = abrir()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
    }

    // ---------------- camara ----------------

    @Suppress("MissingPermission")
    private fun abrir() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISO)
            return
        }
        if (camara != null) return

        hilo = HandlerThread("ojo").also { it.start() }
        manos = Handler(hilo!!.looper)

        val cm = getSystemService(CAMERA_SERVICE) as CameraManager
        // La DELANTERA: es la unica con la que una persona sola puede
        // verse y apuntarse al ojo.
        val id = try {
            cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            } ?: cm.cameraIdList.firstOrNull()
        } catch (e: Exception) { null }

        if (id == null) {
            instruccion.text = "Este móvil no tiene cámara delantera."
            return
        }

        lector = ImageReader.newInstance(1280, 960, android.graphics.ImageFormat.JPEG, 2)
        lector!!.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val buf = img.planes[0].buffer
                val datos = ByteArray(buf.remaining())
                buf.get(datos)
                runOnUiThread { procesar(datos) }
            } catch (e: Exception) {
            } finally { img.close() }
        }, manos)

        try {
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) { camara = d; sesionar() }
                override fun onDisconnected(d: CameraDevice) { d.close(); camara = null }
                override fun onError(d: CameraDevice, e: Int) {
                    d.close(); camara = null
                    runOnUiThread { instruccion.text = "No he podido abrir la cámara." }
                }
            }, manos)
        } catch (e: Exception) {
            instruccion.text = "No he podido abrir la cámara: ${e.message}"
        }
    }

    @Suppress("DEPRECATION")
    private fun sesionar() {
        val c = camara ?: return
        val textura = vista.surfaceTexture ?: return
        textura.setDefaultBufferSize(1280, 960)
        val superficie = Surface(textura)
        val captura = lector?.surface ?: return

        try {
            c.createCaptureSession(listOf(superficie, captura),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        sesion = s
                        val p = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(superficie)
                            set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        try { s.setRepeatingRequest(p.build(), null, manos) } catch (e: Exception) {}
                        runOnUiThread {
                            instruccion.text = "Colóquese y pulse el botón azul"
                            hablar("Acérquese el móvil a la cara. Bájese el párpado de abajo " +
                                   "con el dedo y pulse el botón azul.")
                        }
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        runOnUiThread { instruccion.text = "No he podido preparar la cámara." }
                    }
                }, manos)
        } catch (e: Exception) {}
    }

    private fun hacerFoto() {
        val c = camara ?: return
        val s = sesion ?: return
        val destino = lector?.surface ?: return
        instruccion.text = "Quieto…"
        try {
            val p = c.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(destino)
                set(CaptureRequest.JPEG_ORIENTATION, 0)
            }
            s.capture(p.build(), null, manos)
        } catch (e: Exception) {
            instruccion.text = "No he podido hacer la foto."
        }
    }

    private fun procesar(datos: ByteArray) {
        val foto = BitmapFactory.decodeByteArray(datos, 0, datos.size) ?: return

        // Se analiza el centro de la imagen, que es donde esta el ojo si
        // la persona se ha encuadrado. Recortar quita pelo, ropa y fondo,
        // que solo meterian ruido en el color.
        val lado = minOf(foto.width, foto.height)
        val recorte = Bitmap.createBitmap(
            foto,
            (foto.width - lado) / 2 + lado / 6,
            (foto.height - lado) / 2 + lado / 6,
            (lado * 2) / 3,
            (lado * 2) / 3
        )

        val r = AnalizadorColor.analizar(recorte)
        guardar(foto)

        miniatura.setImageBitmap(recorte)
        miniatura.visibility = android.view.View.VISIBLE
        escala.mostrar(r.colorRojo, r.rojez)
        escala.visibility = android.view.View.VISIBLE

        if (!r.hayBlanco && !r.hayRojo) {
            instruccion.text = "No he reconocido el ojo"
            resultado.text = "No he podido distinguir el ojo en la foto. " +
                "Acérquese más, busque mejor luz y procure que el ojo quede en el centro."
            return
        }

        instruccion.text = "Listo"
        resultado.text = buildString {
            append("PARTE BLANCA DEL OJO\n")
            append(AnalizadorColor.textoAmarilleo(r.amarilleo))
            append("\n\n")
            append("CARNE DEL PÁRPADO\n")
            append(AnalizadorColor.textoRojez(r.rojez, r.hayRojo))
            append("\n\n")
            append("Foto guardada para enseñarla al médico.")
        }
        hablar("Ya está. Puede enseñarle esta foto a su médico.")
    }

    private fun guardar(foto: Bitmap) {
        try {
            val carpeta = File(filesDir, "ojos").apply { mkdirs() }
            val nombre = SimpleDateFormat("yyyyMMdd_HHmmss", Locale("es", "ES")).format(Date())
            val f = File(carpeta, "ojo_$nombre.jpg")
            FileOutputStream(f).use { foto.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            ultimaFoto = f
        } catch (e: Exception) {}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISO && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) abrir()
    }

    private fun hablar(f: String) {
        if (vozLista) voz?.speak(f, TextToSpeech.QUEUE_FLUSH, null, "ojo")
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
        cerrar()
    }

    private fun cerrar() {
        try { sesion?.close() } catch (e: Exception) {}
        try { camara?.close() } catch (e: Exception) {}
        try { lector?.close() } catch (e: Exception) {}
        sesion = null; camara = null; lector = null
        try { hilo?.quitSafely() } catch (e: Exception) {}
        hilo = null; manos = null
    }

    override fun onDestroy() {
        cerrar()
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

/**
 * La escala de color, del rosa bien irrigado al palido.
 *
 * Es la parte de la pantalla en la que mas confio, y no es casualidad:
 * comparar dos colores es algo que el ojo humano hace muy bien y que no
 * depende de que mi algoritmo acierte. Es lo mismo que hace un medico al
 * mirar un parpado.
 */
class EscalaColor(contexto: android.content.Context) : android.view.View(contexto) {

    private var color = Color.GRAY
    private var posicion = -1.0
    private val pincel = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val letra = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
    }

    fun mostrar(c: Int, rojez: Double) {
        color = c
        // 1,10 (muy palido) a 1,60 (bien coloreado) repartido en la barra
        posicion = ((rojez - 1.10) / 0.50).coerceIn(0.0, 1.0)
        invalidate()
    }

    override fun onDraw(lienzo: android.graphics.Canvas) {
        super.onDraw(lienzo)
        val an = width.toFloat()
        val altoBarra = 90f
        val arriba = 60f

        // Degradado de palido a bien coloreado
        val palido = Color.rgb(240, 205, 205)
        val sano = Color.rgb(200, 80, 80)
        pincel.shader = android.graphics.LinearGradient(
            0f, 0f, an, 0f, palido, sano, android.graphics.Shader.TileMode.CLAMP
        )
        lienzo.drawRoundRect(0f, arriba, an, arriba + altoBarra, 16f, 16f, pincel)
        pincel.shader = null

        letra.textSize = 28f
        lienzo.drawText("más pálido", 6f, arriba - 14f, letra)
        val ancho = letra.measureText("bien coloreado")
        lienzo.drawText("bien coloreado", an - ancho - 6f, arriba - 14f, letra)

        if (posicion >= 0) {
            val x = (an * posicion).toFloat().coerceIn(20f, an - 20f)
            // Marca de donde ha caido la medicion
            pincel.color = Color.BLACK
            pincel.style = android.graphics.Paint.Style.STROKE
            pincel.strokeWidth = 6f
            lienzo.drawRoundRect(x - 22f, arriba - 8f, x + 22f, arriba + altoBarra + 8f, 12f, 12f, pincel)
            pincel.style = android.graphics.Paint.Style.FILL

            // Y el color medido de verdad, para comparar al lado
            pincel.color = color
            lienzo.drawRoundRect(
                an / 2f - 90f, arriba + altoBarra + 28f,
                an / 2f + 90f, arriba + altoBarra + 118f, 14f, 14f, pincel
            )
            pincel.color = Color.parseColor("#94A3B8")
            pincel.style = android.graphics.Paint.Style.STROKE
            pincel.strokeWidth = 3f
            lienzo.drawRoundRect(
                an / 2f - 90f, arriba + altoBarra + 28f,
                an / 2f + 90f, arriba + altoBarra + 118f, 14f, 14f, pincel
            )
            pincel.style = android.graphics.Paint.Style.FILL
            letra.textSize = 26f
            val txt = "color medido"
            lienzo.drawText(txt, an / 2f - letra.measureText(txt) / 2f,
                arriba + altoBarra + 150f, letra)
        }
    }
}
