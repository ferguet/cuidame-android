package es.guiamayores.cuidame

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * LA PANTALLA DE LA PULSERA
 *
 * Enseña los ultimos siete dias y, arriba del todo, la unica frase que de
 * verdad hace falta leer: si esta andando menos que antes. Los numeros
 * sueltos casi nadie sabe interpretarlos; "anda bastante menos que la
 * semana pasada" lo entiende cualquiera y es ademas lo que importa.
 */
class PulseraActivity : AppCompatActivity() {

    private lateinit var raiz: LinearLayout

    private val pedirPermisos =
        registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { concedidos ->
            if (concedidos.containsAll(Pulsera.PERMISOS)) cargar()
            else pintarFalta("No se han dado los permisos, así que no puedo leer nada de la pulsera.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 56, 48, 56)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101828"))
            addView(raiz)
        })
        arrancar()
    }

    private fun arrancar() {
        when (Pulsera.estado(this)) {
            Pulsera.Estado.NO_DISPONIBLE -> {
                pintarFalta(
                    "Este móvil no tiene Health Connect, que es el almacén de salud de Android " +
                    "por donde pasan los datos de la pulsera.\n\n" +
                    "Health Connect viene de serie en Android 14 y se puede instalar en Android 9 " +
                    "y posteriores."
                )
                return
            }
            Pulsera.Estado.NO_INSTALADO -> {
                pintarFalta("Hay que instalar o actualizar Health Connect.")
                raiz.addView(boton("Instalar Health Connect", "#1D4ED8") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                            "market://details?id=com.google.android.apps.healthdata"
                        )))
                    } catch (e: Exception) {}
                })
                return
            }
            Pulsera.Estado.LISTO -> {}
        }

        lifecycleScope.launch {
            if (Pulsera.tienePermisos(this@PulseraActivity)) cargar()
            else pedirPermiso()
        }
    }

    private fun pedirPermiso() {
        raiz.removeAllViews()
        raiz.addView(texto("La pulsera", 32f, Color.WHITE, true))
        raiz.addView(texto(
            "Para leer los pasos, el pulso y el sueño que mide la pulsera hace falta su " +
            "permiso.\n\nNada de esto sale del móvil: se lee de Health Connect, aquí dentro, " +
            "y se usa solo para ver si algo va cambiando con el tiempo.",
            18f, Color.parseColor("#9AA4B2")
        ))
        raiz.addView(boton("DAR PERMISO", "#0B7A3B") {
            try { pedirPermisos.launch(Pulsera.PERMISOS) } catch (e: Exception) {}
        })
        raiz.addView(texto(
            "Antes de esto, en la app de la pulsera (Mi Fitness) hay que entrar en " +
            "Perfil → Ajustes → Cuentas → Health Connect y activar pasos, pulso y sueño. " +
            "Si eso no está hecho, aquí no aparecerá nada aunque dé el permiso.",
            15f, Color.parseColor("#6C7689")
        ))
    }

    private fun cargar() {
        raiz.removeAllViews()
        raiz.addView(texto("La pulsera", 32f, Color.WHITE, true))
        raiz.addView(texto("Leyendo los últimos días…", 18f, Color.parseColor("#9AA4B2")))

        lifecycleScope.launch {
            val dias = try { Pulsera.ultimosDias(this@PulseraActivity, 7) } catch (e: Exception) { emptyList() }
            pintar(dias)
        }
    }

    private fun pintar(dias: List<Pulsera.Dia>) {
        raiz.removeAllViews()
        raiz.addView(texto("La pulsera", 32f, Color.WHITE, true))

        val conAlgo = dias.any { it.pasos != null || it.pulsoReposo != null || it.minutosDormido != null }
        if (!conAlgo) {
            raiz.addView(tarjeta(
                "No llega ningún dato.\n\nCasi siempre es porque la app de la pulsera no está " +
                "mandando nada a Health Connect todavía.",
                "#B45309"
            ))
            raiz.addView(texto(
                "En Mi Fitness: Perfil → Ajustes → Cuentas → Health Connect, y activar " +
                "pasos, pulso y sueño. Luego abra la app de la pulsera una vez para que " +
                "sincronice, y vuelva aquí.",
                17f, Color.parseColor("#9AA4B2")
            ))
            raiz.addView(boton("VOLVER A MIRAR", "#1D4ED8") { cargar() })
            return
        }

        // LO PRIMERO, LA CONCLUSION. Los numeros vienen despues.
        Pulsera.tendenciaPasos(dias)?.let {
            raiz.addView(tarjeta(it, if (it.startsWith("⚠️")) "#B45309" else "#0B7A3B"))
        }

        raiz.addView(hueco(20))
        raiz.addView(texto("ÚLTIMOS DÍAS", 13f, Color.parseColor("#7E8AA0")))

        for (d in dias) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 22, 24, 22)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1C2434")); cornerRadius = 18f
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10 }
            }
            fila.addView(celda(d.fecha, 0.8f, Color.parseColor("#9AA4B2")))
            fila.addView(celda(d.pasos?.let { "$it" } ?: "—", 1.2f, Color.WHITE))
            fila.addView(celda(d.pulsoReposo?.let { "$it" } ?: "—", 0.8f, Color.parseColor("#F87171")))
            fila.addView(celda(
                d.minutosDormido?.let { "${it / 60}h ${it % 60}m" } ?: "—",
                1f, Color.parseColor("#38BDF8")
            ))
            raiz.addView(fila)
        }

        raiz.addView(texto(
            "Día · pasos · pulso en reposo · dormido",
            14f, Color.parseColor("#6C7689")
        ))

        raiz.addView(hueco(24))
        raiz.addView(texto(
            "El pulso que se enseña es el de los ratos más tranquilos del día, no la media: " +
            "la media mezcla el sofá con subir la escalera y no sirve para comparar días.\n\n" +
            "Del sueño solo se enseña cuánto ha dormido. Los porcentajes de \"sueño profundo\" " +
            "que dan estas pulseras no aguantan la comparación con un estudio de sueño de " +
            "verdad, así que prefiero no enseñarlos.",
            14f, Color.parseColor("#6C7689")
        ))

        raiz.addView(boton("ACTUALIZAR", "#1D4ED8") { cargar() })
    }

    private fun pintarFalta(mensaje: String) {
        raiz.removeAllViews()
        raiz.addView(texto("La pulsera", 32f, Color.WHITE, true))
        raiz.addView(tarjeta(mensaje, "#B45309"))
    }

    // ---------- ayudas ----------

    private fun celda(t: String, peso: Float, color: Int) = TextView(this).apply {
        text = t
        textSize = 18f
        setTextColor(color)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, peso)
    }

    private fun texto(t: String, tam: Float, color: Int, negrita: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = tam; setTextColor(color)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }

    private fun tarjeta(t: String, colorHex: String) = TextView(this).apply {
        text = t
        textSize = 19f
        setTextColor(Color.WHITE)
        setPadding(30, 30, 30, 30)
        background = GradientDrawable().apply {
            setColor(Color.parseColor(colorHex)); cornerRadius = 26f
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 }
    }

    private fun boton(t: String, colorHex: String, alPulsar: () -> Unit) = Button(this).apply {
        text = t
        textSize = 20f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(Color.parseColor(colorHex)); cornerRadius = 26f
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 170
        ).apply { topMargin = 26 }
        setOnClickListener { alPulsar() }
    }

    private fun hueco(alto: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, alto)
    }
}
