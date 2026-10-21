package pe.edu.upeu.pharmamobil.data.repository

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import pe.edu.upeu.pharmamobil.data.local.FakeAlmacenSeguro
import pe.edu.upeu.pharmamobil.data.local.ProductoLocal
import pe.edu.upeu.pharmamobil.data.local.baseEnMemoria
import pe.edu.upeu.pharmamobil.data.remote.JsonPharma
import pe.edu.upeu.pharmamobil.data.remote.ProductoApiRest
import pe.edu.upeu.pharmamobil.data.remote.crearHttpClient
import pe.edu.upeu.pharmamobil.data.remote.dto.ProductoRequestDto
import pe.edu.upeu.pharmamobil.db.PharmaDb
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.sesion.AvisosDeSesion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Producto 1 de la actividad autonoma de la sesion 11: las seis pruebas del
 * patron offline-first sobre el repositorio real.
 *
 * - Base: SQLite en memoria, nueva en cada prueba (baseEnMemoria()).
 * - Red: el HttpClient real de la app (plugins Auth, ContentNegotiation y el
 *   mapeo de errores de llamadaApi) con MockEngine de Ktor en lugar de
 *   PharmaSoft. Sin red, el motor lanza IOException, igual que en modo avion.
 *
 * Ninguna prueba necesita emulador, red ni esperas.
 */
class SuiteOfflineFirstTest {

    /** PharmaSoft simulado: /productos, /categorias y un interruptor de red. */
    private class PharmaSoftSimulado(vararg iniciales: Producto) {

        var conRed = true
        val productos = iniciales.toMutableList()
        val altas = mutableListOf<String>()
        private var siguienteId = (productos.maxOfOrNull { it.id } ?: 0L) + 1L
        private val json = headersOf(HttpHeaders.ContentType, "application/json")

        fun responder(scope: MockRequestHandleScope, peticion: HttpRequestData): HttpResponseData {
            if (!conRed) throw IOException("Modo avion")
            val ruta = peticion.url.encodedPath
            return when {
                ruta.endsWith("/categorias") ->
                    scope.respond("""[{"id":1,"nombre":"General"}]""", HttpStatusCode.OK, json)

                ruta.endsWith("/productos") && peticion.method == HttpMethod.Post -> {
                    val cuerpo = (peticion.body as TextContent).text
                    val dto = JsonPharma.decodeFromString<ProductoRequestDto>(cuerpo)
                    val creado = Producto(siguienteId++, dto.nombre, dto.precio, dto.stock)
                    productos += creado
                    altas += creado.nombre
                    scope.respond(aJson(creado), HttpStatusCode.Created, json)
                }

                ruta.endsWith("/productos") -> {
                    val contenido = productos.joinToString(",") { aJson(it) }
                    scope.respond(
                        """{"contenido":[$contenido],"pagina":0,"totalElementos":${productos.size}}""",
                        HttpStatusCode.OK, json
                    )
                }

                else -> scope.respond("", HttpStatusCode.NotFound)
            }
        }

        private fun aJson(p: Producto) =
            """{"id":${p.id},"nombre":"${p.nombre}","precio":${p.precio},"stock":${p.stock},"estado":true}"""
    }

    /** Todo lo que una prueba necesita: servidor, base y repositorio reales. */
    private class Escenario(val servidor: PharmaSoftSimulado) {
        val base: PharmaDb = baseEnMemoria()
        val local = ProductoLocal(base)
        private val cliente = crearHttpClient(
            motor = MockEngine { peticion -> servidor.responder(this, peticion) },
            almacen = FakeAlmacenSeguro("token-de-prueba"),
            esDepuracion = false
        )
        val repo = ProductoRepositorioOffline(
            local = local,
            remoto = ProductoApiRest(cliente, AvisosDeSesion()),
            ahora = { 1_000L }
        )

        /** Lo que hay en la tabla, con la columna pendiente incluida. */
        fun filas() = base.productoQueries.listarTodos().executeAsList()
    }

    private val paracetamol = Producto(1L, "Paracetamol", 12.5, 40)
    private val amoxicilina = Producto(2L, "Amoxicilina", 18.0, 25)

    private fun nuevo(nombre: String) = Producto(0L, nombre, 4.5, 20)

