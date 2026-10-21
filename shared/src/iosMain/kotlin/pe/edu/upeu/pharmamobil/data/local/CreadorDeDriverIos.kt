package pe.edu.upeu.pharmamobil.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import pe.edu.upeu.pharmamobil.db.PharmaDb

/** Guarda la base pharma.db en el directorio de la aplicacion en iOS. */
class CreadorDeDriverIos : CreadorDeDriver {

    override fun crear(): SqlDriver = NativeSqliteDriver(
        schema = PharmaDb.Schema,
        name = "pharma.db"
    )
}
