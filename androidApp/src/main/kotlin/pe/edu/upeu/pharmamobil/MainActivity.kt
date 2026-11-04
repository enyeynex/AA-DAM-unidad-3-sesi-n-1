package pe.edu.upeu.pharmamobil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.koin.android.ext.android.inject
import pe.edu.upeu.pharmamobil.permiso.GestorPermisosAndroid
import pe.edu.upeu.pharmamobil.permiso.PuentePermisos

class MainActivity : ComponentActivity() {

    private val gestorPermisos: GestorPermisosAndroid by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // El lanzador del diálogo de permisos se registra antes de que la Activity arranque.
        gestorPermisos.puente = PuentePermisos(this)

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        if (gestorPermisos.puente?.actividad === this) gestorPermisos.puente = null
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}