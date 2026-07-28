package es.guiamayores.cuidame

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * LA PANTALLA DE CASA
 * ===================
 *
 * Se configura una vez y no se vuelve a tocar. Por eso lo importante no
 * es que sea bonita, sino que en cualquier momento se pueda mirar y
 * saber, de un vistazo y sin interpretar nada, si la vigilancia esta
 * puesta o no.
 *
 * Y tiene dos botones de prueba, que para mi son la parte mas importante
 * de todo esto: nadie deberia confiar su seguridad a una app que nunca
 * ha visto funcionar. Se puede probar el mensaje de verdad y se puede
 * ver la pantalla de alarma entera, sin tener que tirarse al suelo.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var ajustes: Ajustes
    private lateinit var estado: TextView
    private lateinit var interruptor: Button
    private lateinit var cPersona: EditText
    private lateinit var cNombre: EditText
    private lateinit var cTelefono: EditText
    private lateinit var permisos: TextView
    private lateinit var arrancarAqui: Button

    private val PERMISOS = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ajustes = Ajustes(this)
        construir()
        pedirPermisos()
        refrescar()
    }

    override fun onResume() {
        super.onResume()
        refrescar()
    }

    private fun construir() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 56, 48, 56)
            setBackgroundColor(Color.parseColor("#101828"))
        }

        col.addView(texto("Cuídame", 38f, Color.WHITE, negrita = true))
        col.addView(texto(
            "Aviso a alguien de confianza si se cae y no responde, o si pasa demasiadas horas sin moverse.",
            17f, Color.parseColor("#9AA4B2")
        ))

        estado = texto("", 21f, Color.WHITE, negrita = true).apply {
            setPadding(34, 34, 34, 34)
            gravity = Gravity.CENTER
        }
        col.addView(hueco(28))
        col.addView(estado)

        interruptor = boton("", Color.parseColor("#0B7A3B")) { alternar() }
        interruptor.textSize = 22f
        interruptor.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 200
        ).apply { topMargin = 22 }
        col.addView(interruptor)

        // =====================================================
        //  ARRIBA: LO QUE USA LA PERSONA MAYOR
        // =====================================================
        //
        // Esta pantalla la miran dos personas muy distintas, y antes
        // estaban mezcladas. La persona mayor solo quiere ver si esta
        // protegida y poder medirse el pulso; los datos de contacto, los
        // permisos y las pruebas los rellena una vez un hijo o el cuidador
        // y no se vuelven a tocar.
        //
        // Poner las dos cosas al mismo tamaño obliga a la persona mayor a
        // leerse y descartar seis botones que no son para ella cada vez que
        // abre la app. Asi que lo suyo va arriba y grande, y la
        // configuracion abajo y pequeña. El orden y el tamaño ya explican
        // que es de cada uno, sin tener que leer nada.
        col.addView(hueco(36))
        col.addView(rotulo("QUÉ PUEDE MEDIRSE"))

        col.addView(botonIcono("❤️", "El pulso", "Latidos, ritmo y respiración",
            "#7C2D6E") { startActivity(Intent(this, PulsoActivity::class.java)) })

        col.addView(botonIcono("🌬️", "Respirar despacio", "Ejercicio de 3 minutos para calmarse",
            "#1E5F8E") { startActivity(Intent(this, RespiracionActivity::class.java)) })

        col.addView(botonIcono("👀", "Mirar el ojo", "Ver si hay anemia o color amarillo",
            "#0E7490") { startActivity(Intent(this, FotoOjoActivity::class.java)) })

        col.addView(botonIcono("✋", "El temblor", "De las manos, en dos posturas",
            "#5B21B6") { startActivity(Intent(this, TemblorActivity::class.java)) })

        col.addView(botonIcono("📋", "Mis mediciones", "Lo medido otros días, para el médico",
            "#0F766E") { startActivity(Intent(this, HistorialActivity::class.java)) })

        // =====================================================
        //  ABAJO Y PEQUEÑO: LO QUE CONFIGURA QUIEN LE CUIDA
        // =====================================================
        col.addView(hueco(64))
        col.addView(separador())
        col.addView(rotulo("PARA QUIEN LE CUIDA"))
        col.addView(texto(
            "Esto se rellena una vez, normalmente un hijo o el cuidador. " +
            "Después ya no hay que volver a tocarlo.",
            14f, Color.parseColor("#6C7689")
        ))

        col.addView(hueco(16))
        col.addView(texto("Nombre de la persona mayor", 13f, Color.parseColor("#6C7689")))
        cPersona = campo("Ej: Antonia", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        col.addView(cPersona)

        col.addView(hueco(14))
        col.addView(texto("A quién se avisa si se cae", 13f, Color.parseColor("#6C7689")))
        cNombre = campo("Ej: Mi hija Marta", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        col.addView(cNombre)
        cTelefono = campo("Teléfono móvil", InputType.TYPE_CLASS_PHONE)
        col.addView(cTelefono)

        // ANTES AQUI HABIA UN BOTON QUE PONIA "GUARDAR", Y ERA UN ERROR.
        //
        // "Guardar" no le dice a nadie que va a pasar despues. Se
        // rellenaban los datos, se veia "guardar", se pulsaba... y uno se
        // quedaba igual, sin saber si ya estaba protegida la persona.
        //
        // Ahora el boton dice lo que HACE y hace las dos cosas de una vez:
        // guarda y arranca la vigilancia. Va justo debajo de los datos,
        // que es donde uno acaba de escribir y mira a continuacion. Es lo
        // unico de esta zona que se mantiene grande, porque es la accion
        // que deja la app en marcha.
        arrancarAqui = boton("GUARDAR Y EMPEZAR A VIGILAR", Color.parseColor("#0B7A3B")) {
            guardarYArrancar()
        }
        col.addView(arrancarAqui)

        // Los datos se guardan solos segun se escriben. Asi, aunque alguien
        // salga de la pantalla sin pulsar nada, no pierde lo escrito ni
        // tiene que acordarse de confirmar.
        listOf(cPersona, cNombre, cTelefono).forEach { campo ->
            campo.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    guardarSilencioso()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }

        col.addView(hueco(30))
        permisos = texto("", 14f, Color.parseColor("#9AA4B2"))
        col.addView(permisos)

        // Si el permiso se rechaza dos veces, Android DEJA DE PREGUNTAR y
        // el boton normal ya no hace nada. La unica salida es ir a los
        // ajustes de la app a mano, y encontrarlos no es evidente. Este
        // boton lleva directo.
        col.addView(botonPequeno("⚙️  Permisos de la app", "#1D4ED8") {
            try {
                startActivity(Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")
                ))
            } catch (e: Exception) {
                Toast.makeText(this, "Abra Ajustes > Aplicaciones > Cuídame > Permisos",
                    Toast.LENGTH_LONG).show()
            }
        })

        col.addView(hueco(26))
        col.addView(texto(
            "Probarlo sin caerse — nadie debería fiarse de esto sin haberlo visto funcionar antes.",
            13f, Color.parseColor("#6C7689")
        ))

        col.addView(botonPequeno("🔔  Ver la alarma (no avisa a nadie)", "#B45309") {
            startActivity(Intent(this, AlarmaActivity::class.java).apply {
                putExtra(AlarmaActivity.EXTRA_PRUEBA, true)
                putExtra(AlarmaActivity.EXTRA_MOTIVO, "se ha caído")
            })
        })

        col.addView(botonPequeno("✉️  Mandar un mensaje de prueba de verdad", "#7A1A15") {
            probarMensaje()
        })

        col.addView(botonPequeno("📈  Ver los sensores en vivo", "#334155") {
            startActivity(Intent(this, DiagnosticoActivity::class.java))
        })

        col.addView(hueco(34))
        col.addView(texto(
            "Para que funcione, el móvil tiene que ir encima de la persona: " +
            "en el bolsillo detecta bien; en un bolso, peor; encima de la mesa, no detecta nada.\n\n" +
            "Esto es una ayuda más, no una garantía. No sustituye a un aviso médico " +
            "ni al 112.",
            13f, Color.parseColor("#6C7689")
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101828"))
            addView(col)
        })

        cPersona.setText(ajustes.nombrePersona)
        cNombre.setText(ajustes.nombreContacto)
        cTelefono.setText(ajustes.telefonoContacto)
    }

    private fun refrescar() {
        val activa = ajustes.vigilanciaActiva && ajustes.estaConfigurada()

        // El boton de debajo de los datos tambien tiene que decir la
        // verdad: si ya esta vigilando, ofrecer "empezar a vigilar" seria
        // hacer dudar de si esta activo o no.
        if (::arrancarAqui.isInitialized) {
            if (activa && ServicioVigilancia.activo) {
                arrancarAqui.text = "GUARDAR LOS DATOS"
                pintar(arrancarAqui, Color.parseColor("#1D4ED8"))
            } else {
                arrancarAqui.text = "GUARDAR Y EMPEZAR A VIGILAR"
                pintar(arrancarAqui, Color.parseColor("#0B7A3B"))
            }
        }

        val sms = tienePermiso(Manifest.permission.SEND_SMS)
        val ubi = tienePermiso(Manifest.permission.ACCESS_FINE_LOCATION) ||
                  tienePermiso(Manifest.permission.ACCESS_COARSE_LOCATION)
        permisos.text = buildString {
            append(if (sms) "✅ Mensajes: puede avisar\n" else "❌ Mensajes: NO podrá avisar a nadie\n")
            append(if (ubi) "✅ Ubicación: dirá dónde está" else "⚠️ Ubicación: el aviso irá sin el sitio")
        }
        permisos.setTextColor(
            if (sms) Color.parseColor("#9AA4B2") else Color.parseColor("#F87171")
        )

        // Se comprueba que el servicio este VIVO, no solo que el ajuste
        // diga que si. Si Android lo mato o no llego a arrancar, decir
        // "vigilando" seria mentir justo en lo que mas importa.
        // NOMBRAR EL BOTON, NO SEÑALARLO.
        //
        // Antes ponia "pulse abajo para volver a activarlo". Para quien ha
        // hecho la pantalla es evidente cual es "abajo"; para quien la ve
        // por primera vez hay tres botones seguidos y uno pone GUARDAR.
        // Decir "abajo" obliga a interpretar, y ahi es donde se falla.
        // Ahora cada mensaje dice LAS PALABRAS EXACTAS que hay escritas en
        // el boton que se debe tocar, para que no haya nada que deducir.
        if (activa && !ServicioVigilancia.activo) {
            estado.text = "⚠️ NO está vigilando\n\n" +
                          "Debería estarlo, pero se ha parado.\n\n" +
                          "Toque el botón verde que pone:\nVOLVER A ACTIVAR"
            estado.background = fondo(Color.parseColor("#B45309"))
            interruptor.text = "🔔  VOLVER A ACTIVAR"
            pintar(interruptor, Color.parseColor("#0B7A3B"))
            return
        }

        if (activa) {
            estado.text = "✅ Vigilando\n\nYa está todo listo.\n" +
                          "Puede guardarse el móvil en el bolsillo y olvidarse."
            estado.background = fondo(Color.parseColor("#0B7A3B"))
            interruptor.text = "🔕  DEJAR DE VIGILAR"
            pintar(interruptor, Color.parseColor("#7A1A15"))
        } else if (!ajustes.estaConfigurada()) {
            // Los datos ya no estan justo debajo: se han bajado al final,
            // a la zona de quien le cuida. Asi que el aviso tiene que
            // decir DONDE ir, no solo que falta.
            estado.text = "Falta el teléfono\n\n" +
                          "Baje del todo, hasta donde pone\nPARA QUIEN LE CUIDA,\n" +
                          "y escriba el teléfono móvil de quien tiene que recibir el aviso.\n\n" +
                          "Luego toque el botón verde:\nGUARDAR Y EMPEZAR A VIGILAR"
            estado.background = fondo(Color.parseColor("#B45309"))
            interruptor.text = "🔔  EMPEZAR A VIGILAR"
            pintar(interruptor, Color.parseColor("#334155"))
        } else {
            estado.text = "No está vigilando\n\n" +
                          "Toque el botón verde que pone:\nEMPEZAR A VIGILAR"
            estado.background = fondo(Color.parseColor("#334155"))
            interruptor.text = "🔔  EMPEZAR A VIGILAR"
            pintar(interruptor, Color.parseColor("#0B7A3B"))
        }
    }

    /** Guarda sin decir nada. Se llama a cada tecla. */
    private fun guardarSilencioso() {
        ajustes.nombrePersona = cPersona.text.toString().trim()
        ajustes.nombreContacto = cNombre.text.toString().trim()
        ajustes.telefonoContacto = cTelefono.text.toString().trim()
    }

    private fun guardar() {
        guardarSilencioso()
        refrescar()
    }

    /**
     * El boton de debajo de los datos: guarda y arranca de una vez.
     *
     * Si falta el telefono no se limita a callarse: dice exactamente que
     * falta y deja el cursor puesto en la casilla, para que no haya que
     * buscarla ni adivinar cual era.
     */
    private fun guardarYArrancar() {
        guardarSilencioso()
        if (!ajustes.estaConfigurada()) {
            Toast.makeText(
                this,
                "Falta el teléfono de la persona que le va a avisar",
                Toast.LENGTH_LONG
            ).show()
            cTelefono.requestFocus()
            refrescar()
            return
        }
        if (!ajustes.vigilanciaActiva) {
            alternar()
        } else {
            Toast.makeText(this, "Datos guardados. Ya está vigilando.", Toast.LENGTH_SHORT).show()
            refrescar()
        }
    }

    private fun alternar() {
        guardar()
        if (!ajustes.estaConfigurada()) {
            Toast.makeText(
                this,
                "Falta el teléfono. Está abajo del todo, en PARA QUIEN LE CUIDA",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (ajustes.vigilanciaActiva) {
            ajustes.vigilanciaActiva = false
            ServicioVigilancia.parar(this)
        } else {
            // ANTES AQUI SE BLOQUEABA TODO SI FALTABA EL PERMISO DE SMS.
            //
            // Era un error de bulto: sin ese permiso la app se negaba a
            // vigilar, asi que no detectaba caidas, no sonaba la alarma y
            // no hacia absolutamente nada. Y el permiso de mensajes es
            // justo el que mas se atasca, porque si se rechaza dos veces
            // Android deja de preguntar y hay que ir a mano a los ajustes.
            //
            // Vigilar y avisar son dos cosas distintas. Sin SMS se pierde
            // el aviso a la familia, pero la deteccion, la alarma sonando
            // y la pantalla pidiendo ayuda siguen funcionando, y eso ya
            // sirve de algo si hay alguien cerca en la casa. Se avisa de
            // la limitacion, pero no se apaga todo por ella.
            ajustes.vigilanciaActiva = true
            ServicioVigilancia.arrancar(this)
            if (!tienePermiso(Manifest.permission.SEND_SMS)) {
                Toast.makeText(
                    this,
                    "Vigilando, pero SIN poder avisar por mensaje. Dé el permiso abajo.",
                    Toast.LENGTH_LONG
                ).show()
                pedirPermisos()
            }
        }
        refrescar()
    }

    private fun probarMensaje() {
        guardar()
        if (!ajustes.estaConfigurada()) {
            Toast.makeText(this, "Escriba primero el teléfono de contacto", Toast.LENGTH_LONG).show()
            return
        }
        val quien = ajustes.nombrePersona.ifBlank { "esta persona" }
        val fallo = Avisador.enviar(
            this,
            "PRUEBA de la app Cuídame. Si $quien se cayera, recibiría un mensaje como este " +
            "con la hora y el sitio. No hay que hacer nada."
        )
        Toast.makeText(this, fallo ?: "Mensaje de prueba enviado", Toast.LENGTH_LONG).show()
    }

    private fun tienePermiso(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun pedirPermisos() {
        val faltan = mutableListOf<String>()
        listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).forEach { if (!tienePermiso(it)) faltan.add(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !tienePermiso(Manifest.permission.POST_NOTIFICATIONS)
        ) faltan.add(Manifest.permission.POST_NOTIFICATIONS)

        if (faltan.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltan.toTypedArray(), PERMISOS)
        }
    }

    // ---------- ayudas para construir la pantalla ----------

    private fun texto(t: String, tam: Float, color: Int, negrita: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = tam; setTextColor(color)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }

    /** Rotulillo de seccion, en gris y pequeño: separa sin gritar. */
    private fun rotulo(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(Color.parseColor("#7E8AA0"))
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.14f
        setPadding(0, 8, 0, 8)
    }

    private fun separador() = View(this).apply {
        setBackgroundColor(Color.parseColor("#25314A"))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 2
        ).apply { bottomMargin = 26 }
    }

    /** Fondo de color con esquinas redondeadas. */
    private fun fondo(color: Int, radio: Float = 26f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radio
    }

    private fun pintar(b: Button, color: Int) { b.background = fondo(color) }

    private fun campo(pista: String, tipo: Int) = EditText(this).apply {
        hint = pista
        inputType = tipo
        textSize = 18f
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#6C7689"))
        background = fondo(Color.parseColor("#1C2434"), 16f)
        setPadding(26, 26, 26, 26)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 }
    }

    private fun boton(t: String, color: Int, alPulsar: () -> Unit) = Button(this).apply {
        text = t
        textSize = 20f
        setTextColor(Color.WHITE)
        background = fondo(color)
        setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 170
        ).apply { topMargin = 22 }
        setOnClickListener { alPulsar() }
    }

    /**
     * BOTON CON DIBUJO, PARA QUIEN NO VE BIEN
     * =======================================
     *
     * Un boton de texto obliga a leer para saber que hace, y leer es justo
     * lo que peor se le da a quien tiene la vista cansada o poca costumbre
     * con el movil. Un dibujo grande se reconoce de un golpe, incluso
     * borroso y sin gafas: el corazon rojo se distingue del ojo aunque no
     * se lean las letras.
     *
     * El dibujo va enorme a proposito (52sp, mas del doble que el texto) y
     * cada boton lleva ademas un color distinto. Asi hay TRES pistas para
     * encontrar el mismo boton -sitio, dibujo y color- y basta con acertar
     * una. Quien no lea nada acaba pulsando "el del corazon" o "el morado",
     * y llega igual.
     *
     * Debajo del titulo va una linea explicando en palabras normales que
     * hace, porque "El pulso" solo no dice si va a medir, a enseñar algo
     * viejo o a llamar a alguien.
     */
    private fun botonIcono(
        icono: String,
        titulo: String,
        explicacion: String,
        colorHex: String,
        alPulsar: () -> Unit
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = fondo(Color.parseColor(colorHex))
        setPadding(30, 30, 30, 30)
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 20 }

        addView(TextView(this@MainActivity).apply {
            text = icono
            textSize = 52f
            setPadding(0, 0, 32, 0)
        })

        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = titulo
                textSize = 25f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = explicacion
                textSize = 15f
                setTextColor(Color.parseColor("#DCE3F0"))
            })
        })

        setOnClickListener { alPulsar() }
    }

    /**
     * Boton discreto de la zona de configuracion.
     *
     * Es pequeño a proposito. No es que importe menos -el permiso de
     * mensajes es lo que hace que el aviso llegue-, es que lo toca otra
     * persona y una sola vez. Si tuviera el mismo tamaño que "El pulso",
     * competiria por la atencion de quien abre la app veinte veces al mes
     * y no tiene nada que hacer aqui.
     */
    private fun botonPequeno(t: String, colorHex: String, alPulsar: () -> Unit) =
        Button(this).apply {
            text = t
            textSize = 15f
            setTextColor(Color.parseColor("#E6EAF2"))
            background = fondo(Color.parseColor(colorHex), 18f)
            setAllCaps(false)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 118
            ).apply { topMargin = 12 }
            setOnClickListener { alPulsar() }
        }

    private fun hueco(alto: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, alto
        )
    }
}
