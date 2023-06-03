package org.ethereumphone.walletsdk

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ethereumphone.walletsdk.model.NoSysWalletException
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService

@SuppressLint("WrongConstant") // need this to make Android Studio behave
class WalletSDK(
    private val context: Context,
    private var web3jInstance: Web3j = Web3j.build(HttpService("https://rpc.ankr.com/eth")), // starts on mainnet
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

        CoroutineScope(Dispatchers.IO).launch {
            address = initialAddressRequest()
        }
    }

    /**
     * In case the system-service is not found, it throws an execption in the init body of the WalletSDK, so that I don't need to check the proxy on every function
     */
    private fun initializeProxyService() = context.getSystemService(SYS_SERVICE) ?: throw NoSysWalletException("System wallet not found on this device")

    /**
     * Wallet only supports one address as of right now, thus we only need to get the wallet address once
     */
    private suspend fun initialAddressRequest(): String = coroutineScope {
        val deferredResult = async {
            val reqId = getAddress.invoke(proxy, sysSession) as String
            while((hasBeenFulfilled.invoke(proxy, reqId) as String) == NOTFULFILLED) {
                delay(10)
            }
            hasBeenFulfilled.invoke(proxy, reqId) as String
        }

        deferredResult.await()
    }

    suspend fun sendTransaction(
        to: String,
        value: String,
        data: String,
        gasPrice: String? = null,
        gasAmount: String = "21000"
    ): String = coroutineScope {
        val deferredResult = async {
            val result: String
            val gas = if (gasPrice.isNullOrBlank()) {
                web3jInstance.ethGasPrice().send().gasPrice.toString()
            } else gasPrice
            val ethGetTransactionCount = web3jInstance.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).send()
            val reqId = sendTransaction.invoke(proxy, sysSession, to, value, data, ethGetTransactionCount.transactionCount.toString(), gas, gasAmount, 0)

            while((hasBeenFulfilled.invoke(proxy, reqId) as String) == NOTFULFILLED) {
                delay(10)
            }
            result = hasBeenFulfilled.invoke(proxy, reqId) as String

            if (result == DECLINE) DECLINE else web3jInstance.ethSendRawTransaction(result).send().transactionHash
        }

        deferredResult.await()
    }

    suspend fun signMessage(message: String, type: String = "personal_sign"): String = coroutineScope {

        val deferredResult = async {
            val reqId = signMessageSys.invoke(proxy, sysSession, message, type) as String
            while((hasBeenFulfilled.invoke(proxy, reqId) as String) == NOTFULFILLED) {
                delay(10)
            }
            hasBeenFulfilled.invoke(proxy, reqId) as String
        }
        deferredResult.await()
    }


    suspend fun getChainId(): Int = coroutineScope {

        val deferredResult = async {
            val reqId = getChainId.invoke(proxy, sysSession) as String
            while((hasBeenFulfilled.invoke(proxy, reqId) as String) == NOTFULFILLED) {
                delay(10)
            }
            Integer.parseInt(hasBeenFulfilled.invoke(proxy, reqId) as String)
        }
        deferredResult.await()
    }

    /**
     *
     */
    suspend fun changeChain(
        chainId: Int,
        rpcEndpoint: String
    ): String = coroutineScope {

        val deferredResult = async {
            web3jInstance = Web3j.build(HttpService(rpcEndpoint))
            val reqId = changeChainId.invoke(proxy, sysSession, chainId) as String
            while((hasBeenFulfilled.invoke(proxy, reqId) as String) == NOTFULFILLED) {
                delay(10)
            }
            hasBeenFulfilled.invoke(proxy, reqId) as String
        }
        deferredResult.await()
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