package es.guiamayores.cuidame

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * MODO COCHE
 * ==========
 *
 * DE QUE VA ESTO, Y DE QUE NO VA
 *
 * Esto NO es un detector de accidentes. Un movil no puede hacer eso bien:
 * no nota la presion del airbag, su acelerometro se topa doce veces por
 * debajo de lo que hace falta, y desde 2018 los coches nuevos ya llaman
 * solos al 112 mejor de lo que podria hacerlo una app.
 *
 * Lo que si puede hacer un movil, y muy bien, es CONTAR LOS FRENAZOS.
 *
 * Y ahi esta lo interesante, que ademas encaja con todo lo demas de esta
 * app: el equilibrio, el temblor y el pulso no estan para avisar de una
 * urgencia, estan para ver si algo va empeorando poco a poco. Los
 * frenazos son exactamente eso mismo aplicado a conducir. Cuando alguien
 * empieza a perder reflejos o vista, lo primero que aparece no son los
 * golpes: son los sustos. Frenazos que antes no daba.
 *
 * Nadie tiene ese dato hoy. Una familia sabe que el abuelo tuvo un roce
 * hace dos años, pero no sabe si este mes ha dado seis frenazos y el
 * anterior ninguno. Eso es una conversacion que se puede tener a tiempo,
 * en vez de despues.
 *
 * UN FRENAZO NO ES CONDUCIR MAL
 *
 * Esto hay que decirlo dentro del codigo para que no se olvide al enseñar
 * los datos: quien frena fuerte a lo mejor acaba de evitar un atropello.
 * Un frenazo suelto no significa NADA y no debe hacer que nadie se
 * justifique. Lo unico que dice algo es la tendencia, y comparada consigo
 * misma.
 *
 * Y DE PASO ARREGLA UN FALLO
 *
 * Mientras se conduce, la deteccion de caidas se apaga. Hacia falta: un
 * motor en marcha vibra de una forma muy parecida a como respira una
 * persona, asi que un bache podia dar "golpe + quieto pero vivo" y
 * disparar un aviso falso con la persona conduciendo tan tranquila.
 */
class ModoCoche(private val contexto: Context) : LocationListener {

    enum class Estado { PARADO, COMPROBANDO, CONDUCIENDO }

    var estado = Estado.PARADO; private set
    var velocidadKmh = 0f; private set
    var frenazosHoy = 0; private set

    /** Ultimo frenazo medido, en m/s2. Para la pantalla de sensores. */
    var ultimoFrenazo = 0f; private set

    private var lm: LocationManager? = null
    private var escuchando = false

    private var velocidadAnterior = -1f      // m/s
    private var instanteAnterior = 0L
    private var noReintentarHasta = 0L
    private var comprobandoDesde = 0L
    private var ultimoMovimientoCoche = 0L

    /**
     * A partir de cuanto se considera un frenazo, en m/s2.
     *
     * Frenar normal en ciudad son 1,5-2,5. Frenar con ganas para no
     * pasarse un ceda el paso, unos 4. A partir de 5,5 ya es un frenazo de
     * los que echan el cuerpo hacia delante y el cinturon se bloquea:
     * eso, o ha pasado algo, o ha faltado poco.
     *
     * Se pone deliberadamente alto. Contar de mas convertiria el dato en
     * ruido y nadie volveria a mirarlo.
     */
    private val umbralFrenazo = 5.5f

    /** Por debajo de esta velocidad no se cuenta nada: es maniobrar. */
    private val minimoParaContar = 6.0f      // m/s, unos 21 km/h

    // -----------------------------------------------------------------

