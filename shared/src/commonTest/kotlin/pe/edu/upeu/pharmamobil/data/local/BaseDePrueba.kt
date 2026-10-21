package pe.edu.upeu.pharmamobil.data.local

import app.cash.sqldelight.db.SqlDriver
import pe.edu.upeu.pharmamobil.db.PharmaDb

/**
 * Driver de las pruebas: una base SQLite en memoria, nueva en cada llamada.
 * En la JVM (androidHostTest) es JdbcSqliteDriver(IN_MEMORY); en iOS, el
 * driver nativo en memoria. Las pruebas de commonTest no saben cual usan.
 */
expect fun crearDriverDePrueba(): SqlDriver

/** Una base por prueba: si se compartiera, los datos se heredarian entre casos. */
fun baseEnMemoria(): PharmaDb = PharmaDb(crearDriverDePrueba())

/** El mismo contrato que usa Koin, para registrarlo en el grafo de prueba. */
class CreadorDeDriverEnMemoria : CreadorDeDriver {
    override fun crear(): SqlDriver = crearDriverDePrueba()
}
