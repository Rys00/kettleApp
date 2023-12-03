package pl.sprytneDzbany.kettleApp

import android.app.Activity
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.Callable


class WebClient(
    serverURI: URI,
    private val context: Activity,
    private val onConnectCallback: (webClient: WebClient) -> Any
) : WebSocketClient(serverURI) {

    private val TAG = "WebClient"
    private val awaitTimeout: Long = 5000
    var verified = false
    private val responseLock = Object()
    private val mainThreadLock = Object()
    private var lastResponse = JSONObject()
    override fun onOpen(serverHandshake: ServerHandshake?) {
        Log.i(TAG, "Opened connection to $uri")
        onConnectCallback(this)
    }

    fun verify(callback: () -> Unit) {
        Log.i(TAG, "Testing if responding device is our kettle...")
        Log.i(TAG, "Sending question to verify...")
        val data = JSONObject()
        var question = ""
        synchronized(mainThreadLock) {
            context.runOnUiThread {
                question = context.getString(R.string.verify_question)
                synchronized(mainThreadLock) {
                    mainThreadLock.notifyAll()
                }
            }
            if(question == "") {
                mainThreadLock.wait()
            }
        }
        data.put("question", question)
        sendCommand("verify", data).subscribe {response ->
            val code = response.get("code").toString()
            val message = response.get("message")
            if(code != "200") {
                Log.i(TAG, "Server response: Error code $code '$message'")
                return@subscribe
            }
            if(message == context.getString(R.string.verify_answer)) {
                Log.i(TAG, "Verification successful - '$uri' is our kettle!")
                verified = true
                callback()
            } else {
                Log.i(TAG, "Verification failed '$uri' isn't our kettle!")
                verified = false
                callback()
            }
        }
    }

    fun sendCommand(command: String, extraData: JSONObject = JSONObject()): Observable<JSONObject> {
        extraData.put("command", command)
        send(extraData.toString())

        // waiting for response
        return Observable.fromCallable(Callable fromCallable@{
            lastResponse = JSONObject("{'code': 408, 'message': 'timeout'}")
            synchronized(responseLock) {
                try {
                    responseLock.wait(awaitTimeout)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            val response = lastResponse.toString()
            Log.i(TAG, "Received message - $response")

            return@fromCallable lastResponse
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun onMessage(s: String) {
        var data = JSONObject("{'code': '400'}")
        try {
            data = JSONObject(s)
        } catch (e: Exception) {
            Log.i(TAG, "Error while parsing message from server")
        }
        lastResponse = data
        synchronized(responseLock) {
            responseLock.notifyAll()
        }
    }

    override fun onClose(i: Int, s: String, b: Boolean) {
        Log.i(TAG, "Closed $s")
    }

    override fun onError(e: Exception) {
        Log.i(TAG, "Error " + e.message)
    }
}