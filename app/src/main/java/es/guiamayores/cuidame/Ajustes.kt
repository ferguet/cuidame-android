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
     * Empezo en cuatro y se ha subido a cinco, pero el numero importa
     * menos de lo que parece: lo que fallaba de verdad era que el reloj
     * seguia contando mientras la persona dormia (ver
     * ServicioVigilancia.comprobarInmovilidad). Arreglado eso, cinco horas
     * contadas desde que empieza el dia significan "no se ha movido en
     * toda la mañana", que ya es una frase con contenido.
     *
     * Se puede cambiar desde la pantalla de casa, porque no hay un numero
     * bueno para todo el mundo: quien vive solo y sale poco necesita mas
     * margen que quien tiene a alguien entrando y saliendo.
     */
    var horasSinMoverse: Int
        get() = p.getInt("horasQuieto", 5)
        set(v) = p.edit().putInt("horasQuieto", v.coerceIn(2, 12)).apply()

    /** Fuera de este horario no se vigila la inmovilidad: es de noche y
     *  lo normal es justamente no moverse. */
    var horaInicioDia: Int
        get() = p.getInt("horaInicio", 9)
        set(v) = p.edit().putInt("horaInicio", v.coerceIn(5, 14)).apply()

    var horaFinDia: Int
        get() = p.getInt("horaFin", 22)
        set(v) = p.edit().putInt("horaFin", v.coerceIn(16, 23)).apply()

    /**
     * DONDE ESTA LA CASA, APRENDIDO SOLO.
     *
     * Nadie escribe su direccion aqui: escribir es justo lo que no puede
     * hacer la persona a la que va dirigida esta app. Se aprende sola,
     * mirando donde esta el movil de madrugada. Un movil pasa la noche en
     * casa practicamente siempre, asi que en una semana el dato es firme
     * sin haber preguntado nada a nadie.
     *
     * Sirve para una sola cosa, pero importante: que el aviso pueda decir
     * "esta en casa" o "esta FUERA de casa". Para quien recibe el mensaje
     * esas dos frases llevan a hacer cosas distintas -subir a mirar, o
     * salir a buscar-, y un enlace de mapa a secas obliga a abrirlo e
     * interpretarlo, que es tiempo perdido con el susto en el cuerpo.
     *
     * Son dos numeros guardados en el propio movil. No se mandan a ningun
     * sitio ni se guarda ningun recorrido: solo el ultimo sitio conocido
     * de dormir, machacado cada noche.
     */
    var latitudCasa: Float
        get() = p.getFloat("latCasa", 0f)
        set(v) = p.edit().putFloat("latCasa", v).apply()

    var longitudCasa: Float
        get() = p.getFloat("lonCasa", 0f)
        set(v) = p.edit().putFloat("lonCasa", v).apply()

    fun sabeDondeEsLaCasa(): Boolean = latitudCasa != 0f || longitudCasa != 0f

    /** Para no repetir el aviso de bateria una y otra vez. */
    var avisadaBateria: Boolean
        get() = p.getBoolean("avisadaBateria", false)
        set(v) = p.edit().putBoolean("avisadaBateria", v).apply()

    /**
     * LO UNICO IMPRESCINDIBLE ES EL TELEFONO.
     *
     * Antes tambien se exigia el nombre de la persona, y eso bloqueaba la
     * app entera de la forma mas silenciosa posible: quien rellenaba solo
     * el telefono pulsaba EMPEZAR A VIGILAR y no pasaba nada visible.
     * Parecia un boton roto.
     *
     * Y el nombre no hacia ninguna falta: solo se usa para redactar el
     * mensaje ("Antonia se ha caido"). Sin el, el aviso dice "La persona
     * se ha caido", que avisa exactamente igual de bien. Nunca se debe
     * impedir que algo funcione por un dato que solo mejora la redaccion.
     */
    fun estaConfigurada(): Boolean =
        telefonoContacto.trim().length >= 9
}
