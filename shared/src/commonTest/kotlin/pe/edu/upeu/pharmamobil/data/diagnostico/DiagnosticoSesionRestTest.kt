package pe.edu.upeu.pharmamobil.data.diagnostico

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import pe.edu.upeu.pharmamobil.data.local.FakeAlmacenSeguro
import pe.edu.upeu.pharmamobil.data.remote.crearHttpClient
import pe.edu.upeu.pharmamobil.data.remote.jwtDePrueba
import pe.edu.upeu.pharmamobil.data.repository.AuthRepositorioRest
import pe.edu.upeu.pharmamobil.data.remote.ProductoApiRest
import pe.edu.upeu.pharmamobil.domain.sesion.AvisosDeSesion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DiagnosticoSesionRestTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val peticiones = mutableListOf<HttpRequestData>()
    private val paginaVacia = """{"contenido":[],"pagina":0,"totalElementos":0}"""

    private fun motor() = MockEngine { peticion ->
        peticiones += peticion
        val autorizacion = peticion.headers[HttpHeaders.Authorization]
        when {
            autorizacion == null || !autorizacion.startsWith("Bearer ") ->
                respondError(HttpStatusCode.Unauthorized, """{"status":401,"message":"Debe iniciar sesión para acceder a este recurso"}""", json)
            else -> respond(paginaVacia, HttpStatusCode.OK, json)
        }
    }

    @Test
    fun alterarElTokenCambiaLaFirmaYElPluginUsaElValorNuevo() = runTest {
        val token = jwtDePrueba("admin", listOf("CATALOGO_LEER"), 9_999_999_999L)
        val almacen = FakeAlmacenSeguro(token)
        val engine = motor()
        val cliente = crearHttpClient(engine, almacen, esDepuracion = false)
        val productos = ProductoApiRest(cliente, AvisosDeSesion())
        val diagnostico = DiagnosticoSesionRest(cliente, engine, almacen)

        productos.listar()
        assertTrue(diagnostico.alterarTokenGuardado())
        productos.listar()

        val alterado = almacen.token!!
        assertNotEquals(token, alterado)
        assertEquals(token.substringBeforeLast('.'), alterado.substringBeforeLast('.'))
        assertEquals("Bearer $alterado", peticiones.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun elPrefijoEnMinusculaViajaTalCualYElServidorLoTrataComoAnonimo() = runTest {
        val engine = motor()
        val almacen = FakeAlmacenSeguro("tok-1")
        val diagnostico = DiagnosticoSesionRest(crearHttpClient(engine, almacen, false), engine, almacen)

        val resultado = diagnostico.peticionConPrefijoEnMinuscula()

        assertEquals("bearer tok-1", peticiones.single().headers[HttpHeaders.Authorization])
        assertEquals(401, resultado.codigo)
        assertEquals("Debe iniciar sesión para acceder a este recurso", resultado.mensaje)
    }

    @Test
    fun despuesDeCerrarSesionLaPeticionSaleSinCabeceraYRecibe401() = runTest {
        val engine = motor()
        val almacen = FakeAlmacenSeguro("tok-1")
        val cliente = crearHttpClient(engine, almacen, false)
        val diagnostico = DiagnosticoSesionRest(cliente, engine, almacen)

        ProductoApiRest(cliente, AvisosDeSesion()).listar()
        AuthRepositorioRest(cliente, almacen).cerrarSesion()
        val resultado = diagnostico.peticionConElClienteActual()

        assertEquals(401, resultado.codigo)
        assertFalse(resultado.conCabeceraAuthorization!!)
        assertFalse(diagnostico.estadoToken().guardado)
    }
}