    /**
     * Se llama una vez por minuto desde el servicio.
     *
     * @param hayMovimiento si el acelerometro esta viendo movimiento
     *        continuado. Sirve de filtro barato: si el movil lleva un rato
     *        completamente quieto en una mesa, no tiene ningun sentido
     *        encender el GPS para ver si va en coche. El GPS es de lo que
     *        mas bateria gasta de un movil, y esta app tiene que aguantar
     *        el dia entero.
     */
    fun latido(hayMovimiento: Boolean) {
        val ahora = System.currentTimeMillis()

        when (estado) {
            Estado.PARADO -> {
                if (!hayMovimiento || ahora < noReintentarHasta) return
                estado = Estado.COMPROBANDO
                comprobandoDesde = ahora
                empezarAEscuchar()
            }

            Estado.COMPROBANDO -> {
                // Minuto y medio para confirmar. Si en ese rato no se ha
                // visto velocidad de coche, se apaga el GPS y no se vuelve
                // a mirar en un cuarto de hora: andar por casa tambien da
                // movimiento y no queremos el GPS encendido todo el dia
                // por eso.
                if (ahora - comprobandoDesde > 90_000L) {
                    pararDeEscuchar()
                    estado = Estado.PARADO
                    noReintentarHasta = ahora + 15 * 60_000L
                }
            }

            Estado.CONDUCIENDO -> {
                // Cinco minutos parado y se acabo el viaje.
                if (ahora - ultimoMovimientoCoche > 5 * 60_000L) {
                    pararDeEscuchar()
                    estado = Estado.PARADO
                    velocidadKmh = 0f
                    velocidadAnterior = -1f
                }
            }
        }
    }

    fun parar() {
        pararDeEscuchar()
        estado = Estado.PARADO
    }

    // -----------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun empezarAEscuchar() {
        if (escuchando) return
        if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val m = contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            lm = m
            escuchando = true
        } catch (e: Exception) {}
    }

    private fun pararDeEscuchar() {
        try { lm?.removeUpdates(this) } catch (e: Exception) {}
        escuchando = false
    }

    override fun onLocationChanged(sitio: Location) {
        // Sin velocidad del GPS no hay nada que calcular. Se podria sacar
        // de la distancia entre dos puntos, pero sale mucho mas sucia: un
        // salto de posicion de veinte metros bajo un puente se convertiria
        // en un frenazo inventado.
        if (!sitio.hasSpeed()) return

        val v = sitio.speed                       // m/s
        val ahora = sitio.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        velocidadKmh = v * 3.6f

        if (v > 3f) ultimoMovimientoCoche = System.currentTimeMillis()

        // Entrar en modo coche: 25 km/h no se alcanzan andando ni
        // corriendo, asi que es una frontera limpia.
        if (estado == Estado.COMPROBANDO && v > 7f) {
            estado = Estado.CONDUCIENDO
            ultimoMovimientoCoche = System.currentTimeMillis()
        }

        if (velocidadAnterior >= 0f && instanteAnterior > 0L) {
            val dt = (ahora - instanteAnterior) / 1000f
            if (dt in 0.4f..3.0f && velocidadAnterior > minimoParaContar) {
                val deceleracion = (velocidadAnterior - v) / dt
                if (deceleracion > umbralFrenazo) {
                    ultimoFrenazo = deceleracion
                    anotarFrenazo(velocidadAnterior, v, deceleracion)
                }
            }
        }
        velocidadAnterior = v
        instanteAnterior = ahora
    }

    private fun anotarFrenazo(antes: Float, despues: Float, deceleracion: Float) {
        frenazosHoy++
        Historial.añadir(
            contexto,
            "Frenazo",
            "de ${(antes * 3.6f).toInt()} a ${(despues * 3.6f).toInt()} km/h",
            "fuerza " + String.format("%.1f", deceleracion / 9.81f) + " g"
        )
    }

    /**
     * ¿Esto ha pintado de choque?
     *
     * Se pide TODO a la vez: iba deprisa, se ha quedado parado de golpe, y
     * ademas el acelerometro ha notado un impacto. Cualquiera de las tres
     * cosas por separado pasa a diario -un frenazo fuerte, un semaforo,
     * que se caiga el movil-; las tres juntas y en el mismo segundo, no.
     *
     * Aun asi esto NO es un detector de accidentes de fiar, y no debe
     * venderse como tal. Es una sospecha razonable que hace que la app
     * pregunte, y de la pregunta ya se encarga el minuto de confirmacion
     * de siempre.
     */
    fun pareceChoque(golpe: Float): Boolean =
        estado == Estado.CONDUCIENDO &&
        ultimoFrenazo > 9.0f &&
        velocidadKmh < 8f &&
        golpe > 18f

    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
    @Deprecated("Obligatorio en Android 7 y 8")
    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
}
