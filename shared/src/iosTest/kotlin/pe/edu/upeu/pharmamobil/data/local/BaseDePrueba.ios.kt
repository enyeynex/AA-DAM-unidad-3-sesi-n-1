package pe.edu.upeu.pharmamobil.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import pe.edu.upeu.pharmamobil.db.PharmaDb

// inMemoryDriver ya crea el esquema al abrir la base.
actual fun crearDriverDePrueba(): SqlDriver = inMemoryDriver(PharmaDb.Schema)
