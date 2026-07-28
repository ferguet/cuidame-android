package es.guiamayores.cuidame

import android.graphics.Bitmap
import android.graphics.Color

/**
 * EL COLOR DEL OJO
 * ================
 *
 * Mira una foto del ojo y saca dos cosas:
 *
 *   - Si la parte blanca tira a amarilla  -> posible ictericia (higado
 *     o vias biliares). Tiene la particularidad de que uno NO se lo nota
 *     a si mismo: se ve los ojos cada dia y el amarilleo es gradual.
 *   - Si la carne de dentro del parpado esta palida -> posible anemia.
 *     Muy frecuente en mayores y casi siempre silenciosa: el cansancio y
 *     la falta de aire se achacan a la edad.
 *
 * EL TRUCO: EL OJO SE COMPARA CONSIGO MISMO
 *
 * El problema de medir color con un movil es que la camara corrige el
 * balance de blancos por su cuenta, asi que el mismo ojo sale distinto en
 * la cocina que junto a la ventana. Medir un color absoluto no funciona.
 *
 * La solucion es no medir colores sino RELACIONES. En la misma foto salen
 * la parte blanca del ojo y la carne del parpado, y las dos reciben
 * exactamente la misma luz. Al dividir una entre otra, la iluminacion se
 * cancela sola: da igual que la habitacion tirara a amarillo, porque
 * afectaba a las dos por igual.
 *
 * HASTA DONDE LLEGA, DICHO SIN ADORNOS
 *
 * Los mejores trabajos publicados con fotos de conjuntiva rondan el 72-75%
 * de acierto para anemia. Eso no es un analisis de sangre ni se le parece.
 * Los cortes que uso aqui son aproximados, no verdades.
 *
 * Por eso lo que de verdad entrega esta pantalla no es el numero: es la
 * FOTO, guardada para enseñarsela al medico, y una escala de color al
 * lado para comparar a ojo. Un medico mira una conjuntiva palida y lo ve
 * en dos segundos, sin necesitar mi algoritmo para nada.
 */
object AnalizadorColor {

    class Resultado(
        val hayBlanco: Boolean,
        val hayRojo: Boolean,
        val amarilleo: Double,      // 0 = blanco puro, sube al amarillear
        val rojez: Double,          // alto = bien irrigado, bajo = palido
        val colorBlanco: Int,       // color medio medido, para enseñarlo
        val colorRojo: Int,
        val pixelesBlanco: Int,
        val pixelesRojo: Int
    )

    /**
     * @param foto la imagen del ojo, ya recortada a la zona util
     */
    fun analizar(foto: Bitmap): Resultado {
        // Se trabaja en pequeño: no hace falta resolucion para medir un
        // color medio, y asi no se atasca el movil.
        val ancho = 360
        val alto = (foto.height * ancho / foto.width.toDouble()).toInt().coerceAtLeast(1)
        val chico = Bitmap.createScaledBitmap(foto, ancho, alto, true)

        var rB = 0.0; var gB = 0.0; var bB = 0.0; var nB = 0
        var rR = 0.0; var gR = 0.0; var bR = 0.0; var nR = 0

        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                val p = chico.getPixel(x, y)
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                val maximo = maxOf(r, g, b); val minimo = minOf(r, g, b)
                val brillo = (r + g + b) / 3.0
                val saturacion = if (maximo > 0) (maximo - minimo) / maximo.toDouble() else 0.0

                // LA PARTE BLANCA DEL OJO: clara y con poco color.
                // Se pide brillo alto para no confundirla con la piel, que
                // es mas apagada y bastante mas saturada.
                if (brillo > 115 && saturacion < 0.28) {
                    rB += r; gB += g; bB += b; nB++
                }
                // LA CARNE DEL PARPADO: el rojo manda con claridad sobre
                // los otros dos. Se descartan los extremos: lo muy oscuro
                // son pestañas y sombras, lo muy claro es reflejo.
                else if (r > g * 1.18 && r > b * 1.18 && brillo > 45 && brillo < 225) {
                    rR += r; gR += g; bR += b; nR++
                }
            }
        }

        val totales = ancho * alto
        val hayBlanco = nB > totales * 0.02
        val hayRojo = nR > totales * 0.02

        if (!hayBlanco && !hayRojo) {
            return Resultado(false, false, 0.0, 0.0, Color.GRAY, Color.GRAY, nB, nR)
        }

        val Rs = if (nB > 0) rB / nB else 1.0
        val Gs = if (nB > 0) gB / nB else 1.0
        val Bs = if (nB > 0) bB / nB else 1.0
        val Rc = if (nR > 0) rR / nR else 0.0
        val Gc = if (nR > 0) gR / nR else 0.0
        val Bc = if (nR > 0) bR / nR else 0.0

        // AMARILLEO de la parte blanca.
        // Amarillo = rojo y verde altos con el azul hundido. Cuanto mas
        // se hunde el azul respecto a los otros dos, mas amarillo.
        val amarilleo = if (hayBlanco) {
            val rojoVerde = (Rs + Gs) / 2.0
            if (rojoVerde > 0) ((rojoVerde - Bs) / rojoVerde).coerceIn(0.0, 1.0) else 0.0
        } else 0.0

        // ROJEZ de la carne, normalizada con la parte blanca.
        // Aqui es donde se cancela la iluminacion: cada canal de la carne
        // se divide por el mismo canal del blanco de al lado.
        val rojez = if (hayBlanco && hayRojo && Rs > 0 && Gs > 0 && Bs > 0) {
            val rn = Rc / Rs
            val gn = Gc / Gs
            val bn = Bc / Bs
            val otros = (gn + bn) / 2.0
            if (otros > 0) rn / otros else 0.0
        } else 0.0

        return Resultado(
            hayBlanco, hayRojo, amarilleo, rojez,
            Color.rgb(Rs.toInt().coerceIn(0, 255), Gs.toInt().coerceIn(0, 255), Bs.toInt().coerceIn(0, 255)),
            Color.rgb(Rc.toInt().coerceIn(0, 255), Gc.toInt().coerceIn(0, 255), Bc.toInt().coerceIn(0, 255)),
            nB, nR
        )
    }

    // ---- Interpretaciones. Cortes APROXIMADOS, no verdades absolutas ----

    fun textoAmarilleo(a: Double): String = when {
        a < 0.10 -> "La parte blanca del ojo se ve blanca."
        a < 0.18 -> "La parte blanca tira un poco a amarilla. Puede ser la luz " +
                    "de la habitación. Repítalo con luz de día, cerca de una ventana."
        else -> "La parte blanca se ve claramente amarillenta.\n\n" +
                "Esto conviene que lo vea un médico: el amarilleo de los ojos puede " +
                "venir del hígado o de la vía biliar, y es de esas cosas que uno no " +
                "se nota a sí mismo. Enséñele esta foto y pídale un análisis."
    }

    fun textoRojez(r: Double, hay: Boolean): String = when {
        !hay -> "No he podido ver bien la carne de dentro del párpado. " +
                "Bájese el párpado de abajo con el dedo y repita la foto."
        r > 1.45 -> "La carne del párpado se ve bien coloreada."
        r > 1.25 -> "Color intermedio. No dice gran cosa por sí solo."
        else -> "La carne del párpado se ve pálida.\n\n" +
                "La palidez ahí puede indicar anemia, que en personas mayores es " +
                "frecuente y suele pasar desapercibida: el cansancio y la falta de " +
                "aire al subir escaleras se achacan a la edad. Se confirma con un " +
                "análisis de sangre sencillo. Enséñele esta foto a su médico."
    }
}
