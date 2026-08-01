package es.guiamayores.cuidame

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * QUIEN VIGILA AL VIGILANTE
 * =========================
 *
 * EL FALLO QUE TAPA, QUE ES EL PEOR DE TODOS
 *
 * Toda esta app se apoya en un servicio que tiene que estar vivo. Y en un
 * movil Android eso no esta garantizado ni de lejos: Xiaomi, Huawei, Oppo
 * y compañia llevan ahorradores de bateria muy agresivos que cierran
 * servicios en segundo plano a las pocas horas, sin avisar a nadie. Es una
 * decision razonable para el 99% de las apps y es un desastre para esta.
 *
 * Y aqui esta lo grave: si el proceso muere, TODO lo que hemos construido
 * para no mentir muere con el. La pantalla de casa comprueba
 * ServicioVigilancia.activo, pero esa comprobacion vive dentro del mismo
 * proceso que acaba de morir. Un muerto no puede avisar de su muerte.
 * Resultado: la familia tan tranquila creyendo que alguien vigila.
 *
 * Es exactamente el mismo fallo que llevamos toda la semana persiguiendo
 * -parecer que se vigila sin vigilar- pero en su version definitiva.
 *
 * COMO SE RESUELVE
 *
 * Con una alarma del sistema. Las alarmas de AlarmManager las guarda
 * ANDROID, no la app, asi que sobreviven a que el proceso muera. Cada
 * cuarto de hora despiertan a este receptor, que hace dos cosas:
 *
 *   1. Si la vigilancia deberia estar puesta y el servicio no esta vivo,
 *      lo vuelve a levantar. Sin preguntar y sin molestar a nadie.
 *
 *   2. Si ademas resulta que llevaba horas caido, avisa al contacto. Que
 *      es lo que de verdad importa: no que se caiga -eso va a pasar-,
 *      sino que nadie se entere de que se cayo.
 *
 * Se usa setAndAllowWhileIdle a proposito y no una alarma exacta: las
 * exactas necesitan un permiso especial desde Android 12 y aqui no hacen
 * falta. Da igual que el repaso salga a los 15 minutos o a los 20; lo que
 * importa es que salga aunque el movil este durmiendo.
 */
class Vigilante : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent?) {
        val ajustes = Ajustes(contexto)
        try {
            programar(contexto)          // la siguiente ronda, siempre

            if (!ajustes.vigilanciaActiva || !ajustes.estaConfigurada()) return

            val ahora = System.currentTimeMillis()
            val silencio = ahora - ajustes.ultimoLatido

            if (!ServicioVigilancia.activo) {
                // AVISAR ANTES DE ARREGLAR.
                //
                // Se mira el silencio antes de relanzar, porque en cuanto
                // el servicio arranque pondra un latido nuevo y se perdera
                // la prueba de cuanto tiempo estuvo muerto. Y ese dato es
                // justo el que hay que contar.
                if (ajustes.ultimoLatido > 0L && silencio > 2 * 60 * 60 * 1000L &&
                    ahora - ajustes.ultimoAvisoCaido > 12 * 60 * 60 * 1000L
                ) {
                    ajustes.ultimoAvisoCaido = ahora
                    val quien = ajustes.nombrePersona.ifBlank { "la persona" }
                    Avisador.enviar(
                        contexto,
                        "CUIDAME. La aplicación del móvil de $quien ha estado " +
                        "${silencio / 3_600_000} horas SIN VIGILAR: el móvil la cerró para " +
                        "ahorrar batería. Ya la he vuelto a poner en marcha, pero conviene " +
                        "entrar en los ajustes del móvil y quitarle a Cuídame el ahorro de batería, " +
                        "o volverá a pasar."
                    )
                    Historial.añadir(
                        contexto, "La app se paró",
                        "estuvo ${silencio / 3_600_000} h sin vigilar", "avisado el contacto"
                    )
                }
                ServicioVigilancia.arrancar(contexto)
            }
        } catch (e: Exception) {}
    }

    companion object {

        private const val CODIGO = 4242

        fun programar(c: Context) {
            try {
                val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = PendingIntent.getBroadcast(
                    c, CODIGO, Intent(c, Vigilante::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val cuando = System.currentTimeMillis() + 15 * 60_000L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, cuando, pi)
                }
            } catch (e: Exception) {}
        }

        /** ¿Le ha quitado el usuario el ahorro de bateria a esta app? */
        fun sinAhorroDeBateria(c: Context): Boolean = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                pm.isIgnoringBatteryOptimizations(c.packageName)
            } else true
        } catch (e: Exception) { true }
    }
}
