package es.guiamayores.cuidame

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * LO QUE MIDE LA PULSERA
 * ======================
 *
 * POR QUE UNA PULSERA CAMBIA ESTA APP MAS QUE CUALQUIER OTRA COSA
 *
 * No es por los datos. Es porque SE LLEVA PUESTA.
 *
 * El agujero mas grande de todo lo que llevamos hecho es que el movil se
 * queda en la mesa. La deteccion de caidas, las horas sin moverse, la
 * ubicacion: todo eso vale mientras el telefono vaya encima de la
 * persona, y muchas veces no va. Una muñeca si.
 *
 * DE DONDE SALEN LOS DATOS
 *
 * De Health Connect, que es el almacen de salud del propio Android. La
 * app de la pulsera escribe alli y nosotros leemos, con permiso y en el
 * propio movil. No hay cuenta nuestra, ni nube, ni contraseñas de nadie,
 * ni tenemos que hacer ingenieria inversa del bluetooth de Xiaomi -que es
 * lo otro que se podria hacer y que se rompe con cada actualizacion del
 * firmware-.
 *
 * QUE SE LEE Y POR QUE ESO Y NO OTRA COSA
 *
 *   - PASOS AL DIA. El mejor dato de los tres, y el que menos se mira.
 *     Una bajada sostenida del numero de pasos es de los avisos mas
 *     tempranos y fiables de que algo va a peor en una persona mayor:
 *     aparece semanas antes que las caidas.
 *
 *   - PULSO EN REPOSO. En tendencia, no en un dia suelto. Sube antes de
 *     que se note una infeccion o de que un corazon se descompense.
 *
 *   - SUEÑO, pero solo el tiempo total y los despertares. Los porcentajes
 *     de "sueño profundo" que dan estas pulseras no se sostienen contra
 *     un estudio de sueño de verdad, asi que no se enseñan: preferimos
 *     dar tres numeros creibles que seis que suenen bien.
 */
object Pulsera {

    /** Todo lo que se pide. Ni un permiso mas de los que se usan. */
    val PERMISOS = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    enum class Estado { LISTO, NO_INSTALADO, NO_DISPONIBLE }

    fun estado(c: Context): Estado = try {
        when (HealthConnectClient.getSdkStatus(c)) {
            HealthConnectClient.SDK_AVAILABLE -> Estado.LISTO
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Estado.NO_INSTALADO
            else -> Estado.NO_DISPONIBLE
        }
    } catch (e: Exception) { Estado.NO_DISPONIBLE }

    fun cliente(c: Context): HealthConnectClient? = try {
        if (estado(c) == Estado.LISTO) HealthConnectClient.getOrCreate(c) else null
    } catch (e: Exception) { null }

    suspend fun tienePermisos(c: Context): Boolean {
        return try {
            val cl = cliente(c)
            if (cl == null) false
            else cl.permissionController.getGrantedPermissions().containsAll(PERMISOS)
        } catch (e: Exception) { false }
    }

    class Dia(
        val fecha: String,
        val pasos: Long?,
        val pulsoReposo: Int?,
        val minutosDormido: Long?
    )

    /**
     * Los ultimos dias, del mas reciente al mas antiguo.
     *
     * Se leen dias enteros y no instantes sueltos porque lo que importa es
     * la tendencia. Un dia con pocos pasos no significa nada -llovio, o
     * hubo visita-; una semana entera por debajo de lo normal, si.
     */
    suspend fun ultimosDias(c: Context, cuantos: Int = 7): List<Dia> {
        val cl = cliente(c) ?: return emptyList()
        val zona = ZoneId.systemDefault()
        val salida = mutableListOf<Dia>()

        for (atras in 0 until cuantos) {
            val dia = Instant.now().atZone(zona).toLocalDate().minusDays(atras.toLong())
            val desde = dia.atStartOfDay(zona).toInstant()
            val hasta = dia.plusDays(1).atStartOfDay(zona).toInstant()
            val filtro = TimeRangeFilter.between(desde, hasta)

            val pasos = try {
                cl.readRecords(ReadRecordsRequest(StepsRecord::class, filtro))
                    .records.sumOf { it.count }.takeIf { it > 0 }
            } catch (e: Exception) { null }

            // EL PULSO EN REPOSO, NO EL PULSO MEDIO.
            //
            // El medio del dia mezcla el sofa con subir la escalera y no
            // dice nada. Se coge el percentil mas bajo -los momentos mas
            // tranquilos- que es lo que de verdad se compara entre dias.
            val pulso = try {
                val todos = cl.readRecords(ReadRecordsRequest(HeartRateRecord::class, filtro))
                    .records.flatMap { it.samples }.map { it.beatsPerMinute }.sorted()
                if (todos.size >= 10) todos[todos.size / 10].toInt() else null
            } catch (e: Exception) { null }

            val dormido = try {
                cl.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filtro))
                    .records.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
                    .takeIf { it > 0 }
            } catch (e: Exception) { null }

