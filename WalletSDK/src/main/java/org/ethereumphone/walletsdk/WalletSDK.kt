package org.ethereumphone.walletsdk

import android.content.Context
import dev.pinkroom.walletconnectkit.WalletConnectKitConfig
import dev.pinkroom.walletconnectkit.WalletConnectKit
import kotlin.concurrent.thread


const val SYS_SERVICE = "android.os.WalletProxy"

class WalletSDK(
    context: Context,
    walletConnectKitConfig: WalletConnectKitConfig? = null

) {
    private val cls: Class<*>? = Class.forName(SYS_SERVICE)
    private val proxy = context.getSystemService("wallet")
    private val config = walletConnectKitConfig ?: WalletConnectKitConfig(
        context = context,
        bridgeUrl = "wss://bridge.aktionariat.com:8887",
        appUrl = "https://ethereumphone.org",
        appName = "WalletSDK",
        appDescription = ""
    )
    private var walletConnectKit: WalletConnectKit? = null

    init {
        walletConnectKit = WalletConnectKit.Builder(config).build()
    }

    /**
     * Sends transaction to
     */

    //TODO: request id check for request completion
    fun sendTransaction(): String {
        if(proxy != null) {
            val sendTransaction = cls?.getMethod("sendTransaction")
            val hasBeenFulfilled = cls?.getMethod("hasBeenFulfilled")
            var result =  "notfulfilled"

            thread {
                result = hasBeenFulfilled!!.invoke(proxy) as String
                if(result != "notfulfilled") {
                    Thread.interrupted()
                }
            }
            return result
        }
        return ""
    }

    fun signMessage(): String {
        if(proxy != null) {
            val signMessage = cls?.getMethod("signMessage")
            val hasBeenFulfilled = cls?.getMethod("hasBeenFulfilled")
            var result =  "notfulfilled"

            thread {
                result = hasBeenFulfilled!!.invoke(proxy) as String
                if(result != "notfulfilled") {
                    Thread.interrupted()
                }
            }
            return result
        }
        return ""
    }

    /**
     * Creats connection to the Wallet system service.
     * If wallet is not found, user is redirect to WalletConnect login
     */
    fun createSession(): String {
        if(proxy != null) {
            val createSession = cls?.getMethod("createSession")
            return createSession!!.invoke(proxy) as String
        }
        return ""
    }
}