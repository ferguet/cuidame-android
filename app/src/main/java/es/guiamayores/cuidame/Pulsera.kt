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

    suspend fun tienePermisos(c: Context): Boolean = try {
        val cl = cliente(c) ?: return false
        cl.permissionController.getGrantedPermissions().containsAll(PERMISOS)
    } catch (e: Exception) { false }

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
