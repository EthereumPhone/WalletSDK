package org.ethereumphone.walletsdk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import org.ethereumphone.walletsdk.model.NoSysWalletException
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import java.util.concurrent.CompletableFuture

class WalletSDK(
    context: Context,
    web3RPC: String = "https://cloudflare-eth.com"
)  {

    companion object {
        const val SYS_SERVICE_CLASS = "android.os.WalletProxy"
        const val SYS_SERVICE = "wallet"
        const val DECLINE = "decline"
        const val NOTFULFILLED = "notfulfilled"
    }


    private val cls: Class<*> = Class.forName(SYS_SERVICE_CLASS)
    private val createSession = cls.declaredMethods[2]
    private val getUserDecision = cls.declaredMethods[5]
    private val hasBeenFulfilled = cls.declaredMethods[6]
    private val sendTransaction =  cls.declaredMethods[7]
    private val signMessageSys = cls.declaredMethods[8]
    private val getAddress = cls.declaredMethods[3]
    private val getChainId = cls.declaredMethods[4]
    private val changeChainId = cls.declaredMethods[1]
    private var address: String? = null
    @SuppressLint("WrongConstant")
    private val proxy = context.getSystemService(SYS_SERVICE)
    private var web3j: Web3j? = null
    private var sysSession: String? = null

    init {
        if (proxy == null) {
            throw NoSysWalletException("No system wallet found")
        } else {
            sysSession = createSession.invoke(proxy) as String
            val reqID = getAddress.invoke(proxy, sysSession) as String
            while ((hasBeenFulfilled.invoke(proxy, reqID) as String) == NOTFULFILLED) {
                Thread.sleep(10)
            }
            address = hasBeenFulfilled.invoke(proxy, reqID) as String
        }
        web3j = Web3j.build(HttpService(web3RPC))
    }

    /**
     * Sends transaction to
     */

    @RequiresApi(Build.VERSION_CODES.N)
    fun sendTransaction(to: String, value: String, data: String, gasPrice: String? = null, gasAmount: String = "21000", chainId: Int = 1): CompletableFuture<String> {
        val completableFuture = CompletableFuture<String>()
        var gasPriceVAL = gasPrice
        if(proxy != null) {
            // Use system-wallet

            CompletableFuture.runAsync {
                val ethGetTransactionCount = web3j!!.ethGetTransactionCount(
                    address, DefaultBlockParameterName.LATEST
                ).sendAsync().get()

                if (gasPrice == null) {
                    gasPriceVAL = web3j?.ethGasPrice()?.sendAsync()?.get()?.gasPrice.toString()
                }

                val reqID = sendTransaction.invoke(proxy, sysSession, to, value, data, ethGetTransactionCount.transactionCount.toString(), gasPriceVAL, gasAmount, chainId)

                var result = NOTFULFILLED

                while (true) {
                    val tempResult =  hasBeenFulfilled!!.invoke(proxy, reqID)
                    if (tempResult != null) {
                        result = tempResult as String
                        if(result != NOTFULFILLED) {
                            break
                        }
                    }
                    Thread.sleep(100)
                }
                if (result == DECLINE) {
                    completableFuture.complete(DECLINE)
                } else {
                    val txResult = web3j!!.ethSendRawTransaction(result).sendAsync().get()
                    val txHash = txResult.transactionHash
                    completableFuture.complete(txHash)
                }
            }
            return completableFuture
        } else {
            throw NoSysWalletException("No system wallet found")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun signMessage(message: String, type: String = "personal_sign"): CompletableFuture<String> {
        val completableFuture = CompletableFuture<String>()
        if(proxy != null) {
            CompletableFuture.runAsync {
                val reqID = signMessageSys.invoke(proxy, sysSession, message, type) as String

                var result =  NOTFULFILLED

                while (true) {
                    val tempResult =  hasBeenFulfilled!!.invoke(proxy, reqID)
                    if (tempResult != null) {
                        result = tempResult as String
                        if(result != NOTFULFILLED) {
                            break
                        }
                    }
                    Thread.sleep(100)
                }
                completableFuture.complete(result)
            }

            return completableFuture
        } else {
            throw NoSysWalletException("No system wallet found")
        }
    }

    /**
     * Creates connection to the Wallet system service.
     * If wallet is not found, user is redirect to WalletConnect login
     */
    fun createSession(onConnected: ((address: String) -> Unit)? = null): String {
        if(proxy != null) {
            onConnected?.let { it(sysSession.orEmpty()) }
            return sysSession.orEmpty()
        } else {
            throw NoSysWalletException("No system wallet found")
        }
    }


    fun getAddress(): String {
        if (proxy != null) {
            return address.orEmpty()
        } else {
            throw NoSysWalletException("No system wallet found")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun getChainId(): Int {
        if (proxy != null) {
            val completableFuture = CompletableFuture<Int>()
            CompletableFuture.runAsync {
                val reqId = getChainId.invoke(proxy, sysSession) as String
                while ((hasBeenFulfilled.invoke(proxy, reqId) as String) == NOTFULFILLED) {
                    Thread.sleep(10)
                }
                completableFuture.complete(Integer.parseInt(hasBeenFulfilled.invoke(proxy, reqId) as String))
            }
            return completableFuture.get()
        } else {
            throw NoSysWalletException("No system wallet found")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun changeChainid(chainId: Int): CompletableFuture<String> {
        val completableFuture = CompletableFuture<String>()
        if(proxy != null) {
            CompletableFuture.runAsync {
                val reqID = changeChainId.invoke(proxy, sysSession, chainId) as String

                var result =  NOTFULFILLED

                while (true) {
                    val tempResult =  hasBeenFulfilled!!.invoke(proxy, reqID)
                    if (tempResult != null) {
                        result = tempResult as String
                        if(result != NOTFULFILLED) {
                            break
                        }
                    }
                    Thread.sleep(100)
                }
                completableFuture.complete(result)
            }

            return completableFuture
        } else {
            throw NoSysWalletException("No system wallet found")
        }
    }

    fun isEthOS(): Boolean {
        return proxy != null
    }
}