package es.guiamayores.cuidame

import android.content.Context

/**
 * Todo lo que la app recuerda. Cuatro cosas y ninguna sale del movil.
 */
class Ajustes(contexto: Context) {

    private val p = contexto.getSharedPreferences("cuidame", Context.MODE_PRIVATE)

    /** A quien se avisa. */
    var telefonoContacto: String
        get() = p.getString("telefono", "") ?: ""
        set(v) = p.edit().putString("telefono", v).apply()

    var nombreContacto: String
        get() = p.getString("nombreContacto", "") ?: ""
        set(v) = p.edit().putString("nombreContacto", v).apply()

    /** De quien se habla en el mensaje. */
    var nombrePersona: String
        get() = p.getString("nombrePersona", "") ?: ""
        set(v) = p.edit().putString("nombrePersona", v).apply()

    var vigilanciaActiva: Boolean
        get() = p.getBoolean("activa", false)
        set(v) = p.edit().putBoolean("activa", v).apply()

    /**
     * Horas sin moverse que hacen saltar el aviso.
     *
     * Cuatro horas es un punto intermedio pensado a proposito: una siesta
     * larga no lo dispara, pero un desmayo por la mañana no espera hasta
     * la noche. Es lo que mas gente salva y lo que menos falsos avisos da.
     */
    var horasSinMoverse: Int
        get() = p.getInt("horasQuieto", 4)
        set(v) = p.edit().putInt("horasQuieto", v).apply()

    /** Fuera de este horario no se vigila la inmovilidad: es de noche y
     *  lo normal es justamente no moverse. */
    var horaInicioDia: Int
        get() = p.getInt("horaInicio", 9)
        set(v) = p.edit().putInt("horaInicio", v).apply()

    var horaFinDia: Int
        get() = p.getInt("horaFin", 22)
        set(v) = p.edit().putInt("horaFin", v).apply()

    fun estaConfigurada(): Boolean =
        telefonoContacto.trim().length >= 9 && nombrePersona.isNotBlank()
}
