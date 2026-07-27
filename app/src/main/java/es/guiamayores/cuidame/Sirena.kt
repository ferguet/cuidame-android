package es.guiamayores.cuidame

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * EL RUIDO DE LA ALARMA, SEPARADO DE LA PANTALLA
 * ==============================================
 *
 * Antes el sonido y la vibracion vivian dentro de la pantalla de alarma.
 * Parecia lo natural, pero escondia un fallo grave: con el movil
 * bloqueado y la pantalla apagada, Android NO deja que una app abra una
 * pantalla desde segundo plano -lo prohibio en Android 10 para que
 * ninguna app te asalte la pantalla cuando le apetezca-. La deja en cola
 * y la enseña cuando desbloqueas. Resultado: la persona se caia y el
 * movil se quedaba mudo en el bolsillo hasta que alguien lo desbloqueaba.
 *
 * Al sacar el ruido aqui, el servicio puede hacer sonar la alarma SIEMPRE,
 * sin depender de que Android le deje pintar nada. Y eso es lo que de
 * verdad importa: que suene fuerte, para que la persona lo oiga y para
 * que quien este cerca en la casa se entere. La pantalla es un extra.
 */
object Sirena {

    private var tono: Ringtone? = null
    private var vibrador: Vibrator? = null
    private var sonando = false

    fun sonar(c: Context) {
        if (sonando) return
        sonando = true

        try {
            // El volumen de alarma al maximo: una alarma que no se oye
            // porque el movil estaba en silencio no es una alarma.
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
            )

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            tono = RingtoneManager.getRingtone(c.applicationContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        } catch (e: Exception) {}

        try {
            vibrador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val patron = longArrayOf(0, 700, 400, 700, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrador?.vibrate(VibrationEffect.createWaveform(patron, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrador?.vibrate(patron, 0)
            }
        } catch (e: Exception) {}
    }

    /** Repite el tono si el movil no soporta bucle (Android 8 y anteriores). */
    fun seguirSonando() {
        try {
            if (sonando && tono?.isPlaying == false) tono?.play()
        } catch (e: Exception) {}
    }

    fun callar() {
        sonando = false
        try { tono?.stop() } catch (e: Exception) {}
        try { vibrador?.cancel() } catch (e: Exception) {}
        tono = null
    }
}
