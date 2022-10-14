package org.ethereumphone.walletsdk

import android.content.Context
import dev.pinkroom.walletconnectkit.WalletConnectKitConfig

const val SYS_SERVICE = "android.os.WalletProxy"


class WalletSDK(
    context: Context,

) {
    var cls: Class<*>? = Class.forName(SYS_SERVICE)
    var proxy = context.getSystemService("wallet")


    /**
     * Sends transaction to
     */
    fun performTransaction() {
        val connect = cls?.getMethod("performTransaction")
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