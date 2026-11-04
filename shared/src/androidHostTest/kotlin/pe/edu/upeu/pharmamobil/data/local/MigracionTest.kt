package pe.edu.upeu.pharmamobil.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import pe.edu.upeu.pharmamobil.db.PharmaDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Quien instalo la app en la practica tiene pharma.db en la version 1 (solo la
 * tabla producto). Las migraciones 1.sqm (tabla sincronizacion), 2.sqm
 * (columna rechazo) y 3.sqm (intentos y ultimoError, Guía 13) deben
 * aplicarse sin perder los productos guardados.
 */
class MigracionTest {

    @Test
    fun laVersion1MigraALaUltimaSinPerderProductos() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """CREATE TABLE producto (id INTEGER NOT NULL PRIMARY KEY, nombre TEXT NOT NULL,
               precio REAL NOT NULL, stock INTEGER NOT NULL, pendiente INTEGER NOT NULL DEFAULT 0)""",
            0
        )
        driver.execute(null, "INSERT INTO producto VALUES (-1, 'Ibuprofeno', 4.5, 20, 1)", 0)

        PharmaDb.Schema.migrate(driver, oldVersion = 1, newVersion = PharmaDb.Schema.version)
        val base = PharmaDb(driver)

        assertEquals(4L, PharmaDb.Schema.version)
        val pendiente = base.productoQueries.listarPendientes().executeAsOne()
        assertEquals("Ibuprofeno", pendiente.nombre)
        assertNull(pendiente.rechazo, "2.sqm agrego la columna rechazo vacia")
        assertEquals(0L, pendiente.intentos, "3.sqm agrego intentos con 0")
        assertNull(pendiente.ultimoError)
        base.productoQueries.sumarIntento("Sin conexión", -1L)
        assertEquals(1L, base.productoQueries.contarPendientes(5L).executeAsOne())
        assertEquals(0L, base.productoQueries.contarFallidas(5L).executeAsOne())
        assertNull(base.sincronizacionQueries.ultima().executeAsOneOrNull())
        base.sincronizacionQueries.registrar(5_000L)
        assertEquals(5_000L, base.sincronizacionQueries.ultima().executeAsOne())
    }
}
