package pl.sprytneDzbany.kettleApp

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.util.Log
import android.widget.TextView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Optional
import java.util.concurrent.Callable
import kotlin.math.pow


class NetworkScanner(
    private val context: Activity,
    private val tvProggress: TextView? = null,
) {

    private val TAG = "NetworkScanner"
    private val scanCompleteLock = Object()
    private val scanResulLock = Object()

    fun findActiveKettles(port: Int = 2137): Observable<Optional<ArrayList<WebClient>>> {
        return Observable.fromCallable fromCallable@{
            try {
                displayProgressMessage(R.string.progress_fetching_network_config)
                val linkAddress = getIPv4LinkAddress()?: return@fromCallable Optional.empty()
                val result: Optional<ArrayList<WebClient>>
                val verifiedMatches = ArrayList<WebClient>()
                scanHosts(linkAddress, port).subscribe fromScanHost@{ r ->
                    if(!r.isPresent || r.get().isEmpty()) {
                        synchronized(scanResulLock) {
                            scanResulLock.notifyAll()
                        }
                        return@fromScanHost
                    }
                    val allMatches = r.get()
                    var count = 0
                    displayProgressMessage(R.string.progress_verifying_devices)
                    allMatches.forEach { webClient ->
                        webClient.verify {
                            count += 1
                            if(webClient.verified) {
                                verifiedMatches.add(webClient)
                            } else {
                                webClient.close()
                            }
                            if (count == allMatches.size) {
                                synchronized(scanResulLock) {
                                    scanResulLock.notifyAll()
                                }
                            }
                        }
                    }
                }
                synchronized(scanResulLock) {
                    scanResulLock.wait(10000)
                }
                result = Optional.of(verifiedMatches)
                return@fromCallable result
            } catch (e: Exception) {
                Log.e(TAG, e.printStackTrace().toString())
                return@fromCallable Optional.empty()
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun scanHosts(onLinkAddress: LinkAddress, lookForOpenPort: Int):
            Observable<Optional<ArrayList<WebClient>>> {
        return Observable.fromCallable fromCallable@{
            var scanResult = Optional.empty<ArrayList<WebClient>>()

            val address = onLinkAddress.address.hostAddress
            val netmask = onLinkAddress.prefixLength
            if (netmask < 22) {return@fromCallable scanResult}

            displayProgressMessage(R.string.progress_scanning_network)
            Log.i(TAG, "Scanning network addresses...")

            val binaryNetworkPart = getBinaryNetworkPart(address, netmask)
            val hostAmount = (2.toDouble().pow((32 - netmask)) - 2).toInt()

            var scanned = 0
            val matches = ArrayList<WebClient>()
            for (hostId in 1..hostAmount) {
                val ipAddress = getHostAddress(binaryNetworkPart, hostId)
                openConnection(ipAddress, lookForOpenPort).subscribe fromOpenConnection@{ result ->
                    if (!result.isPresent) {
                        //Log.i(TAG, "Unsuccessful connection to $ipAddress")
                        scanned += 1
                        synchronized(scanCompleteLock) {
                            if(scanned == hostAmount) {
                                scanCompleteLock.notifyAll()
                            }
                        }
                        return@fromOpenConnection
                    }
                    Log.i(TAG, "Found opened port $lookForOpenPort at address: $ipAddress")
                    val socket = result.get()
                    socket.close()
                    val socketAddress = socket.remoteSocketAddress as InetSocketAddress
                    val hostAddress = socketAddress.address.hostAddress
                    val uri = URI("ws://$hostAddress:$lookForOpenPort/")
                    val webClient = WebClient(uri, context) {
                        scanned += 1
                        synchronized(scanCompleteLock) {
                            if(scanned == hostAmount) {
                                scanCompleteLock.notifyAll()
                            }
                        }
                    }
                    matches.add(webClient)
                    webClient.connect()
                }
            }
            synchronized(scanCompleteLock) {
                scanCompleteLock.wait(5000)
            }
            scanResult = Optional.of(matches)
            Log.i(TAG, "Scanning complete!")
            return@fromCallable scanResult
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun getIPv4LinkAddress(): LinkAddress? {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val currentNetwork = connectivityManager.activeNetwork
        val linkProperties =
            connectivityManager.getLinkProperties(currentNetwork)?: return null
        val linkAddress = selectIPv4LinkAddress(linkProperties.linkAddresses) ?: return null

        val address = linkAddress.address.hostAddress
        val netmask = linkAddress.prefixLength

        Log.i(TAG, "My address: $address")
        Log.i(TAG, "My netmask: $netmask")
        return linkAddress
    }

    private fun selectIPv4LinkAddress(linkAddresses: List<LinkAddress>): LinkAddress? {
        linkAddresses.forEach { address ->
            if(address.address.address.size == 4) {
                return address
            }
        }
        return null
    }

    private fun openConnection(ipAddress: String, port: Int, timeout: Int = 1000): Observable<Optional<Socket>> {
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
    private fun getHostAddress(binaryNetworkPart: String, i: Int): String {
        val netmask = binaryNetworkPart.length
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

    private fun displayProgressMessage(messageStringPointer: Int) {
        if(tvProggress != null) {
            context.runOnUiThread {
                val message = context.getString(messageStringPointer)
                Log.i(TAG, "Displaying progress message - $message")
                tvProggress.text = message
            }
        }
    }
}