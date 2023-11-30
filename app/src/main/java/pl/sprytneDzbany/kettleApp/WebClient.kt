package pl.sprytneDzbany.kettleApp

import android.app.Activity
import android.content.Context
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import pl.sprytneDzbany.kettleApp.PermissionManger.getString
import java.net.URI
import java.util.concurrent.Callable


class WebClient(
    serverURI: URI,
    private val context: Activity,
    private val onConnectCallback: (webClient: WebClient) -> Any
) : WebSocketClient(serverURI) {

    private val TAG = "WebClient"
    private val timeout: Long = 5000
    var verified = false
    val responseToken = Object()
    val mainThreadToken = Object()
    private var lastResponse = JSONObject()
    override fun onOpen(serverHandshake: ServerHandshake?) {
        Log.i(TAG, "Opened connection to $uri")
        onConnectCallback(this)
    }

    fun verify(callback: () -> Any) {
        Log.i(TAG, "Testing if responding device is our kettle...")
        Log.i(TAG, "Sending question to verify...")
        val data = JSONObject()
        var question = ""
        synchronized(mainThreadToken) {
            context.runOnUiThread {
                question = context.getString(R.string.verify_question)
                synchronized(mainThreadToken) {
                    mainThreadToken.notifyAll()
                }
            }
            mainThreadToken.wait()
        }
        data.put("question", question)
        val answer = sendCommand("verify", data)
        answer.subscribe {response ->
            val code = response.get("code").toString()
            val message = response.get("message")
            if(code != "200") {
                Log.i(TAG, "Server response: Error code $code '$message'")
                return@subscribe
            }
            if(message == context.getString(R.string.verify_answer)) {
                Log.i(TAG, "Verify successful '$uri' is kettle!")
                verified = true
                callback()
            } else {
                Log.i(TAG, "Verify unsuccessful '$uri' isn't kettle!")
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
            synchronized(responseToken) {
                try {
                    responseToken.wait(timeout)
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
        synchronized(responseToken) {
            responseToken.notifyAll()
        }
    }

    override fun onClose(i: Int, s: String, b: Boolean) {
        Log.i(TAG, "Closed $s")
    }

    override fun onError(e: Exception) {
        Log.i(TAG, "Error " + e.message)
    }
}