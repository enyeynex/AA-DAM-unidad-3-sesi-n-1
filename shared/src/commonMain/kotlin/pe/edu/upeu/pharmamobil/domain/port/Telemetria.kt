package pe.edu.upeu.pharmamobil.domain.port

/**
 * Registro de errores y eventos (Guía 13). El dominio declara qué necesita;
 * el proveedor concreto (Firebase Crashlytics) vive en data/firebase.
 * Nunca se envían datos personales ni el token de sesión: el registro
 * termina en un servicio de terceros.
 */
interface Telemetria {
    fun registrarError(e: Throwable, contexto: Map<String, String> = emptyMap())
    fun registrarEvento(nombre: String, datos: Map<String, String> = emptyMap())
}