            salida.add(
                Dia(
                    "%02d/%02d".format(dia.dayOfMonth, dia.monthValue),
                    pasos, pulso, dormido
                )
            )
        }
        return salida
    }

    /**
     * ¿SE LA ESTA PONIENDO? Y ADEMAS, ¿SIGUE LLEGANDO EL DATO?
     *
     * Esta es la pregunta mas importante de todas y casi nadie la hace.
     * Todo lo que mide una pulsera vale cero si esta en un cajon. Y si un
     * dia deja de llegar el dato, la familia se queda tan tranquila
     * creyendo que se vigila, que es el fallo que llevamos toda la semana
     * persiguiendo en esta app.
     *
     * Se responde SIN bluetooth y sin ningun permiso nuevo: una pulsera
     * solo mide el pulso cuando la llevas puesta -si esta en la mesilla no
     * tiene contra que medir-. Asi que si hay muestras de pulso recientes,
     * es que la lleva puesta. Y si no llega ninguna en muchas horas, algo
     * pasa: se la ha quitado, se quedo sin bateria, o la app de la pulsera
     * ha dejado de sincronizar.
     *
     * @return horas desde la ultima medida de pulso, o null si no hay nada.
     */
    suspend fun horasDesdeElUltimoDato(c: Context): Double? {
        val cl = cliente(c) ?: return null
        return try {
            val ahora = Instant.now()
            val filtro = TimeRangeFilter.between(ahora.minus(4, ChronoUnit.DAYS), ahora)
            val ultima = cl.readRecords(ReadRecordsRequest(HeartRateRecord::class, filtro))
                .records.maxOfOrNull { it.endTime } ?: return null
            ChronoUnit.MINUTES.between(ultima, ahora) / 60.0
        } catch (e: Exception) { null }
    }

    /**
     * COMPARAR A LA PERSONA CONSIGO MISMA, NO CON UNA TABLA.
     *
     * Un pulso en reposo de 78 no dice nada suelto: para una persona es lo
     * de siempre y para otra es una señal. Lo que dice algo es que HOY
     * este ocho latidos por encima de lo que ha sido su normal las ultimas
     * tres semanas.
     *
     * Por eso todo lo que decide esta app se compara contra el propio
     * historial de la persona. Las tablas de valores normales estan hechas
     * con poblacion general y no sirven para alguien de ochenta y cinco
     * años con tres pastillas.
     */
    class Comparacion(
        val quePasa: String,
        val reciente: Double,
        val normal: Double
    )

    suspend fun comparar(c: Context): List<Comparacion> {
        val dias = ultimosDias(c, 21)
        val fuera = mutableListOf<Comparacion>()

        fun mirar(
            nombre: String,
            saca: (Dia) -> Double?,
            subeEsMalo: Boolean,
            cuantoCambio: Double,
            texto: (Double, Double) -> String
        ) {
            val recientes = dias.take(3).mapNotNull(saca)
            val base = dias.drop(4).mapNotNull(saca)
            if (recientes.size < 2 || base.size < 7) return
            val a = recientes.average()
            val b = base.average()
            if (b <= 0.0) return
            val cambio = (a - b) / b
            val salta = if (subeEsMalo) cambio > cuantoCambio else cambio < -cuantoCambio
            if (salta) fuera.add(Comparacion(texto(a, b), a, b))
        }

        mirar("pulso", { it.pulsoReposo?.toDouble() }, true, 0.12) { a, b ->
            "El pulso en reposo le ha subido: estos días ${a.toInt()} por minuto, " +
            "cuando lo normal en él/ella era ${b.toInt()}."
        }
        mirar("pasos", { it.pasos?.toDouble() }, false, 0.35) { a, b ->
            "Anda bastante menos: ${a.toInt()} pasos al día frente a los ${b.toInt()} " +
            "de las semanas anteriores."
        }
        mirar("sueñoMenos", { it.minutosDormido?.toDouble() }, false, 0.25) { a, b ->
            "Duerme bastante menos: ${(a / 60).toInt()}h frente a las ${(b / 60).toInt()}h de antes."
        }
        mirar("sueñoMas", { it.minutosDormido?.toDouble() }, true, 0.30) { a, b ->
            "Duerme bastante más de lo que solía: ${(a / 60).toInt()}h frente a ${(b / 60).toInt()}h. " +
            "Dormir de más también avisa, sobre todo si además anda menos."
        }
        return fuera
    }

    /**
     * La frase que de verdad sirve: ¿va a peor?
     *
     * Compara los tres ultimos dias con los cuatro anteriores. No es
     * estadistica fina y no pretende serlo: es lo suficiente para que
     * alguien mire, que es todo lo que puede hacer una app.
     */
    fun tendenciaPasos(dias: List<Dia>): String? {
        val con = dias.filter { it.pasos != null }
        if (con.size < 6) return null
        val recientes = con.take(3).mapNotNull { it.pasos }
        val antes = con.drop(3).mapNotNull { it.pasos }
        if (recientes.isEmpty() || antes.isEmpty()) return null
        val a = recientes.average()
        val b = antes.average()
        if (b < 500) return null
        val cambio = (a - b) / b
        return when {
            cambio < -0.30 -> "⚠️ Está andando bastante menos que los días anteriores " +
                              "(${a.toInt()} pasos frente a ${b.toInt()}). Si no hay un motivo " +
                              "claro —mal tiempo, un catarro—, merece la pena preguntarle qué tal se encuentra."
            cambio > 0.30 -> "Está andando más que los días anteriores. Buena señal."
            else -> "Anda más o menos lo mismo que los días anteriores."
        }
    }
}