    @Test
    fun `1 sincronizar con red llena la base y el Flow emite la lista completa`() = runTest {
        val e = Escenario(PharmaSoftSimulado(paracetamol, amoxicilina))

        e.repo.sincronizar()

        assertEquals(listOf("Amoxicilina", "Paracetamol"), e.repo.observar().first().map { it.nombre })
        assertEquals(2, e.filas().size)
        assertTrue(e.filas().all { it.pendiente == 0L })
        assertEquals(1_000L, e.repo.ultimaSincronizacion().first(), "se registro el instante")
    }

    @Test
    fun `2 registrar sin red deja el producto pendiente y el Flow lo emite`() = runTest {
        val servidor = PharmaSoftSimulado().apply { conRed = false }
        val e = Escenario(servidor)

        val guardado = e.repo.registrar(nuevo("Ibuprofeno"))

        assertEquals(listOf("Ibuprofeno"), e.repo.observar().first().map { it.nombre })
        val fila = e.filas().single()
        assertEquals(1L, fila.pendiente)
        assertTrue(guardado.id < 0, "id local negativo")
        assertTrue(servidor.productos.isEmpty(), "PharmaSoft no recibio nada")
        assertNull(e.repo.ultimaSincronizacion().first(), "sin red no hubo sincronizacion correcta")
    }

    @Test
    fun `3 al reconectar sincronizar envia lo pendiente y la fila deja de estar pendiente`() = runTest {
        val servidor = PharmaSoftSimulado(paracetamol).apply { conRed = false }
        val e = Escenario(servidor)
        e.repo.registrar(nuevo("Ibuprofeno"))

        servidor.conRed = true
        e.repo.sincronizar()

        assertEquals(listOf("Ibuprofeno"), servidor.altas)
        val ibuprofeno = e.filas().single { it.nombre == "Ibuprofeno" }
        assertEquals(0L, ibuprofeno.pendiente)
        assertEquals(2L, ibuprofeno.id, "ahora tiene el id que asigno el servidor")
        assertTrue(e.local.pendientes().isEmpty())
    }

    @Test
    fun `4 la bajada del servidor no borra lo creado sin red`() = runTest {
        val e = Escenario(PharmaSoftSimulado(paracetamol, amoxicilina))
        e.local.guardarPendiente(nuevo("Ibuprofeno"))

        // Solo la bajada, sin subir: es justo el orden que borraria lo pendiente.
        e.local.reemplazarSincronizados(listOf(paracetamol, amoxicilina))

        assertEquals(
            listOf("Amoxicilina", "Ibuprofeno", "Paracetamol"),
            e.repo.observar().first().map { it.nombre }
        )
        assertEquals(1L, e.filas().single { it.nombre == "Ibuprofeno" }.pendiente)
    }

    @Test
    fun `5 un id local y uno del servidor conviven sin sobrescribirse`() = runTest {
        val servidor = PharmaSoftSimulado(paracetamol)
        val e = Escenario(servidor)
        e.repo.sincronizar()

        servidor.conRed = false
        val local = e.repo.registrar(nuevo("Ibuprofeno"))

        val porId = e.filas().associateBy { it.id }
        assertEquals(setOf(local.id, 1L), porId.keys)
        assertEquals("Paracetamol", assertNotNull(porId[1L]).nombre, "la fila del servidor sigue intacta")
        assertEquals("Ibuprofeno", assertNotNull(porId[local.id]).nombre)
        assertTrue(local.id < 0)
    }

    @Test
    fun `6 sincronizar dos veces seguidas no duplica filas`() = runTest {
        val servidor = PharmaSoftSimulado(paracetamol).apply { conRed = false }
        val e = Escenario(servidor)
        e.repo.registrar(nuevo("Ibuprofeno"))
        servidor.conRed = true

        e.repo.sincronizar()
        val despuesDeLaPrimera = e.filas().size
        e.repo.sincronizar()

        assertEquals(2, despuesDeLaPrimera)
        assertEquals(despuesDeLaPrimera, e.filas().size)
        assertEquals(2, servidor.productos.size, "PharmaSoft recibio el pendiente una sola vez")
        assertEquals(1, servidor.altas.size)
    }
}
