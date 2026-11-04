package pe.edu.upeu.pharmamobil.domain.port

/** Muestra una notificación local; cada plataforma aporta su implementación. */
interface Notificador {
    fun mostrar(titulo: String, texto: String)
}
