package pe.edu.upeu.pharmamobil.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.edu.upeu.pharmamobil.data.local.ProductoLocal
import pe.edu.upeu.pharmamobil.data.local.baseEnMemoria
import pe.edu.upeu.pharmamobil.data.remote.FakeProductoApi
import pe.edu.upeu.pharmamobil.domain.model.Producto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hallazgo de la bitacora (escenario 3): PharmaSoft rechazo con 409 un
 * producto creado sin red porque ya existia otro con el mismo nombre, y ese
 * pendiente bloqueaba toda la sincronizacion. Estas pruebas fijan la correccion.
 */
class RechazoAlSincronizarTest {

    private val paracetamol = Producto(1L, "paracetamol", 12.0, 0)

    private class Escenario(val api: FakeProductoApi) {
        val local = ProductoLocal(baseEnMemoria())
        val repo = ProductoRepositorioOffline(local, api, ahora = { 7_000L })
    }

    private fun nuevo(nombre: String) = Producto(0L, nombre, 3.0, 15)

    @Test
    fun `un pendiente rechazado no bloquea a los demas ni la bajada`() = runTest {
        val api = FakeProductoApi(listOf(paracetamol), conRed = false).apply { rechazados += "paracetamol" }
        val e = Escenario(api)
        e.repo.registrar(nuevo("paracetamol"))
        e.repo.registrar(nuevo("Ibuprofeno"))

        api.conRed = true
        e.repo.sincronizar()

        assertEquals(listOf("paracetamol", "Ibuprofeno"), api.enServidor.map { it.nombre })
        val rechazado = e.repo.observar().first().single { it.id < 0 }
        assertEquals("Ya existe un producto con el nombre paracetamol", rechazado.rechazo)
        assertEquals(7_000L, e.repo.ultimaSincronizacion().first(), "la bajada si se completo")
    }

    @Test
    fun `un rechazado no se vuelve a enviar en cada sincronizacion`() = runTest {
        val api = FakeProductoApi(listOf(paracetamol)).apply { rechazados += "paracetamol" }
        val e = Escenario(api)
        e.repo.registrar(nuevo("paracetamol"))

        e.repo.sincronizar()
        e.repo.sincronizar()

        assertEquals(1, api.eventos.count { it == "registrar paracetamol" })
        assertTrue(e.local.pendientes().isEmpty(), "ya no cuenta como pendiente por enviar")
    }

    @Test
    fun `registrar con red devuelve el id del servidor y sin red el local`() = runTest {
        val api = FakeProductoApi(listOf(paracetamol))
        val e = Escenario(api)

        val conRed = e.repo.registrar(nuevo("Ibuprofeno"))
        api.conRed = false
        val sinRed = e.repo.registrar(nuevo("Amoxicilina"))

        assertEquals(2L, conRed.id)
        assertTrue(sinRed.id < 0)
        assertNull(conRed.rechazo)
    }

    @Test
    fun `registrar devuelve el motivo si PharmaSoft lo rechaza y se puede descartar`() = runTest {
        val api = FakeProductoApi(listOf(paracetamol)).apply { rechazados += "paracetamol" }
        val e = Escenario(api)

        val rechazado = e.repo.registrar(nuevo("paracetamol"))
        assertNotNull(rechazado.rechazo)

        e.repo.descartar(rechazado.id)

        assertEquals(listOf(1L), e.repo.observar().first().map { it.id })
    }

    @Test
    fun `descartar no borra productos que ya existen en el servidor`() = runTest {
        val e = Escenario(FakeProductoApi(listOf(paracetamol)))
        e.repo.sincronizar()

        e.repo.descartar(1L)

        assertEquals(listOf(1L), e.repo.observar().first().map { it.id })
    }
}
