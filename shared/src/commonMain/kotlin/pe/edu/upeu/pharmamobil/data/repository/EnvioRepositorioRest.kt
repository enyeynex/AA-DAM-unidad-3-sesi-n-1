package pe.edu.upeu.pharmamobil.data.repository

import pe.edu.upeu.pharmamobil.data.remote.ErrorServidorException
import pe.edu.upeu.pharmamobil.data.remote.ProductoApi
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.model.ResultadoEnvio
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRemotoRepository

/**
 * Envío de pendientes a PharmaSoft (Guía 13). Aquí, en la capa de datos, se
 * decide si un error es transitorio o permanente; el caso de uso solo ve
 * [ResultadoEnvio].
 */
class EnvioRepositorioRest(
    private val api: ProductoApi
) : ProductoRemotoRepository {

    override suspend fun enviar(producto: Producto): ResultadoEnvio =
        try {
            ResultadoEnvio.Exito(api.registrar(producto))
        } catch (fallo: FalloSesionException) {
            fallo.aResultado()
        } catch (fallo: ErrorServidorException) {
            fallo.aResultado()
        }

    override suspend fun listar(): List<Producto> = api.listar()

    private fun FalloSesionException.aResultado(): ResultadoEnvio = when (error) {
        ErrorSesion.SinConexion -> ResultadoEnvio.Transitorio(message ?: "Sin conexión", sinConexion = true)
        ErrorSesion.TiempoAgotado -> ResultadoEnvio.Transitorio(message ?: "Tiempo agotado")
        ErrorSesion.SinPermiso -> ResultadoEnvio.Permanente(message ?: "Sin privilegio")
        // El 401 ya avisó a la sesión (llamadaApi): la fila queda fallida y
        // el usuario la reintenta después de volver a iniciar sesión.
        ErrorSesion.SesionExpirada, ErrorSesion.CredencialesInvalidas ->
            ResultadoEnvio.Permanente("Sesión vencida")
    }

    /** 5xx: el servicio no está disponible ahora. 400, 404, 409: reintentar no cambia nada. */
    private fun ErrorServidorException.aResultado(): ResultadoEnvio {
        val motivo = message ?: "PharmaSoft respondió $codigo"
        return if (codigo >= 500) ResultadoEnvio.Transitorio(motivo) else ResultadoEnvio.Permanente(motivo)
    }
}
