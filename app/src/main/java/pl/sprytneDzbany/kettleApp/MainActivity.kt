package pl.sprytneDzbany.kettleApp

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.widget.Button

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
            } else {
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.b_main)
            .setOnClickListener {
                startScan()
            }

        Log.i("main", "Helllo?!?")
    }

    private fun startScan() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_NETWORK_STATE
            ) == PackageManager.PERMISSION_GRANTED -> {
                val scanner = NetworkScanner(this)
                scanner.findActiveKettles()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_NETWORK_STATE) -> {
            }
            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.ACCESS_NETWORK_STATE)
            }
        }
    }
}