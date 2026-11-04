package pe.edu.upeu.pharmamobil.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.edu.upeu.pharmamobil.data.local.ProductoLocal
import pe.edu.upeu.pharmamobil.data.local.baseEnMemoria
import pe.edu.upeu.pharmamobil.data.remote.FakeProductoApi
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Producto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Los dos caminos, con red y sin red, sobre una base SQLite real en memoria:
 * sin emulador y sin PharmaSoft. Cada prueba crea su propia base.
 */
class ProductoRepositorioOfflineTest {

    private val paracetamol = Producto(id = 7L, nombre = "Paracetamol", precio = 12.5, stock = 40)

    private class Escenario(val api: FakeProductoApi) {
        val local = ProductoLocal(baseEnMemoria())
        val repo = ProductoRepositorioOffline(local, api)
    }

    private fun apiQueFalla() = FakeProductoApi(conRed = false)

    private fun nuevo(nombre: String) = Producto(0, nombre, 4.5, 20)

    @Test
    fun `sin red el producto queda pendiente y la pantalla lo ve`() = runTest {
        val e = Escenario(apiQueFalla())

        val guardado = e.repo.registrar(nuevo("Ibuprofeno"))

        assertEquals(1, e.repo.observar().first().size)
        assertTrue(guardado.id < 0, "un producto creado sin red lleva id local negativo")
        assertEquals(listOf("Ibuprofeno"), e.local.pendientes().map { it.nombre })
    }

    @Test
    fun `con red el producto se envia y vuelve con el id del servidor`() = runTest {
        val e = Escenario(FakeProductoApi(listOf(paracetamol)))

        e.repo.registrar(nuevo("Ibuprofeno"))

        val lista = e.repo.observar().first()
        assertEquals(listOf("Ibuprofeno", "Paracetamol"), lista.map { it.nombre })
        assertTrue(lista.all { it.id > 0 }, "ya no queda ningun id local")
        assertTrue(e.local.pendientes().isEmpty())
        assertEquals(8L, lista.first { it.nombre == "Ibuprofeno" }.id)
    }

    @Test
    fun `sincronizar sube lo pendiente antes de bajar del servidor`() = runTest {
        val api = apiQueFalla()
        val e = Escenario(api)
        e.repo.registrar(nuevo("Ibuprofeno"))

        api.conRed = true
        e.repo.sincronizar()

        assertEquals(listOf("registrar Ibuprofeno", "listar"), api.eventos)
        assertEquals(listOf("Ibuprofeno"), api.enServidor.map { it.nombre })
        val enPantalla = e.repo.observar().first().single()
        assertTrue(enPantalla.id > 0, "el producto ya tiene id real de PharmaSoft")
    }

    @Test
    fun `sin red sincronizar falla pero los datos guardados siguen visibles`() = runTest {
        val api = FakeProductoApi(listOf(paracetamol))
        val e = Escenario(api)
        e.repo.sincronizar()

        api.conRed = false
        val fallo = assertFailsWith<FalloSesionException> { e.repo.sincronizar() }

        assertEquals(ErrorSesion.SinConexion, fallo.error)
        assertEquals(listOf("Paracetamol"), e.repo.observar().first().map { it.nombre })
    }

    @Test
    fun `la bajada del servidor no borra lo creado sin red`() = runTest {
        val e = Escenario(FakeProductoApi(listOf(paracetamol)))
        e.local.guardarPendiente(nuevo("Ibuprofeno"))

        e.local.reemplazarSincronizados(listOf(paracetamol))

        assertEquals(listOf("Ibuprofeno", "Paracetamol"), e.repo.observar().first().map { it.nombre })
    }

    @Test
    fun `los ids locales son negativos y no se repiten`() = runTest {
        val e = Escenario(apiQueFalla())

        val primero = e.repo.registrar(nuevo("Ibuprofeno"))
        val segundo = e.repo.registrar(nuevo("Amoxicilina"))

        assertEquals(-1L, primero.id)
        assertEquals(-2L, segundo.id)
        assertEquals(2, e.local.pendientes().size)
    }

    @Test
    fun `un pendiente ya enviado no se reenvia si la bajada falla`() = runTest {
        val api = FakeProductoApi().apply { fallaAlListar = true }
        val e = Escenario(api)

        e.repo.registrar(nuevo("Ibuprofeno"))
        api.fallaAlListar = false
        e.repo.sincronizar()

        assertEquals(1, api.enServidor.size, "PharmaSoft recibio el producto una sola vez")
        assertTrue(e.local.pendientes().isEmpty())
    }

    @Test
    fun `un error que no es de red si se informa y el producto queda guardado`() = runTest {
        val api = FakeProductoApi().apply { fallo = FalloSesionException(ErrorSesion.SesionExpirada) }
        val e = Escenario(api)

        val fallo = assertFailsWith<FalloSesionException> { e.repo.registrar(nuevo("Ibuprofeno")) }

        assertEquals(ErrorSesion.SesionExpirada, fallo.error)
        // Guía 13: la fila no se pierde; queda fallida («Sesión vencida») para
        // reintentarla después de volver a iniciar sesión.
        val guardado = e.local.observar().first().single()
        assertEquals("Sesión vencida", guardado.rechazo)
    }
}
