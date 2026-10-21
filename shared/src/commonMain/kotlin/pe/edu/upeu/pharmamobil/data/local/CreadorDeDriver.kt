package pe.edu.upeu.pharmamobil.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Abre la base de datos local. El esquema y las consultas son comunes; lo
 * unico que cambia por plataforma es quien abre el archivo pharma.db. Es el
 * mismo patron del AlmacenSeguro de la sesion 10: interfaz en commonMain e
 * implementacion registrada en el platformModule.
 */
interface CreadorDeDriver {
    fun crear(): SqlDriver
}
