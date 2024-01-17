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
import java.nio.channels.NotYetConnectedException
import java.util.concurrent.Callable
import java.util.UUID


class WebClient(
    serverURI: URI,
    private val context: Activity,
    private var onConnectCallback: ((webClient: WebClient) -> Any)? = null
) : WebSocketClient(serverURI) {

    private val TAG = "WebClient"
    private val awaitTimeout: Long = 2000
    var verified = false
    private var currentMessageId: UUID? = null
    private val responseLock = Object()
    private val busyLock = Object()
    private val connectingLock = Object()
    private val mainThreadLock = Object()
    private var lastResponse = JSONObject()
    override fun onOpen(serverHandshake: ServerHandshake?) {
        Log.i(TAG, "Opened connection to $uri")
        onConnectCallback?.let { it(this) }
        onConnectCallback = null
        synchronized(connectingLock) {
            connectingLock.notifyAll()
        }
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
                Log.w(TAG, "Verification failed: Server error code $code '$message'")
                return@subscribe
            }
            if(message == context.getString(R.string.verify_answer)) {
                Log.i(TAG, "Verification successful - '$uri' is our kettle!")
                verified = true
                callback()
            } else {
                Log.w(TAG, "Verification failed '$uri' isn't our kettle!")
                verified = false
                callback()
            }
        }
    }

    private fun getTimeoutResponse(): JSONObject {
        return JSONObject("{'code': 408, 'message': 'timeout', 'uuid': '$currentMessageId'}")
    }

    fun sendCommand(command: String, extraData: JSONObject = JSONObject()): Observable<JSONObject> {
        if(currentMessageId != null) {
            synchronized(busyLock) {
                // waits until previous message received its response
                busyLock.wait()
            }
        }

        // generates message uuid
        currentMessageId = UUID.randomUUID()
        extraData.put("uuid", currentMessageId?.toString())
        extraData.put("command", command)

        try {
            send(extraData.toString())
        } catch (e: NotYetConnectedException) {
            Log.w(TAG, "Connection with server was closed, trying to open once again...")
            connect()
            synchronized(connectingLock) {
                connectingLock.wait()
            }
            Log.w(TAG, "Connection with server reestablished!")
        }
        send(extraData.toString())

        // waiting for response or timeout
        return Observable.fromCallable(Callable fromCallable@{
            lastResponse = getTimeoutResponse()
            synchronized(responseLock) {
                try {
                    responseLock.wait(awaitTimeout)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }

            // frees busy lock
            synchronized(busyLock) {
                currentMessageId = null
                busyLock.notifyAll()
            }
            return@fromCallable lastResponse
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun onMessage(s: String) {
        var data: JSONObject? = null
        try {
            data = JSONObject(s)
        } catch (e: Exception) {
            Log.w(TAG, "Error while parsing message from server!")
        }
        try {
            if(
                data == null
                || currentMessageId == null
                || data.get("uuid") != currentMessageId.toString()
                )
            {
                Log.w(TAG, "Received not awaited message! - $data")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Received not identified message! - $data")
            return
        }
        lastResponse = data
        synchronized(responseLock) {
            responseLock.notifyAll()
        }
        Log.i(TAG, "Received awaited message - $data")
    }

    override fun onClose(i: Int, s: String, b: Boolean) {
        Log.i(TAG, "Closed $s")
    }

    override fun onError(e: Exception) {
        Log.i(TAG, "Error " + e.message)
    }
}