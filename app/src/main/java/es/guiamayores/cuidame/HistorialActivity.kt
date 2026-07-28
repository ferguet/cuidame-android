package es.guiamayores.cuidame

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * LA LISTA DE MEDICIONES
 * ======================
 *
 * Pensada para dos personas distintas:
 *
 *   - La persona mayor, que solo tiene que ver que ha ido haciendo. Letra
 *     grande, una linea por medicion, sin graficos que interpretar.
 *   - Su medico o su familia, que necesitan verlo TODO junto y poder
 *     llevarselo. De ahi el boton de compartir: manda la lista entera
 *     como texto por donde quiera -correo, WhatsApp, lo que sea-.
 *
 * El valor esta en la acumulacion. Una medicion suelta se discute; diez
 * seguidas con fechas ya no.
 */
class HistorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pintar()
    }

    override fun onResume() {
        super.onResume()
        pintar()
    }

    private fun pintar() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 44)
            setBackgroundColor(Color.parseColor("#101828"))
        }

        col.addView(t("Mis mediciones", 30f, Color.WHITE, true))

        val lista = Historial.leer(this)

        if (lista.isEmpty()) {
            col.addView(t(
                "Todavía no hay nada guardado.\n\nSe irán guardando solas las " +
                "mediciones que salgan bien hechas. Las que salen dudosas no se " +
                "guardan a propósito: una lista con datos malos es peor que no " +
                "tener lista.",
                18f, Color.parseColor("#9AA4B2")
            ))
        } else {
            col.addView(t(
                "${lista.size} mediciones guardadas. La más reciente arriba.",
                16f, Color.parseColor("#9AA4B2")
            ))

            for (e in lista) {
                val tarjeta = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(28, 24, 28, 24)
                    setBackgroundColor(color(e.tipo))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 16 }
                }
                tarjeta.addView(t("${e.fecha()}   ${e.hora()}", 15f, Color.parseColor("#CBD5E1")))
                tarjeta.addView(t(e.tipo, 20f, Color.WHITE, true))
                tarjeta.addView(t(e.resumen, 18f, Color.parseColor("#E2E8F0")))
                if (e.detalle.isNotBlank()) {
                    tarjeta.addView(t(e.detalle, 14f, Color.parseColor("#94A3B8")))
                }
                col.addView(tarjeta)
            }

            col.addView(boton("Enviar la lista al médico o a la familia",
                Color.parseColor("#1D4ED8")) { compartir() })

            col.addView(boton("Borrar todo", Color.parseColor("#7A1A15")) { confirmarBorrado() })
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101828"))
            addView(col)
        })
    }

    private fun color(tipo: String) = when {
        tipo.startsWith("Caída") -> Color.parseColor("#3B1310")
        tipo.startsWith("Pulso") -> Color.parseColor("#1C2434")
        tipo.startsWith("Ojo") -> Color.parseColor("#12303A")
        tipo.startsWith("Temblor") -> Color.parseColor("#2A2438")
        else -> Color.parseColor("#1C2434")
    }

    private fun compartir() {
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Mediciones de Cuídame")
                putExtra(Intent.EXTRA_TEXT, Historial.comoTexto(this@HistorialActivity))
            }
            startActivity(Intent.createChooser(i, "Enviar mediciones"))
        } catch (e: Exception) {}
    }

    private fun confirmarBorrado() {
        // Se pregunta antes: borrar meses de mediciones por un toque
        // accidental seria irreparable, no hay copia en ningun sitio.
        AlertDialog.Builder(this)
            .setTitle("¿Borrar todas las mediciones?")
            .setMessage("Se perderán todas las mediciones guardadas y no se pueden " +
                        "recuperar. ¿Seguro?")
            .setPositiveButton("Sí, borrar") { _, _ ->
                Historial.borrar(this); pintar()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun t(s: String, tam: Float, c: Int, negrita: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = tam; setTextColor(c)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }

    private fun boton(txt: String, c: Int, alPulsar: () -> Unit) = Button(this).apply {
        text = txt
        textSize = 19f
        setTextColor(Color.WHITE)
        setBackgroundColor(c)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 170
        ).apply { topMargin = 24 }
        setOnClickListener { alPulsar() }
    }
}
