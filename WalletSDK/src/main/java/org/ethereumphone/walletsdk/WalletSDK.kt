package org.ethereumphone.walletsdk

import android.content.Context
import dev.pinkroom.walletconnectkit.WalletConnectKit
import dev.pinkroom.walletconnectkit.WalletConnectKitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.ethereumphone.walletsdk.model.Config
import org.walletconnect.Session
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread


const val SYS_SERVICE = "android.os.WalletProxy"

class WalletSDK(
    context: Context,
    configWallet: Config? = null,
    web3RPC: String = "https://cloudflare-eth.com"
)  {
    private val cls: Class<*> = Class.forName(SYS_SERVICE)
    private val createSession = cls.declaredMethods[1]
    private val getUserDecision = cls.declaredMethods[3]
    private val hasBeenFulfilled = cls.declaredMethods[4]
    private val sendTransaction =  cls.declaredMethods[5]
    private val signMessageSys = cls.declaredMethods[6]
    private val getAddress = cls.declaredMethods[2]
    private var address: String? = null
    private var onConnected: ((address: String) -> Unit)? = null
    private val proxy = context.getSystemService("wallet")
    private val config = configWallet?.walletConnectKitConfig ?: WalletConnectKitConfig(
        context = context,
        bridgeUrl = "wss://bridge.aktionariat.com:8887",
        appUrl = "https://ethereumphone.org",
        appName = "WalletSDK",
        appDescription = ""
    )
    private var web3j: Web3j? = null
    private var walletConnectKit: WalletConnectKit? = null
    private var sysSession: String? = null

    init {
        if (proxy == null) {
            walletConnectKit = WalletConnectKit.Builder(config).build()
        } else {
            sysSession = createSession.invoke(proxy) as String
            val reqID = getAddress.invoke(proxy, sysSession) as String
            Thread.sleep(200)
            address = hasBeenFulfilled.invoke(proxy, reqID) as String
        }
        web3j = Web3j.build(HttpService(web3RPC))
    }

    /**
     * Sends transaction to
     */

    fun sendTransaction(to: String, value: String, data: String, gasPriceVAL: String? = null, gasAmount: String = "21000"): CompletableFuture<String> {
        val completableFuture = CompletableFuture<String>()
        var gasPrice = gasPriceVAL
        if(proxy != null) {
            // Use system-wallet

            CompletableFuture.runAsync {
                val ethGetTransactionCount = web3j!!.ethGetTransactionCount(
                    address, DefaultBlockParameterName.LATEST
                ).sendAsync().get()

                if (gasPrice == null) {
                    gasPrice = web3j?.ethGasPrice()?.sendAsync()?.get()?.gasPrice.toString()
                }

                val reqID = sendTransaction.invoke(proxy, sysSession, to, value, data, ethGetTransactionCount.transactionCount.toString(), gasPrice, gasAmount)

                var result = "notfulfilled"

                while (true) {
                    val tempResult =  hasBeenFulfilled!!.invoke(proxy, reqID)
                    if (tempResult != null) {
                        result = tempResult as String
                        if(result != "notfulfilled") {
                            break
                        }
                    }
                }
                completableFuture.complete(web3j!!.ethSendRawTransaction(result).sendAsync().get().transactionHash)
                //completableFuture.complete(result)
            }
            return completableFuture
        } else {
            val completableFuture = CompletableFuture<String>()

            CompletableFuture.runAsync {
                GlobalScope.launch(Dispatchers.Main) {
                    completableFuture.complete(walletConnectKit!!.performTransaction(
                        address = to,
                        value = value,
                        data = data).result.toString())
                }

            }
            return completableFuture
        }
    }

    fun signMessage(message: String): CompletableFuture<String> {
        val completableFuture = CompletableFuture<String>()
        if(proxy != null) {
            CompletableFuture.runAsync {
                val reqID = signMessageSys.invoke(proxy, sysSession, message) as String

                var result =  "notfulfilled"

                while (true) {
                    val tempResult =  hasBeenFulfilled!!.invoke(proxy, reqID)
                    if (tempResult != null) {
                        result = tempResult as String
                        if(result != "notfulfilled") {
                            break
                        }
                    }
                    Thread.sleep(50)
                }
                completableFuture.complete(result)
            }

            return completableFuture
        } else {
            val completableFuture = CompletableFuture<String>()

            CompletableFuture.runAsync {
                GlobalScope.launch(Dispatchers.Main) {
                    completableFuture.complete(walletConnectKit!!.personalSign(message).result.toString())
                }

            }
            return completableFuture
        }
    }

    /**
     * Creats connection to the Wallet system service.
     * If wallet is not found, user is redirect to WalletConnect login
     */
    fun createSession(onConnectedS: (address: String) -> Unit): String {
        if(proxy != null) {
            onConnected?.let { it(sysSession.orEmpty()) }
            return sysSession.orEmpty()
        } else {
            if (walletConnectKit?.isSessionStored!!) {
                // TODO: create callback
                //walletConnectKit?.loadSession(this)
                walletConnectKit?.address?.let { onConnected?.let { it1 -> it1(it) } }
            }
            // TODO: create callback
            //walletConnectKit?.createSession(callback = {})
            onConnected = onConnectedS
        }
        return ""
    }

    private fun onSessionApproved() {
        walletConnectKit?.address?.let {
            onConnected?.let { it1 -> it1(it) }
        }
    }

    private fun onSessionConnected() {
        walletConnectKit?.address ?: walletConnectKit?.requestHandshake()
    }

    fun getAddress(): String {
        if (proxy != null) {
            return address.orEmpty()
        }
        return walletConnectKit?.address.orEmpty()
    }

    fun onMethodCall(call: Session.MethodCall) {
        println(call.toString())
    }

    fun onStatus(status: Session.Status) {
        println(status.toString())
        when (status) {
            is Session.Status.Approved -> onSessionApproved()
            is Session.Status.Connected -> onSessionConnected()
            else -> {println("Something else")}
        }
    }

    fun isEthOS(): Boolean {
        return proxy != null
    }
}