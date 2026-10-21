package pe.edu.upeu.pharmamobil.domain.model

data class Credenciales(val usuario: String, val clave: String)

/**
 * Sesion abierta en PharmaSoft. El dominio no sabe que el token es un JWT ni
 * como viaja: solo que existe y que la sesion trae un conjunto de permisos.
 */
data class Sesion(
    val token: String,
    val usuario: String,
    val nombreCompleto: String,
    val permisos: Set<String>,
    /**
     * El token ya vencio, pero no hubo red para confirmarlo con PharmaSoft: la
     * sesion solo sirve para ver los datos locales hasta reconectar.
     */
    val expirada: Boolean = false
) {
    fun puede(permiso: String): Boolean = permiso in permisos
}
