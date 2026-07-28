package es.guiamayores.cuidame

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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
            setPadding(28, 28, 28, 28)
            gravity = Gravity.CENTER
        }
        col.addView(hueco(28))
        col.addView(estado)

        interruptor = boton("", Color.parseColor("#0B7A3B")) { alternar() }
        col.addView(interruptor)

        col.addView(hueco(40))
        col.addView(texto("QUIÉN ES LA PERSONA", 14f, Color.parseColor("#9AA4B2")))
        cPersona = campo("Su nombre. Ej: Antonia", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        col.addView(cPersona)

        col.addView(hueco(28))
        col.addView(texto("A QUIÉN SE AVISA", 14f, Color.parseColor("#9AA4B2")))
        cNombre = campo("Nombre. Ej: Mi hija Marta", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        col.addView(cNombre)
        cTelefono = campo("Teléfono móvil", InputType.TYPE_CLASS_PHONE)
        col.addView(cTelefono)

        col.addView(boton("Guardar", Color.parseColor("#1D4ED8")) { guardar() })

        col.addView(hueco(40))
        col.addView(texto("SALUD", 14f, Color.parseColor("#9AA4B2")))
        col.addView(boton("Medir el pulso y la respiración", Color.parseColor("#7C2D6E")) {
            startActivity(Intent(this, PulsoActivity::class.java))
        })

        col.addView(boton("Respiración guiada (3 minutos)", Color.parseColor("#1E5F8E")) {
            startActivity(Intent(this, RespiracionActivity::class.java))
        })

        col.addView(hueco(40))
        col.addView(texto("PERMISOS", 14f, Color.parseColor("#9AA4B2")))
        permisos = texto("", 16f, Color.parseColor("#9AA4B2"))
        col.addView(permisos)

        // Si el permiso se rechaza dos veces, Android DEJA DE PREGUNTAR y
        // el boton normal ya no hace nada. La unica salida es ir a los
        // ajustes de la app a mano, y encontrarlos no es evidente. Este
        // boton lleva directo.
        col.addView(boton("Abrir los permisos de la app", Color.parseColor("#1D4ED8")) {
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

        col.addView(hueco(44))
        col.addView(texto("PROBARLO SIN CAERSE", 14f, Color.parseColor("#9AA4B2")))
        col.addView(texto(
            "Nadie debería fiarse de esto sin haberlo visto funcionar antes.",
            15f, Color.parseColor("#9AA4B2")
        ))

        col.addView(boton("Ver la alarma (no avisa a nadie)", Color.parseColor("#B45309")) {
            startActivity(Intent(this, AlarmaActivity::class.java).apply {
                putExtra(AlarmaActivity.EXTRA_PRUEBA, true)
                putExtra(AlarmaActivity.EXTRA_MOTIVO, "se ha caído")
            })
        })

        col.addView(boton("Mandar un mensaje de prueba de verdad", Color.parseColor("#7A1A15")) {
            probarMensaje()
        })

        col.addView(boton("Ver los sensores en vivo", Color.parseColor("#334155")) {
            startActivity(Intent(this, DiagnosticoActivity::class.java))
        })

        col.addView(hueco(40))
        col.addView(texto(
            "Para que funcione, el móvil tiene que ir encima de la persona: " +
            "en el bolsillo detecta bien; en un bolso, peor; encima de la mesa, no detecta nada.\n\n" +
            "Esto es una ayuda más, no una garantía. No sustituye a un aviso médico " +
            "ni al 112.",
            14f, Color.parseColor("#6C7689")
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
        if (activa && !ServicioVigilancia.activo) {
            estado.text = "⚠️ Debería estar vigilando, pero no lo está\n\n" +
                          "Pulse abajo para volver a activarlo."
            estado.setBackgroundColor(Color.parseColor("#B45309"))
            interruptor.text = "Volver a activar"
            interruptor.setBackgroundColor(Color.parseColor("#0B7A3B"))
            return
        }

        if (activa) {
            estado.text = "✅ Vigilando\n\nPuede guardar el móvil y olvidarse."
            estado.setBackgroundColor(Color.parseColor("#0B7A3B"))
            interruptor.text = "Dejar de vigilar"
            interruptor.setBackgroundColor(Color.parseColor("#7A1A15"))
        } else if (!ajustes.estaConfigurada()) {
            estado.text = "Faltan datos\n\nRellene el nombre y el teléfono de abajo."
            estado.setBackgroundColor(Color.parseColor("#B45309"))
            interruptor.text = "Empezar a vigilar"
            interruptor.setBackgroundColor(Color.parseColor("#334155"))
        } else {
            estado.text = "No está vigilando"
            estado.setBackgroundColor(Color.parseColor("#334155"))
            interruptor.text = "Empezar a vigilar"
            interruptor.setBackgroundColor(Color.parseColor("#0B7A3B"))
        }
    }

    private fun guardar() {
        ajustes.nombrePersona = cPersona.text.toString().trim()
        ajustes.nombreContacto = cNombre.text.toString().trim()
        ajustes.telefonoContacto = cTelefono.text.toString().trim()
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
        refrescar()
    }

    private fun alternar() {
        guardar()
        if (!ajustes.estaConfigurada()) {
            Toast.makeText(this, "Rellene el nombre y el teléfono primero", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Rellene el nombre y el teléfono primero", Toast.LENGTH_LONG).show()
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

    private fun campo(pista: String, tipo: Int) = EditText(this).apply {
        hint = pista
        inputType = tipo
        textSize = 20f
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#6C7689"))
        setBackgroundColor(Color.parseColor("#1C2434"))
        setPadding(28, 30, 28, 30)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 14 }
    }

    private fun boton(t: String, color: Int, alPulsar: () -> Unit) = Button(this).apply {
        text = t
        textSize = 20f
        setTextColor(Color.WHITE)
        setBackgroundColor(color)
        setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 170
        ).apply { topMargin = 22 }
        setOnClickListener { alPulsar() }
    }

    private fun hueco(alto: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, alto
        )
    }
}
