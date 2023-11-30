package pl.sprytneDzbany.kettleApp

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Optional
import java.util.concurrent.Callable
import kotlin.math.pow


class NetworkScanner(private val context: Activity) {

    private val TAG = "NetworkScanner"
    data class LocalDeviceInfo(val host: String, val strMacAddress: String?)
    fun findActiveKettles(port: Int = 2137): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val currentNetwork = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(currentNetwork) ?: return false
            val linkAddress = getIPv4Address(linkProperties.linkAddresses) ?: return false
            val address = linkAddress.address.hostAddress
            val netmask = linkAddress.prefixLength
            if(netmask < 22) {return false}
            Log.i(TAG, "My address: $address")
            Log.i(TAG, "My netmask: $netmask")
            Log.i(TAG, "Scanning network addresses...")
            val binaryNetworkPart = getBinaryNetworkPart(address, netmask)
            val hostAmount = (2.toDouble().pow((32 - netmask))-2).toInt()
            for (hostId in 1..hostAmount) {
                val ipAddress = getHostAddress(binaryNetworkPart, netmask, hostId)
                //Log.i(TAG, ipAddress)
                openConnection(ipAddress, port).subscribe fromCallable@{ result ->
                    if(!result.isPresent) {
                        //Log.i(TAG, "Unsuccessful connection to $ipAddress")
                        return@fromCallable
                    }
                    Log.i(TAG, "Found opened port $port at address: $ipAddress")
                    val socket = result.get()
                    socket.close()
                    val socketAddress = socket.remoteSocketAddress as InetSocketAddress
                    val hostAddress = socketAddress.address.hostAddress
                    val uri = URI("ws://$hostAddress:$port/")
                    val webClient = WebClient(uri, context) {webClient ->
                        webClient.verify(fun() {
                            if (webClient.verified) {
                                Log.i(TAG, "Kettle verified")
                                webClient.sendCommand("ledOn").subscribe { response ->
                                    val message = response.get("message")
                                    Log.i(TAG, "$message")
                                    Thread.sleep(1000)
                                    webClient.sendCommand("ledOff").subscribe { r ->
                                        val m = r.get("message")
                                        Log.i(TAG, "$m")
                                    }
                                }
                            }
                        })
                    }
                    webClient.connect()
                }
            }
        } catch (e: Exception) {
            throw e
            return false
        }
        return true
    }

    private fun getIPv4Address(linkAddresses: List<LinkAddress>): LinkAddress? {
        linkAddresses.forEach { address ->
            if(address.address.address.size == 4) {
                return address
            }
        }
        return null
    }

    private fun openConnection(ipAddress: String, port: Int, timeout: Int = 5000): Observable<Optional<Socket>> {
        return Observable.fromCallable(Callable fromCallable@{
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(ipAddress, port), timeout)
            } catch (e: Exception) {
                return@fromCallable Optional.empty()
            }
            return@fromCallable Optional.of(socket)
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }
    private fun getHostAddress(binaryNetworkPart: String, netmask: Int, i: Int): String {
        val binaryHostPart = Integer.toBinaryString(i).padStart(32-netmask, '0')
        val binaryAddress = binaryNetworkPart+binaryHostPart
        return String.format("%d.%d.%d.%d",
            binaryAddress.substring(0, 8).toInt(2),
            binaryAddress.substring(8, 16).toInt(2),
            binaryAddress.substring(16, 24).toInt(2),
            binaryAddress.substring(24, 32).toInt(2)
        )
    }

    private fun getBinaryNetworkPart(address: String, netmask: Int): String {
        var binaryAddress = ""
        address.split(".").forEach { byte ->
            binaryAddress += Integer.toBinaryString(byte.toInt()).padStart(8, '0')
        }
        return binaryAddress.substring(0, netmask)
    }
}