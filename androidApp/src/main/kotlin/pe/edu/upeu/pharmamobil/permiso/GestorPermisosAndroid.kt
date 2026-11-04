package pe.edu.upeu.pharmamobil.permiso

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import pe.edu.upeu.pharmamobil.domain.permiso.EstadoPermiso
import pe.edu.upeu.pharmamobil.domain.permiso.GestorPermisos
import pe.edu.upeu.pharmamobil.domain.permiso.Permiso
import pe.edu.upeu.pharmamobil.domain.permiso.decidirEstado

/** Implementación de Android con los tres estados del paso 5. */
class GestorPermisosAndroid(
    private val contexto: Context,
    private val preferencias: SharedPreferences
) : GestorPermisos {

    /** La Activity registra aquí su lanzador en onCreate y lo quita en onDestroy. */
    var puente: PuentePermisos? = null

    override suspend fun estado(permiso: Permiso): EstadoPermiso {
        val nombre = nombreDeSistema(permiso) ?: return EstadoPermiso.Concedido
        val concedido = ContextCompat.checkSelfPermission(contexto, nombre) ==
            PackageManager.PERMISSION_GRANTED
        if (concedido) return EstadoPermiso.Concedido
        val actividad = puente?.actividad ?: return EstadoPermiso.Denegado
        val yaSePidio = preferencias.getBoolean("pedido_$nombre", false)
        val procedeExplicar = ActivityCompat.shouldShowRequestPermissionRationale(actividad, nombre)
        return decidirEstado(concedido = false, yaSePidio = yaSePidio, procedeExplicar = procedeExplicar)
    }

    override suspend fun solicitar(permiso: Permiso): EstadoPermiso {
        val nombre = nombreDeSistema(permiso) ?: return EstadoPermiso.Concedido
        preferencias.edit().putBoolean("pedido_$nombre", true).apply()
        val concedido = puente?.pedir(nombre) ?: false
        return if (concedido) EstadoPermiso.Concedido else estado(permiso)
    }

    /** Pantalla de información de la app en Ajustes, donde se activan los permisos. */
    override fun abrirAjustes() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", contexto.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(intent)
    }

    /** null: el sistema no exige permiso en tiempo de ejecución en esta versión. */
    private fun nombreDeSistema(permiso: Permiso): String? = when (permiso) {
        Permiso.NOTIFICACIONES ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        Permiso.CAMARA -> Manifest.permission.CAMERA
        Permiso.UBICACION -> Manifest.permission.ACCESS_COARSE_LOCATION
    }
}
