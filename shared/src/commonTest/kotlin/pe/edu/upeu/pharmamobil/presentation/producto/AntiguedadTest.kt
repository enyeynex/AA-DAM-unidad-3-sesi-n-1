package pe.edu.upeu.pharmamobil.presentation.producto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Producto 3: la antiguedad de los datos en lenguaje natural. */
class AntiguedadTest {

    private val minuto = 60_000L
    private val hora = 60 * minuto
    private val dia = 24 * hora
    private val ahora = 100 * dia

    @Test
    fun nuncaSincronizadoSeIndicaExplicitamente() {
        assertEquals(
            "Nunca sincronizado: conéctate para descargar el inventario",
            textoAntiguedad(instante = null, ahora = ahora)
        )
    }

    @Test
    fun menosDeUnMinutoEsHaceUnMomento() {
        assertEquals("Actualizado hace un momento", textoAntiguedad(ahora - 30_000L, ahora))
    }

    @Test
    fun minutosYHorasEnSingularYPlural() {
        assertEquals("Actualizado hace 1 minuto", textoAntiguedad(ahora - minuto, ahora))
        assertEquals("Actualizado hace 5 minutos", textoAntiguedad(ahora - 5 * minuto, ahora))
        assertEquals("Actualizado hace 1 hora", textoAntiguedad(ahora - hora, ahora))
        assertEquals("Actualizado hace 2 horas", textoAntiguedad(ahora - 2 * hora - 10 * minuto, ahora))
    }

    @Test
    fun masDeUnDiaSeExpresaEnDiasYSeMarcaComoAntiguo() {
        assertEquals("Actualizado hace 1 día", textoAntiguedad(ahora - dia - hora, ahora))
        assertEquals("Actualizado hace 3 días", textoAntiguedad(ahora - 3 * dia, ahora))
        assertTrue(datosAntiguos(ahora - dia, ahora))
        assertFalse(datosAntiguos(ahora - 23 * hora, ahora))
        assertFalse(datosAntiguos(null, ahora))
    }

    @Test
    fun unRelojAtrasadoNoProduceTiemposNegativos() {
        assertEquals("Actualizado hace un momento", textoAntiguedad(ahora + hora, ahora))
    }
}
