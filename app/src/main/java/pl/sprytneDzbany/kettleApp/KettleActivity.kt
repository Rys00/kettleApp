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
        binding.bBlink
            .setOnClickListener {
               sendCommand("ledOn") {
                   Thread.sleep(500)
                   sendCommand("ledOff") {
                       displayProgressMessage(R.string.progress_led_blinked)
                   }
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
        onSuccessfulResponseCallback: (response: JSONObject) -> Any) {
        kettle.sendCommand(command, extraData).subscribe fromResponse@{ response ->
            if(response.get("code") != 200) {
                displayProgressMessage(R.string.progress_error)
                return@fromResponse
            }
            onSuccessfulResponseCallback(response)
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