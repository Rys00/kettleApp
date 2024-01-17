package pl.sprytneDzbany.kettleApp

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import pl.sprytneDzbany.kettleApp.databinding.ActivityKettleBinding


class KettleActivity: ComponentActivity() {

    private val TAG = "KettleActivity"
    private lateinit var binding: ActivityKettleBinding
    private val sleeper = Object()

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

        sendCommand("isKettleOn") { response ->
            this.runOnUiThread {
                binding.bPowerKettle.isChecked = response.get("message") as Boolean
            }
        }
        binding.bPowerKettle.setOnClickListener {
            if(binding.bPowerKettle.isChecked) {
                binding.bPowerKettle.isChecked = false
                sendCommand("kettleOn") {
                    displayProgressMessage(R.string.progress_kettle_on)
                    this.runOnUiThread {
                        binding.bPowerKettle.isChecked = true
                    }
                }
            } else {
                binding.bPowerKettle.isChecked = true
                sendCommand("kettleOff") {
                    displayProgressMessage(R.string.progress_kettle_off)
                    this.runOnUiThread {
                        binding.bPowerKettle.isChecked = false
                    }
                }
            }
        }

        Observable.fromCallable {
            for (i in 1..100) {
                updateTemperatureBar(i.toDouble()/100)
                synchronized(sleeper) {
                    sleeper.wait(50)
                }
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
    }

    private fun updateTemperatureBar(value: Double) {
        val startColor: Int = Color.parseColor("#d8de26")
        val endColor: Int = Color.parseColor("#ed3257")
        val inverseValue: Double = 1f - value
        val red: Double = Color.red(endColor) * value + Color.red(startColor) * inverseValue
        val green: Double = Color.green(endColor) * value + Color.green(startColor) * inverseValue
        val blue: Double = Color.blue(endColor) * value + Color.blue(startColor) * inverseValue
        val matrix = floatArrayOf(
            0f, 0f, 0f, 0f, red.toFloat(),
            0f, 0f, 0f, 0f, green.toFloat(),
            0f, 0f, 0f, 0f, blue.toFloat(),
            0f, 0f, 0f, 1f, 0f
        )
        binding.temperatureBar.progressDrawable.colorFilter = ColorMatrixColorFilter(matrix)
        binding.temperatureBar.progress = (100*value).toInt()
        binding.temperatureBarGlow.progress = (100*value).toInt()
        this.runOnUiThread {
            binding.temperatureBarText.text = getString(R.string.temperature_bar_text, (100 * value).toInt().toString())
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