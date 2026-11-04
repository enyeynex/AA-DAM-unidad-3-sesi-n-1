package pe.edu.upeu.pharmamobil.data.repository

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.edu.upeu.pharmamobil.data.local.FakeAlmacenSeguro
import pe.edu.upeu.pharmamobil.data.local.ProductoLocal
import pe.edu.upeu.pharmamobil.data.local.baseEnMemoria
import pe.edu.upeu.pharmamobil.data.remote.ProductoApiRest
import pe.edu.upeu.pharmamobil.data.remote.crearHttpClient
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.port.Telemetria
import pe.edu.upeu.pharmamobil.domain.sesion.AvisosDeSesion
import pe.edu.upeu.pharmamobil.domain.usecase.SincronizarUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guía 13, paso 7: sincronización refinada con la base en memoria y el motor
 * HTTP simulado. El POST /productos responde con [estadoAlta]; el resto
 * funciona normal. Las esperas son virtuales: [esperas] las registra.
 */
class SincronizacionTest {

    private class Servidor(var estadoAlta: HttpStatusCode = HttpStatusCode.Created) {
        val eventos = mutableListOf<String>()
        private val json = headersOf(HttpHeaders.ContentType, "application/json")

        fun responder(scope: MockRequestHandleScope, peticion: HttpRequestData): HttpResponseData {
            val ruta = peticion.url.encodedPath
            return when {
                ruta.endsWith("/categorias") ->
                    scope.respond("""[{"id":1,"nombre":"General"}]""", HttpStatusCode.OK, json)
                ruta.endsWith("/productos") && peticion.method == HttpMethod.Post -> {
                    eventos += "POST"
                    if (estadoAlta == HttpStatusCode.Created) {
                        scope.respond("""{"id":50,"nombre":"Ibuprofeno","precio":4.5,"stock":20}""", estadoAlta, json)
                    } else {
                        scope.respond("""{"status":${estadoAlta.value},"message":"Fallo ${estadoAlta.value}"}""", estadoAlta, json)
                    }
                }
                ruta.endsWith("/productos") -> {
                    eventos += "GET"
                    scope.respond("""{"contenido":[],"pagina":0,"totalElementos":0}""", HttpStatusCode.OK, json)
                }
                else -> scope.respond("", HttpStatusCode.NotFound)
            }
        }
    }

    private class TelemetriaDePrueba : Telemetria {
        val errores = mutableListOf<Pair<Throwable, Map<String, String>>>()
        override fun registrarError(e: Throwable, contexto: Map<String, String>) {
            errores += e to contexto
        }
        override fun registrarEvento(nombre: String, datos: Map<String, String>) = Unit
    }

    private class Escenario(val servidor: Servidor) {
        val local = ProductoLocal(baseEnMemoria())
        val esperas = mutableListOf<Long>()
        val telemetria = TelemetriaDePrueba()
        private val cliente = crearHttpClient(
            motor = MockEngine { peticion -> servidor.responder(this, peticion) },
            almacen = FakeAlmacenSeguro("token-secreto"),
            esDepuracion = false
        )
        val caso = SincronizarUseCase(
            local = local,
            remoto = EnvioRepositorioRest(ProductoApiRest(cliente, AvisosDeSesion())),
            ahora = { 9_000L },
            esperar = { esperas += it },
            telemetria = telemetria
        )
        val pendiente = local.guardarPendiente(Producto(0L, "Ibuprofeno", 4.5, 20))
        fun fila() = local.buscar(pendiente.id)
    }

    @Test
    fun unFalloTransitorioSumaIntentoYNoMarcaFallida() = runTest {
        val e = Escenario(Servidor(HttpStatusCode.ServiceUnavailable))

        e.caso()

        val fila = e.fila()!!
        assertTrue(fila.intentos >= 1, "el 503 sumó intentos")
        assertEquals("Fallo 503", fila.ultimoError)
        assertNull(fila.rechazo, "un fallo transitorio no es un rechazo")
    }

    @Test
    fun unFalloPermanenteMarcaLaFilaYNoLaReintenta() = runTest {
        val e = Escenario(Servidor(HttpStatusCode.Conflict))

        e.caso()
        e.caso()

        assertEquals("Fallo 409", e.fila()!!.rechazo)
        assertEquals(1, e.servidor.eventos.count { it == "POST" }, "el 409 no se reintenta")
        assertEquals(1, e.local.observarFallidas(SincronizarUseCase.MAX_INTENTOS).first())
        assertTrue(e.esperas.isEmpty(), "un permanente no espera")
    }

    @Test
    fun agotarLosIntentosDejaLaFilaFallidaYVisible() = runTest {
        val e = Escenario(Servidor(HttpStatusCode.InternalServerError))

        val resumen = e.caso()

        assertEquals(SincronizarUseCase.MAX_INTENTOS, e.fila()!!.intentos)
        assertTrue(e.fila()!!.agotoIntentos)
        assertEquals(1, resumen.fallidas)
        assertEquals(0, e.local.observarPendientes(SincronizarUseCase.MAX_INTENTOS).first())
        assertEquals(1, e.local.observarFallidas(SincronizarUseCase.MAX_INTENTOS).first())
        // Otra sincronización ya no la vuelve a enviar.
        e.caso()
        assertEquals(SincronizarUseCase.MAX_INTENTOS, e.servidor.eventos.count { it == "POST" })
    }

    @Test
    fun laEsperaCreceConLosIntentos() = runTest {
        val e = Escenario(Servidor(HttpStatusCode.ServiceUnavailable))

        e.caso()

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), e.esperas)
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 16_000L),
            (0..5).map { SincronizarUseCase.espera(it) },
            "la espera se duplica y se detiene en 16 s"
        )
    }

    @Test
    fun laSubidaOcurreAntesDeLaBajada() = runTest {
        val e = Escenario(Servidor(HttpStatusCode.Created))

        val resumen = e.caso()

        assertEquals(listOf("POST", "GET"), e.servidor.eventos)
        assertEquals(1, resumen.enviadas)
        assertNull(e.fila(), "la fila provisional se reemplazó por la definitiva")
        assertEquals(50L, resumen.confirmados[e.pendiente.id]?.id)
    }

    @Test
    fun reintentarAhoraReiniciaLosIntentos() = runTest {
        val servidor = Servidor(HttpStatusCode.ServiceUnavailable)
        val e = Escenario(servidor)
        e.caso()
        assertTrue(e.fila()!!.agotoIntentos)

        servidor.estadoAlta = HttpStatusCode.Created
        val resumen = e.caso.reintentarFallidas()

        assertEquals(1, resumen.enviadas)
        assertEquals(0, e.local.observarFallidas(SincronizarUseCase.MAX_INTENTOS).first())
    }

    @Test
    fun laTelemetriaRecibeElErrorPermanenteConContextoYSinToken() = runTest {
        val e = Escenario(Servidor(HttpStatusCode.Conflict))

        e.caso()

        val (error, contexto) = e.telemetria.errores.single()
        assertEquals("Fallo 409", error.message)
        assertEquals("sincronizar", contexto["operacion"])
        assertEquals("permanente", contexto["tipo"])
        assertFalse(contexto.values.any { "token" in it }, "nunca se envía el token")
    }
}
