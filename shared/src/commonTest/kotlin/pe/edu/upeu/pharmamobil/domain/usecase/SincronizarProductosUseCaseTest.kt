package pe.edu.upeu.pharmamobil.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.edu.upeu.pharmamobil.data.repository.FakeProductoRepository
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Producto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SincronizarProductosUseCaseTest {

    private val paracetamol = Producto(id = 1L, nombre = "Paracetamol", precio = 12.50, stock = 5)

    @Test
    fun observarEntregaLoQueHayEnLaBaseLocal() = runTest {

        val repositorio = FakeProductoRepository(listOf(paracetamol))

        val productos = ObservarProductosUseCase(repositorio)().first()

        assertEquals("Paracetamol", productos.single().nombre)
    }

    @Test
    fun sincronizarConRedDevuelveSuccess() = runTest {

        val repositorio = FakeProductoRepository().apply { delServidor = listOf(paracetamol) }

        val resultado = SincronizarProductosUseCase(repositorio)()

        assertTrue(resultado.isSuccess)
        assertEquals(listOf(paracetamol), repositorio.tabla.value)
    }

    @Test
    fun sincronizarSinRedDevuelveFailureConSinConexion() = runTest {

        val repositorio = FakeProductoRepository().apply {
            fallaAlSincronizar = FalloSesionException(ErrorSesion.SinConexion)
        }

        val fallo = SincronizarProductosUseCase(repositorio)().exceptionOrNull()

        assertEquals(ErrorSesion.SinConexion, assertIs<FalloSesionException>(fallo).error)
    }
}
