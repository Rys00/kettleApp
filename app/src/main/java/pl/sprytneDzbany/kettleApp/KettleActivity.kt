package pl.sprytneDzbany.kettleApp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import org.json.JSONObject
import pl.sprytneDzbany.kettleApp.databinding.ActivityKettleBinding

class KettleActivity: ComponentActivity() {

    private val TAG = "KettleActivity"
    private lateinit var binding: ActivityKettleBinding

    private var busy = false

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var kettle: WebClient
    }

    fun setup(webClient: WebClient): KettleActivity {
        kettle = webClient
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKettleBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        binding.bBlink.setOnClickListener {
            sendCommand("ledOn") {
                Thread.sleep(1000)
                sendCommand("ledOff") {
                    displayProgressMessage(R.string.progress_led_blinked)
                }
            }
        }

        binding.bKettleOn.setOnClickListener {
            sendCommand("kettleOn") {
                displayProgressMessage(R.string.progress_kettle_on)
            }
        }

        binding.bKettleOff.setOnClickListener {
            sendCommand("kettleOff") {
                displayProgressMessage(R.string.progress_kettle_off)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        kettle.close()
    }

    private fun sendCommand(
        command: String,
        extraData: JSONObject = JSONObject(),
        onSuccessfulResponseCallback: (response: JSONObject) -> Any
    ) {
        if(busy) return
        busy = true
        try {
            kettle.sendCommand(command, extraData).subscribe fromResponse@{ response ->
                busy = false
                if(response.get("code") != 200) {
                    displayProgressMessage(R.string.progress_error)
                    return@fromResponse
                }
                onSuccessfulResponseCallback(response)
            }
        }
        catch (e: Exception) {
            busy = false
            displayProgressMessage(R.string.connection_error)
        }
    }

    private fun displayProgressMessage(messageStringPointer: Int) {
        this@KettleActivity.runOnUiThread {
            val message = this@KettleActivity.getString(messageStringPointer)
            Log.i(TAG, "Displaying progress message - $message")
            binding.tvManagerProggress.text = message
        }
    }

    private fun displayHelp(messageStringPointer: Int) {
        this@KettleActivity.runOnUiThread {
            val message = this@KettleActivity.getString(messageStringPointer)
            Log.i(TAG, "Displaying progress message - $message")
            binding.tvManagerHelp.text = message
        }
    }
}