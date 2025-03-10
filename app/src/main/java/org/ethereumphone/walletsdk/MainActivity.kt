package org.ethereumphone.walletsdk

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val bundlerRPCURL = BuildConfig.BUNDLER_RPC_URL

        val wallet = WalletSDK(
            context = this,
            bundlerRPCUrl = bundlerRPCURL,
            web3jInstance = Web3j.build(HttpService("https://base.llamarpc.com"))
        )


        CoroutineScope(Dispatchers.IO).launch {
            val beforeAddr = System.currentTimeMillis()
            val address = wallet.getAddress()
            val afterAddr = System.currentTimeMillis()
            Log.d("addresstiming", (afterAddr-beforeAddr).toString())
            Log.d("address", address)

            val signedMessage = wallet.signMessage(
                message = "Hello World",
                chainId = 8453
            )

            Log.d("sign", signedMessage)
        }
    }
}



