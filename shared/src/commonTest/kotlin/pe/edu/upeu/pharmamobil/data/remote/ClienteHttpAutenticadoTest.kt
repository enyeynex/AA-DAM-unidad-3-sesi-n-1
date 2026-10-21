package pe.edu.upeu.pharmamobil.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import pe.edu.upeu.pharmamobil.data.local.FakeAlmacenSeguro
import pe.edu.upeu.pharmamobil.data.repository.AuthRepositorioRest
import pe.edu.upeu.pharmamobil.domain.model.Credenciales
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.sesion.AvisosDeSesion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Prueba el cliente HTTP real (plugins Auth, Logging y el mapeo de errores)
 * contra un servidor simulado con MockEngine: no hace falta PharmaSoft.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClienteHttpAutenticadoTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val peticiones = mutableListOf<HttpRequestData>()

    private fun loginJson(token: String, usuario: String = "admin") = """
        {"token":"$token","tipo":"Bearer","expiraEnSegundos":3600,"username":"$usuario",
         "nombreCompleto":"Carlos Ramirez Torres","roles":["ADMIN"],
         "permisos":["CATALOGO_LEER","CATALOGO_ESCRIBIR","CLIENTES_LEER"]}
    """.trimIndent()

    private val paginaVacia = """{"contenido":[],"pagina":0,"tamanio":100,"totalElementos":0,"totalPaginas":0,"ultima":true}"""

    private inner class Escenario(
        val almacen: FakeAlmacenSeguro = FakeAlmacenSeguro(),
        val avisos: AvisosDeSesion = AvisosDeSesion(),
        ahora: Long = 1_000L,
        registro: Logger = Logger.SIMPLE,
        esDepuracion: Boolean = false,
        servidor: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ) {
        val cliente = crearHttpClient(
            motor = MockEngine { peticion ->
                peticiones += peticion
                servidor(peticion)
            },
            almacen = almacen,
            esDepuracion = esDepuracion,
            registro = registro
        )
        val auth = AuthRepositorioRest(cliente, almacen, ahora = { ahora })
        val productos = ProductoApiRest(cliente, avisos)
    }

    private fun HttpRequestData.autorizacion(): String? = headers[HttpHeaders.Authorization]

    private suspend fun esperarFallo(bloque: suspend () -> Unit): FalloSesionException {
        try {
            bloque()
        } catch (fallo: FalloSesionException) {
            return fallo
        }
        fail("Se esperaba un FalloSesionException")
    }

    @Test
    fun elLoginGuardaElTokenYNoEnviaAuthorization() = runTest {

        val escenario = Escenario { respond(loginJson("tok-1"), HttpStatusCode.OK, json) }

        val sesion = escenario.auth.iniciarSesion(Credenciales("admin", "Admin123"))

        assertEquals("tok-1", escenario.almacen.token)
        assertEquals("Carlos Ramirez Torres", sesion.nombreCompleto)
        assertTrue(sesion.puede("CATALOGO_ESCRIBIR"))
        assertTrue(peticiones.single().url.encodedPath.endsWith("/api/v1/auth/login"))
        assertNull(peticiones.single().autorizacion())
    }

    @Test
    fun elPluginAuthAdjuntaElBearerSinPeticionPreviaSinToken() = runTest {

        val escenario = Escenario { peticion ->
            if (peticion.url.encodedPath.endsWith("auth/login")) respond(loginJson("tok-1"), HttpStatusCode.OK, json)
            else respond(paginaVacia, HttpStatusCode.OK, json)
        }

        escenario.auth.iniciarSesion(Credenciales("admin", "Admin123"))
        escenario.productos.listar()

        // Login + una sola peticion de productos: sendWithoutRequest evita el 401 previo.
        assertEquals(2, peticiones.size)
        assertEquals("Bearer tok-1", peticiones[1].autorizacion())
    }

    @Test
    fun unSegundoLoginUsaElTokenNuevoGraciasAClearToken() = runTest {

        var siguienteToken = "tok-1"
        val escenario = Escenario { peticion ->
            if (peticion.url.encodedPath.endsWith("auth/login")) respond(loginJson(siguienteToken), HttpStatusCode.OK, json)
            else respond(paginaVacia, HttpStatusCode.OK, json)
        }

        escenario.auth.iniciarSesion(Credenciales("admin", "Admin123"))
        escenario.productos.listar()
        siguienteToken = "tok-2"
        escenario.auth.iniciarSesion(Credenciales("almacen", "Almacen123"))
        escenario.productos.listar()

        assertEquals("Bearer tok-2", peticiones.last().autorizacion())
    }

    @Test
    fun cerrarSesionBorraElTokenYDejaDeEnviarlo() = runTest {

        val escenario = Escenario(almacen = FakeAlmacenSeguro("tok-1")) {
            respond(paginaVacia, HttpStatusCode.OK, json)
        }

        escenario.productos.listar()
        escenario.auth.cerrarSesion()
        escenario.productos.listar()

        assertNull(escenario.almacen.token)
        assertEquals("Bearer tok-1", peticiones.first().autorizacion())
        assertNull(peticiones.last().autorizacion())
    }

    @Test
    fun credencialesIncorrectasNoGuardanNada() = runTest {

        val escenario = Escenario {
            respondError(HttpStatusCode.Unauthorized, """{"status":401,"message":"Usuario o contraseña incorrectos"}""", json)
        }

        val fallo = esperarFallo { escenario.auth.iniciarSesion(Credenciales("admin", "mala")) }

        assertEquals(ErrorSesion.CredencialesInvalidas, fallo.error)
        assertNull(escenario.almacen.token)
    }

    @Test
    fun un401PorTokenExpiradoAvisaQueLaSesionTermino() = runTest {

        val avisos = AvisosDeSesion()
        val recibidos = mutableListOf<ErrorSesion>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            avisos.sesionTerminada.collect { recibidos += it.error }
        }
        val escenario = Escenario(almacen = FakeAlmacenSeguro("tok-viejo"), avisos = avisos) {
            respondError(HttpStatusCode.Unauthorized, """{"status":401,"message":"El token ha expirado. Inicie sesión nuevamente"}""", json)
        }

        val fallo = esperarFallo { escenario.productos.listar() }

        assertEquals(ErrorSesion.SesionExpirada, fallo.error)
        assertEquals("El token ha expirado. Inicie sesión nuevamente", fallo.detalle)
        assertEquals(listOf<ErrorSesion>(ErrorSesion.SesionExpirada), recibidos)
    }

    @Test
    fun un403EsFaltaDePermisoYNoTerminaLaSesion() = runTest {

        val avisos = AvisosDeSesion()
        val recibidos = mutableListOf<ErrorSesion>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            avisos.sesionTerminada.collect { recibidos += it.error }
        }
        val escenario = Escenario(almacen = FakeAlmacenSeguro("tok-almacen"), avisos = avisos) {
            respondError(HttpStatusCode.Forbidden, """{"status":403,"message":"Acceso denegado"}""", json)
        }

        val fallo = esperarFallo { escenario.productos.listar() }

        assertEquals(ErrorSesion.SinPermiso, fallo.error)
        assertTrue(recibidos.isEmpty())
        assertEquals("tok-almacen", escenario.almacen.token)
    }

    @Test
    fun unFalloDeRedSeTraduceASinConexion() = runTest {

        val escenario = Escenario(almacen = FakeAlmacenSeguro("tok-1")) {
            throw IOException("Connection refused")
        }

        val fallo = esperarFallo { escenario.productos.listar() }

        assertEquals(ErrorSesion.SinConexion, fallo.error)
    }

    @Test
    fun elRegistroSaneaLaCabeceraAuthorization() = runTest {

        val lineas = mutableListOf<String>()
        val escenario = Escenario(
            almacen = FakeAlmacenSeguro("tok-secreto"),
            esDepuracion = true,
            registro = object : Logger {
                override fun log(message: String) { lineas += message }
            }
        ) { respond(paginaVacia, HttpStatusCode.OK, json) }

        escenario.productos.listar()

        val registro = lineas.joinToString("\n")
        assertTrue("Authorization: ***" in registro, registro)
        assertFalse("tok-secreto" in registro)
    }

    @Test
    fun registrarCreaLaCategoriaGeneralCuandoNoHayNinguna() = runTest {

        val escenario = Escenario(almacen = FakeAlmacenSeguro("tok-1")) { peticion ->
            val ruta = peticion.url.encodedPath
            when {
                ruta.endsWith("categorias") && peticion.method.value == "GET" -> respond("[]", HttpStatusCode.OK, json)
                ruta.endsWith("categorias") -> respond("""{"id":7,"nombre":"General"}""", HttpStatusCode.Created, json)
                else -> respond("""{"id":1,"nombre":"Paracetamol","precio":12.5,"stock":5,"estado":true,"categoriaId":7}""", HttpStatusCode.Created, json)
            }
        }

        val guardado = escenario.productos.registrar(Producto(id = 0, nombre = "Paracetamol", precio = 12.5, stock = 5))

        assertEquals(1L, guardado.id)
        assertEquals(3, peticiones.size)
        assertTrue(peticiones.all { it.autorizacion() == "Bearer tok-1" })
    }

    @Test
    fun laSesionGuardadaVencidaConRedSeCierraPorEl401() = runTest {

        val token = jwtDePrueba("admin", listOf("ROLE_ADMIN"), expiraEn = 500L)
        val escenario = Escenario(almacen = FakeAlmacenSeguro(token), ahora = 1_000L) {
            respondError(HttpStatusCode.Unauthorized, """{"status":401,"message":"El token ha expirado. Inicie sesión nuevamente"}""", json)
        }

        assertNull(escenario.auth.sesionGuardada())
        assertNull(escenario.almacen.token)
    }

    @Test
    fun laSesionGuardadaVencidaSinRedSeConservaParaVerLosDatosLocales() = runTest {

        val token = jwtDePrueba("admin", listOf("ROLE_ADMIN", "CATALOGO_ESCRIBIR"), expiraEn = 500L)
        val escenario = Escenario(almacen = FakeAlmacenSeguro(token), ahora = 1_000L) {
            throw IOException("Sin red")
        }

        val sesion = assertNotNull(escenario.auth.sesionGuardada())

        assertTrue(sesion.expirada)
        assertEquals("admin", sesion.usuario)
        assertEquals(token, escenario.almacen.token, "el token se conserva hasta poder validarlo")
    }

    @Test
    fun laSesionGuardadaVigenteSeReconstruyeConAuthMe() = runTest {

        val token = jwtDePrueba("vendedor", listOf("VENTAS_LEER"), expiraEn = 5_000L)
        val escenario = Escenario(almacen = FakeAlmacenSeguro(token), ahora = 1_000L) {
            respond(
                """{"id":2,"username":"vendedor","empleadoNombre":"Lucia Fernandez Quispe","roles":["VENDEDOR"],"permisos":["CLIENTES_LEER","VENTAS_LEER"]}""",
                HttpStatusCode.OK, json
            )
        }

        val sesion = assertNotNull(escenario.auth.sesionGuardada())

        assertEquals("Lucia Fernandez Quispe", sesion.nombreCompleto)
        assertTrue(sesion.puede("CLIENTES_LEER"))
        assertEquals("Bearer $token", peticiones.single().autorizacion())
    }

    @Test
    fun sinConexionLaSesionGuardadaSeArmaConLosClaims() = runTest {

        val token = jwtDePrueba("almacen", listOf("ROLE_ALMACENERO", "CATALOGO_ESCRIBIR"), expiraEn = 5_000L)
        val escenario = Escenario(almacen = FakeAlmacenSeguro(token), ahora = 1_000L) {
            throw IOException("Sin red")
        }

        val sesion = assertIs<pe.edu.upeu.pharmamobil.domain.model.Sesion>(escenario.auth.sesionGuardada())

        assertEquals("almacen", sesion.usuario)
        assertTrue(sesion.puede("CATALOGO_ESCRIBIR"))
        assertFalse(sesion.puede("CLIENTES_LEER"))
    }
}
