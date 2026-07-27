package es.guiamayores.cuidame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * LA ONDA DEL PULSO, DIBUJADA EN DIRECTO
 * ======================================
 *
 * Enseña lo mismo que ve el analizador: los latidos como montañas, con un
 * punto en la cima de cada uno.
 *
 * POR QUE MERECE LA PENA DIBUJARLO
 *
 * Un numero al final -"49 pulsaciones, irregular"- no deja ver de donde
 * sale. Con la onda delante, un salto se ve a simple vista: si entre dos
 * montañas hay un hueco del doble de largo, ahi falta un latido, y eso
 * distingue "se me ha escapado un latido" de "el corazon va a
 * trompicones". La primera version daba el numero sin enseñar nada, y
 * cuando salio raro no habia forma de saber si fiarse.
 *
 * Y sobre todo: se ve MIENTRAS se mide, no despues. Si la onda sale plana
 * o llena de saltos, la persona recoloca el dedo en ese momento en vez de
 * perder medio minuto para nada.
 *
 * EL COLOR NO ES DECORACION
 *
 * El fondo cambia segun lo quieto que este el dedo -verde bien, naranja
 * se mueve algo, rojo se mueve mucho, gris no hay dedo-. Es la forma mas
 * rapida de decirle a alguien mayor "asi no, apoye mejor" sin que tenga
 * que leer ni interpretar nada.
 */
class GraficaPulso(contexto: Context) : View(contexto) {

    private var onda = FloatArray(0)
    private var picos = IntArray(0)
    private var firmeza = AnalizadorPulso.Firmeza.SIN_DEDO

    private val linea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.WHITE
    }
    private val punto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FBBF24")
    }
    private val fondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val guia = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#33FFFFFF")
    }
    private val camino = Path()

    fun actualizar(o: AnalizadorPulso.Onda, f: AnalizadorPulso.Firmeza) {
        onda = o.puntos
        picos = o.picos
        firmeza = f
        invalidate()
    }

    private fun colorFondo() = when (firmeza) {
        AnalizadorPulso.Firmeza.BIEN -> Color.parseColor("#0F2E1D")
        AnalizadorPulso.Firmeza.REGULAR -> Color.parseColor("#33280C")
        AnalizadorPulso.Firmeza.MAL -> Color.parseColor("#3B1310")
        AnalizadorPulso.Firmeza.SIN_DEDO -> Color.parseColor("#1C2434")
    }

    private fun colorBorde() = when (firmeza) {
        AnalizadorPulso.Firmeza.BIEN -> Color.parseColor("#22C55E")
        AnalizadorPulso.Firmeza.REGULAR -> Color.parseColor("#F59E0B")
        AnalizadorPulso.Firmeza.MAL -> Color.parseColor("#EF4444")
        AnalizadorPulso.Firmeza.SIN_DEDO -> Color.parseColor("#475569")
    }

    override fun onDraw(lienzo: Canvas) {
        super.onDraw(lienzo)
        val an = width.toFloat()
        val al = height.toFloat()

        fondo.color = colorFondo()
        lienzo.drawRoundRect(0f, 0f, an, al, 24f, 24f, fondo)
        borde.color = colorBorde()
        lienzo.drawRoundRect(3f, 3f, an - 3f, al - 3f, 24f, 24f, borde)

        // Linea central de referencia
        lienzo.drawLine(0f, al / 2f, an, al / 2f, guia)

        if (onda.size < 4) return

        // Escala automatica: el latido es pequeñisimo comparado con el
        // brillo total, asi que se estira para que se vea.
        var maximo = 0f
        for (v in onda) if (kotlin.math.abs(v) > maximo) maximo = kotlin.math.abs(v)
        if (maximo < 0.0001f) return
        val escala = (al * 0.38f) / maximo

        camino.reset()
        for (i in onda.indices) {
            val x = an * i / (onda.size - 1).toFloat()
            val y = al / 2f - onda[i] * escala
            if (i == 0) camino.moveTo(x, y) else camino.lineTo(x, y)
        }
        linea.color = colorBorde()
        lienzo.drawPath(camino, linea)

        // Un punto en la cima de cada latido. Ahi es donde se ve si falta
        // alguno: un hueco doble entre dos puntos canta a la vista.
        for (p in picos) {
            if (p < 0 || p >= onda.size) continue
            val x = an * p / (onda.size - 1).toFloat()
            val y = al / 2f - onda[p] * escala
            lienzo.drawCircle(x, y, 9f, punto)
        }
    }
}
