package es.guiamayores.cuidame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * DIBUJO DE LA POSICION DE LOS PIES
 * =================================
 *
 * Explicar con palabras donde va cada pie es sorprendentemente dificil.
 * "El talon de un pie tocando el dedo gordo del otro" hay que leerlo dos
 * veces aunque uno lea bien; con la vista cansada y de pie, intentando no
 * perder el equilibrio, no hay quien lo siga.
 *
 * Un dibujo de dos pies vistos desde arriba se entiende de un vistazo y no
 * hay que leer nada. Es la misma idea que los botones con icono de la
 * pantalla de casa: para quien no ve bien, una forma vale mas que una
 * frase.
 *
 * Se dibuja a mano en vez de usar una imagen para que se vea igual de
 * nitido en cualquier movil, y para que la app no engorde.
 */
class DibujoPies(c: Context, private val posicion: Int) : View(c) {

    /** 0 = pies juntos, 1 = semi-tandem, 2 = tandem. */

    private val pintura = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val ancho = width.toFloat()
        val alto = height.toFloat()
        if (ancho <= 0f || alto <= 0f) return

        // Un pie: alto = 45% del dibujo, ancho = 16%.
        val largoPie = alto * 0.45f
        val anchoPie = ancho * 0.16f
        val centroX = ancho / 2f
        val centroY = alto / 2f

        // Cada postura es solo dos numeros: cuanto se separan de lado y
        // cuanto se adelanta uno respecto al otro.
        val (separacion, adelanto) = when (posicion) {
            0 -> Pair(anchoPie * 0.55f, 0f)                  // juntos
            1 -> Pair(anchoPie * 0.55f, largoPie * 0.50f)    // semi-tandem
            else -> Pair(0f, largoPie * 1.00f)               // tandem, en linea
        }

        dibujarPie(canvas, centroX - separacion, centroY + adelanto / 2f,
            anchoPie, largoPie, Color.parseColor("#38BDF8"))
        dibujarPie(canvas, centroX + separacion, centroY - adelanto / 2f,
            anchoPie, largoPie, Color.parseColor("#A78BFA"))

        // Linea del suelo, para que se entienda que es visto desde arriba.
        pintura.color = Color.parseColor("#25314A")
        pintura.strokeWidth = 3f
        canvas.drawLine(ancho * 0.08f, alto * 0.94f, ancho * 0.92f, alto * 0.94f, pintura)
    }

    private fun dibujarPie(c: Canvas, x: Float, y: Float, an: Float, la: Float, color: Int) {
        pintura.color = color
        pintura.style = Paint.Style.FILL
        // El pie: un ovalo alargado (planta) y un circulito delante (dedos).
        c.drawRoundRect(
            RectF(x - an / 2f, y - la / 2f, x + an / 2f, y + la / 2f),
            an / 2f, an / 2f, pintura
        )
        c.drawCircle(x, y - la / 2f + an * 0.35f, an * 0.30f, pintura.apply {
            this.color = Color.parseColor("#0B1220")
        })
        pintura.color = color
    }
}
