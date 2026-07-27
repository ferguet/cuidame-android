package es.guiamayores.cuidame

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * EL VIGILANTE
 * ============
 *
 * Escucha el acelerometro con la pantalla apagada y el movil en el
 * bolsillo. Android obliga a que esto sea un servicio "en primer plano"
 * con un aviso permanente y visible, y me parece lo correcto: la persona
 * tiene que ver en todo momento que la app esta mirando. Aqui no hay
 * vigilancia escondida.
 *
 * VIGILA DOS COSAS DISTINTAS
 *
 * 1. CAIDAS. El golpe seguido de quietud (ver DetectorCaida).
 *
 * 2. FALTA DE ACTIVIDAD. Esta es la que menos se ve venir y la que mas
 *    gente encuentra a tiempo. Un detector de caidas no sirve de nada si
 *    a la persona le da un ictus sentada en el sofa, o se encuentra mal
 *    y se acuesta, o se desmaya con el movil en la mesa. Pero un movil
 *    que lleva cuatro horas sin registrar ni un movimiento un martes a
 *    las once de la mañana SI dice algo. Y casi no da falsas alarmas.
 *
 *    De noche no se vigila: lo raro seria moverse.
 */
class ServicioVigilancia : Service(), SensorEventListener {

    private lateinit var sensores: SensorManager
    private var acelerometro: Sensor? = null
    private val detector = DetectorCaida()
    private lateinit var ajustes: Ajustes
    private var wakeLock: PowerManager.WakeLock? = null

    private var rearmarDesde = 0L
    private var ultimaComprobacionQuietud = 0L

    companion object {
        const val CANAL = "cuidame_vigilancia"
        const val ID_AVISO = 1
        const val ACCION_PARAR = "es.guiamayores.cuidame.PARAR"

        /**
         * Si el servicio esta VIVO de verdad, no solo si el ajuste dice
         * que si. Android puede matar un servicio, o puede fallar al
         * arrancarlo por un permiso, y entonces la pantalla de casa diria
         * "vigilando" mientras en realidad no vigila nada. Esa mentira es
         * lo peor que puede hacer una app de seguridad.
         */
        @Volatile
        var activo = false
            internal set

        fun arrancar(contexto: Context) {
            val i = Intent(contexto, ServicioVigilancia::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                contexto.startForegroundService(i)
            } else {
                contexto.startService(i)
            }
        }

        fun parar(contexto: Context) {
            contexto.stopService(Intent(contexto, ServicioVigilancia::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        ajustes = Ajustes(this)
        sensores = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensores.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        crearCanal()
        startForeground(ID_AVISO, construirAviso("Vigilando por usted"))

        // Sin esto, algunos moviles duermen el sensor a los pocos minutos
        // de apagar la pantalla y la vigilancia se queda muerta sin avisar.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cuidame:vigilancia").apply {
            setReferenceCounted(false)
            acquire()
        }

        // El umbral del golpe se ajusta a lo que ESTE movil sabe medir.
        // Un sensor de rango corto nunca llegaria a un umbral fijo alto,
        // y la app se pasaria la vida sin detectar nada.
        detector.ajustarAlSensor(acelerometro?.maximumRange ?: 0f)

        acelerometro?.let {
            // SENSOR_DELAY_FASTEST y no GAME. El pico de un golpe dura muy
            // poco -a veces menos de 20 milisegundos-, y a 50 lecturas por
            // segundo se cuela entre dos muestras sin que lo veamos. Es una
            // de las razones por las que la primera version no detectaba
            // caidas. Gasta algo mas de bateria y merece la pena.
            sensores.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        detector.marcarMovimiento()
        activo = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_PARAR) {
            ajustes.vigilanciaActiva = false
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY: si Android mata el servicio por falta de memoria,
        // que lo vuelva a levantar solo.
        return START_STICKY
    }

    override fun onDestroy() {
        activo = false
        try { sensores.unregisterListener(this) } catch (e: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(evento: SensorEvent?) {
        if (evento == null || evento.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val ahora = System.currentTimeMillis()

        // Mientras la alarma esta en pantalla no se vigila: la persona ya
        // esta atendida. Y al cerrarse, se deja un respiro de 4 segundos
        // para que el manoteo de pulsar el boton no cuente como caida.
        //
        // Antes esto era un temporizador fijo de 90 segundos, y era un
        // fallo grave: aunque la persona dijera "estoy bien" al instante,
        // la app se quedaba sorda minuto y medio. Justo el rato en que es
        // mas facil volver a caerse, porque uno se levanta mareado.
        if (AlarmaActivity.visible) {
            rearmarDesde = ahora + 4000L
            detector.reiniciar()
            detector.marcarMovimiento(ahora)
            return
        }
        if (ahora < rearmarDesde) {
            detector.marcarMovimiento(ahora)
            return
        }

        if (detector.procesar(evento.values[0], evento.values[1], evento.values[2], ahora)) {
            dispararAlarma("parece que se ha caído")
            return
        }

        // La inmovilidad se mira una vez por minuto: no hace falta mas y
        // asi no se gasta bateria en cuentas continuas.
        if (ahora - ultimaComprobacionQuietud > 60_000L) {
            ultimaComprobacionQuietud = ahora
            comprobarInmovilidad(ahora)
        }
    }

    private fun comprobarInmovilidad(ahora: Long) {
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hora < ajustes.horaInicioDia || hora >= ajustes.horaFinDia) return

        val limite = ajustes.horasSinMoverse * 60L * 60L * 1000L
        if (ahora - detector.ultimoMovimiento > limite) {
            dispararAlarma("lleva ${ajustes.horasSinMoverse} horas sin moverse")
            detector.marcarMovimiento(ahora)   // para no repetir en bucle
        }
    }

    private fun dispararAlarma(motivo: String) {
        rearmarDesde = System.currentTimeMillis() + 4000L
        val i = Intent(this, AlarmaActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmaActivity.EXTRA_MOTIVO, motivo)
        }
        startActivity(i)
        detector.reiniciar()
        detector.marcarMovimiento()
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL, "Vigilancia", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Aviso permanente mientras la app vigila" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(canal)
        }
    }

    private fun construirAviso(texto: String): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Cuídame")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(abrir)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
