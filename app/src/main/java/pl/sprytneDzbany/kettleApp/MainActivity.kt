package pl.sprytneDzbany.kettleApp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import pl.sprytneDzbany.kettleApp.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var loadCircle: AnimatedVectorHelper
    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var kettle: WebClient
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        binding.bMain
            .setOnClickListener {
                startScan()
            }

        binding.ivLoadCircle.visibility = View.INVISIBLE

        loadCircle = AnimatedVectorHelper(this, R.drawable.load_circle_fancy, binding.ivLoadCircle)
    }

    private fun startScan() {
        if(scanning) {return}
        scanning = true
        binding.bMain.visibility = View.INVISIBLE
        binding.ivLoadCircle.visibility = View.VISIBLE
        loadCircle.startLoop(binding.ivLoadCircle)
        val scanner = NetworkScanner(this, binding.tvConnectionProggress)
        scanner.findActiveKettles().subscribe fromScanner@{result ->
            binding.ivLoadCircle.visibility = View.INVISIBLE
            loadCircle.stopLoop(binding.ivLoadCircle)
            binding.bMain.visibility = View.VISIBLE
            if (!result.isPresent || result.get().isEmpty()) {
                Log.i(TAG, "No kettles found")
                displayProgressMessage(R.string.progress_no_devices_found)
                displayHelp(R.string.help_no_devices_found)
                scanning = false
                return@fromScanner
            }
            displayProgressMessage(R.string.progress_done)
            kettle = result.get()[0]
            startActivity(Intent(this, KettleActivity().setup(kettle)::class.java))
            scanning = false
        }
    }

    private fun displayProgressMessage(messageStringPointer: Int) {
        this@MainActivity.runOnUiThread {
            val message = this@MainActivity.getString(messageStringPointer)
            Log.i(TAG, "Displaying progress message - $message")
            binding.tvConnectionProggress.text = message
        }
    }

    private fun displayHelp(messageStringPointer: Int) {
        this@MainActivity.runOnUiThread {
            val message = this@MainActivity.getString(messageStringPointer)
            Log.i(TAG, "Displaying progress message - $message")
            binding.tvConnectionHelp.text = message
        }
    }
}