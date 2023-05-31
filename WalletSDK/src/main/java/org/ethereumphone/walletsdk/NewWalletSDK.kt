package org.ethereumphone.walletsdk

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.ethereumphone.walletsdk.model.NoSysWalletException
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

@SuppressLint("WrongConstant") // need this to make Android Studio behave
class NewWalletSDK(
    private val context: Context,
    private val web3jInstance: Web3j = Web3j.build(HttpService("https://rpc.ankr.com/eth")), // starts on mainnet
) {
    // System service methods
    private val cls: Class<*> = Class.forName(SYS_SERVICE_CLASS)
    private val changeChainId = cls.declaredMethods[1]
    private val createSession = cls.declaredMethods[2]
    private val getAddress = cls.declaredMethods[3]
    private val getChainId = cls.declaredMethods[4]
    private val getUserDecision = cls.declaredMethods[5]
    private val hasBeenFulfilled = cls.declaredMethods[6]
    private val sendTransaction =  cls.declaredMethods[7]
    private val signMessageSys = cls.declaredMethods[8]

    private var address: String? = null
    private var proxy : Any? = null
    private var sysSession: String? = null

    init {
        proxy = initializeProxyService()
        sysSession = createSession.invoke(proxy) as String
        address = initialAddressRequest()
    }

    /**
     * Initializes the public system wallet proxy service.
     */
    private fun initializeProxyService() = context.getSystemService(SYS_SERVICE) ?: throw NoSysWalletException("System wallet not found on this device")

    /**
     * Wallet only supports one address as of right now, thus we only need to get the wallet address once
     */
    private fun initialAddressRequest(): String {
        val reqID = getAddress.invoke(proxy, sysSession) as String
        while ((hasBeenFulfilled.invoke(proxy, reqID) as String) == NOTFULFILLED) {
            Thread.sleep(10)
        }
        return hasBeenFulfilled.invoke(proxy, reqID) as String
    }

    private fun changeWeb3jChain() {

    }


    /**
     *
     */
    fun sendTransaction(
        to: String,
        value: String,
        data: String,
        gasPrice: String? = null,
        gasAmount: String = "21000",
        chainId: Int = 1
    ) {

    }



    fun singMessage(message: String, type: String = "personal_sign"): String {
        val reqId = getChainId.invoke(proxy, sysSession) as String
        return hasBeenFulfilled.invoke(proxy, reqId) as String
    }


    fun getChainId(): Int {
        val reqId = getChainId.invoke(proxy, sysSession) as String
        return Integer.parseInt(hasBeenFulfilled.invoke(proxy, reqId) as String)
    }

    fun changeChainId(chainId: Int): String {
        val reqId = changeChainId.invoke(proxy, sysSession, chainId) as String
        return hasBeenFulfilled.invoke(proxy, reqId) as String
    }


    fun isEthOS(): Boolean {
        return proxy != null
    }



    companion object {
        const val SYS_SERVICE_CLASS = "android.os.WalletProxy"
        const val SYS_SERVICE = "wallet"
        const val DECLINE = "decline"
        const val NOTFULFILLED = "notfulfilled"
    }
}