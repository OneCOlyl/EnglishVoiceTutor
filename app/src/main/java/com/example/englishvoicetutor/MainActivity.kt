package com.example.englishvoicetutor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.englishvoicetutor.presentation.navigation.AppNavHost
import com.example.englishvoicetutor.presentation.theme.EnglishVoiceTutorTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var micGranted by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }

            // rememberLauncherForActivityResult — стандартный Compose-способ запросить
            // разрешение. Определяем прямо здесь, а не в отдельной @Composable-функции
            // внутри Activity (это ломало обработку Compose-компилятором).
            val micLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> micGranted = granted }

            val modelFile = File(filesDir, "model.litertlm")
            var isModelReady by remember { mutableStateOf(modelFile.exists()) }

            EnglishVoiceTutorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        micPermissionGranted = micGranted,
                        onRequestMicPermission = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        isModelReady = isModelReady
                    )
                }
            }
        }
    }
}
