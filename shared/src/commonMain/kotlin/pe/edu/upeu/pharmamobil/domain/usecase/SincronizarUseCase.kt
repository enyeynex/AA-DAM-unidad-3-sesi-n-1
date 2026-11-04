package pe.edu.upeu.pharmamobil.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.model.ResultadoEnvio
import pe.edu.upeu.pharmamobil.domain.model.ResumenSync
import pe.edu.upeu.pharmamobil.domain.port.Telemetria
import pe.edu.upeu.pharmamobil.domain.repository.ProductoLocalRepository
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRemotoRepository
import pe.edu.upeu.pharmamobil.platform.milisegundosActuales

/**
 * Sincronización refinada (Guía 13): intentos por fila, espera creciente y
 * distinción entre fallo transitorio y permanente. No conoce códigos HTTP.
 *
 * - Exito: la fila provisional se sustituye por la definitiva.
 * - Permanente: la fila queda fallida, visible y no se reintenta sola.
 * - Transitorio del servicio (5xx, tiempo agotado): suma un intento, espera
 *   1 s, 2 s, 4 s, 8 s… y vuelve a intentar la misma fila hasta MAX_INTENTOS.
 * - Sin conexión: la petición no salió del teléfono, así que no cuenta como
 *   intento (contarlo agotaría las filas solo por estar en un sótano); se deja
 *   de subir y la bajada informa el error.
 *
 * Idempotencia: PharmaSoft no acepta una clave de operación. Si un POST llega
 * al servidor pero su respuesta se pierde (tiempo agotado), reintentarlo
 * podría duplicar el producto; el servicio lo rechaza con 409 por nombre
 * repetido, que aquí se trata como permanente y queda visible para el usuario.
 *
 * Koin lo registra como single: el [Mutex] evita que dos sincronizaciones
 * envíen la misma fila a la vez.
 */
class SincronizarUseCase(
    private val local: ProductoLocalRepository,
    private val remoto: ProductoRemotoRepository,
    private val ahora: () -> Long = ::milisegundosActuales,
    private val esperar: suspend (Long) -> Unit = { delay(it) },
    /** Registro técnico de los envíos fallidos; nunca recibe el token ni datos personales. */
    private val telemetria: Telemetria? = null
) {

    private val candado = Mutex()

    suspend operator fun invoke(): ResumenSync = candado.withLock {
        var enviadas = 0
        var fallidas = 0
        val confirmados = mutableMapOf<Long, Producto>()

        subida@ for (fila in local.pendientes(MAX_INTENTOS)) {
            var intentos = fila.intentos
            while (true) {
                when (val r = remoto.enviar(fila)) {
                    is ResultadoEnvio.Exito -> {
                        local.confirmar(fila.id, r.delServidor)
                        confirmados[fila.id] = r.delServidor
                        enviadas++
                        break
                    }
                    is ResultadoEnvio.Permanente -> {
                        local.marcarFallida(fila.id, r.motivo)
                        fallidas++
                        telemetria?.registrarError(
                            EnvioFallido(r.motivo),
                            mapOf("operacion" to "sincronizar", "tipo" to "permanente", "fila" to fila.id.toString())
                        )
                        break
                    }
                    is ResultadoEnvio.Transitorio -> {
                        if (r.sinConexion) break@subida
                        local.sumarIntento(fila.id, r.motivo)
                        intentos++
                        if (intentos >= MAX_INTENTOS) {
                            fallidas++
                            telemetria?.registrarError(
                                EnvioFallido(r.motivo),
                                mapOf("operacion" to "sincronizar", "tipo" to "intentos_agotados", "fila" to fila.id.toString())
                            )
                            break
                        }
                        esperar(espera(intentos - 1))
                    }
                }
            }
        }

        // Solo después de subir se baja: invertirlo borra lo pendiente.
        local.conciliar(remoto.listar())
        local.registrarSincronizacion(ahora())
        ResumenSync(enviadas, fallidas, local.contarPendientes(MAX_INTENTOS), confirmados)
    }

    /** «Reintentar ahora»: las filas fallidas vuelven a la cola y se sincroniza. */
    suspend fun reintentarFallidas(): ResumenSync {
        local.reiniciarIntentos(MAX_INTENTOS)
        return invoke()
    }

    /** Error que se envía a la telemetría: solo el motivo técnico. */
    class EnvioFallido(motivo: String) : Exception(motivo)

    companion object {
        const val MAX_INTENTOS = Producto.MAX_INTENTOS_ENVIO

        /** 1 s, 2 s, 4 s, 8 s y como máximo 16 s. */
        fun espera(intentos: Int): Long = 1_000L shl intentos.coerceIn(0, 4)
    }
}
