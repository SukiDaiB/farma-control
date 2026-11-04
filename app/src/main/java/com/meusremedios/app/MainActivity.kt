package com.meusremedios.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.meusremedios.app.alarme.Notificacoes
import com.meusremedios.app.ui.AppRemedios
import com.meusremedios.app.ui.theme.TemaRemedios

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val abrirAba = intent?.getStringExtra(Notificacoes.EXTRA_ABRIR_ABA)
        setContent {
            TemaRemedios {
                AppRemedios(abrirAba = abrirAba)
            }
        }
    }
}
