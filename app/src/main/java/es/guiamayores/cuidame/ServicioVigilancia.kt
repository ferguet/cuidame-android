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
        const val CANAL_ALARMA = "cuidame_alarma"
        const val ID_AVISO = 1
        const val ID_ALARMA = 2
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

        /**
         * Hasta cuando no se vigilan caidas.
         *
         * Lo pone la pantalla de alarma cuando la persona dice "estoy
         * bien". Levantarse del suelo genera golpes seguidos de quietud,
         * que es exactamente lo que busca el detector, y sin esto la app
         * volvia a dar la alarma a los pocos segundos de haberla quitado.
         */
        @Volatile
        var tregua = 0L

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
            // EL SEGUNDO NUMERO -el cero- ES EL QUE QUITA EL RETRASO.
            //
            // Con la pantalla apagada, Android agrupa las lecturas de los
            // sensores y las entrega a ratos, en paquetes, para gastar
            // menos bateria. Para contar pasos es perfecto; para detectar
            // una caida es fatal: el golpe ya ha pasado y la app se entera
            // varios segundos despues. Era la causa principal de que la
            // alarma tardara tanto en saltar con el movil bloqueado.
            //
            // Ese cero es el "tiempo maximo que puedes retenerme una
            // lectura": ninguno. Que lleguen segun ocurren.
            //
            // Y se baja de FASTEST a GAME (unas 50 por segundo) a
            // proposito: con un umbral de 19 y golpes reales que pasan de
            // 100, 50 lecturas por segundo sobran para verlo, y asi no se
            // come la bateria de una app que tiene que aguantar todo el
            // dia encendida.
            sensores.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, 0)
        }
        detector.marcarMovimiento()
        // Se deja el tono de alarma cargado ahora, con tiempo de sobra,
        // para que en el momento del susto suene sin un segundo de espera.
        Sirena.preparar(this)
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
        if (ahora < rearmarDesde || ahora < tregua) {
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
            comprobarBateria()
            aprenderDondeDuerme()
        }
    }

    private fun comprobarInmovilidad(ahora: Long) {
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hora < ajustes.horaInicioDia || hora >= ajustes.horaFinDia) return

        val limite = ajustes.horasSinMoverse * 60L * 60L * 1000L
        if (ahora - detector.ultimoMovimiento > limite) {
            dispararAlarma(
                "lleva ${ajustes.horasSinMoverse} horas sin moverse",
                AlarmaActivity.TIPO_INMOVILIDAD
            )
            detector.marcarMovimiento(ahora)   // para no repetir en bucle
        }
    }

    /**
     * EL AGUJERO DE LA BATERIA.
     *
     * Una app de vigilancia que se apaga sin decir nada es peor que no
     * tener ninguna, porque la familia se queda tranquila creyendo que
     * alguien mira. Y el movil que se queda sin bateria es la causa mas
     * corriente de que eso pase: nadie se da cuenta de que ha dejado de
     * vigilar hasta que hace falta.
     *
     * Asi que cuando queda poca, se avisa al contacto -no a la persona
     * mayor, que probablemente no lo vea- de que hay que recordarle que
     * cargue el movil. Se manda una sola vez por descarga: se rearma solo
     * cuando el movil vuelve a subir del 40%.
     */
    private fun comprobarBateria() {
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val nivel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (nivel <= 0) return

            if (nivel > 40 && ajustes.avisadaBateria) {
                ajustes.avisadaBateria = false
                return
            }
            if (nivel <= 10 && !ajustes.avisadaBateria) {
                ajustes.avisadaBateria = true
                Avisador.enviar(this, Avisador.mensajeBateria(this))
                Historial.añadir(this, "Batería",
                    "quedaba un $nivel% · avisado el contacto", "")
            }
        } catch (e: Exception) {}
    }

    /**
     * De madrugada se apunta donde esta el movil: eso es la casa.
     *
     * Asi el aviso puede decir "está en casa" o "está FUERA de casa" sin
     * que nadie haya tenido que escribir una direccion en ningun sitio.
     */
    private fun aprenderDondeDuerme() {
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hora == 4) Avisador.aprenderCasa(this)
    }

    /**
     * DISPARAR LA ALARMA CON EL MOVIL BLOQUEADO
     *
     * Aqui estaba el fallo mas grave que ha tenido esta app: con la
     * pantalla apagada no avisaba hasta que alguien desbloqueaba el
     * movil. O sea, justo en la situacion real -el movil en el bolsillo
     * de alguien que se ha caido- no servia para nada.
     *
     * El motivo: desde Android 10 una app NO puede abrir una pantalla
     * estando en segundo plano. Android se la guarda y la enseña cuando
     * el usuario vuelve. Es una proteccion razonable contra apps que
     * asaltan la pantalla, pero deja fuera a las alarmas de verdad.
     *
     * La via correcta -la misma que usan los despertadores y las
     * llamadas entrantes- es una notificacion "de pantalla completa".
     * Android SI la deja saltar sobre la pantalla de bloqueo.
     *
     * Y por si acaso, primero se hace sonar la sirena. Aunque el sistema
     * no dejara pintar nada, el movil suena. Que suene importa mas que
     * lo que se vea: lo oye la persona y lo oye quien este en la casa.
     */
    private fun dispararAlarma(motivo: String, tipo: String = "caida") {
        rearmarDesde = System.currentTimeMillis() + 4000L
        detector.reiniciar()
        detector.marcarMovimiento()

        Sirena.sonar(this)

        val i = Intent(this, AlarmaActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmaActivity.EXTRA_MOTIVO, motivo)
            putExtra(AlarmaActivity.EXTRA_TIPO, tipo)
        }
        val pi = PendingIntent.getActivity(
            this, 1, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val aviso = NotificationCompat.Builder(this, CANAL_ALARMA)
            .setContentTitle("¿Está usted bien?")
            .setContentText("Toque aquí. Si no contesta, avisaré a su contacto.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)   // esto es lo que la saca sobre el bloqueo
            .build()

        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID_ALARMA, aviso)
        } catch (e: Exception) {}

        // Y ademas se intenta abrir directamente: si la app estaba en
        // primer plano, asi sale al instante sin pasar por el aviso.
        try { startActivity(i) } catch (e: Exception) {}
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val canal = NotificationChannel(
                CANAL, "Vigilancia", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Aviso permanente mientras la app vigila" }
            nm.createNotificationChannel(canal)

            // Canal aparte para la alarma, con importancia MAXIMA. Sin
            // esto Android no deja que la notificacion salte sola sobre
            // la pantalla de bloqueo, y volveriamos al problema de que no
            // avisa hasta desbloquear.
            val alarma = NotificationChannel(
                CANAL_ALARMA, "Alarma de caída", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Salta cuando se detecta una caída"
                setBypassDnd(true)          // suena aunque este en 'no molestar'
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }
            nm.createNotificationChannel(alarma)
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
