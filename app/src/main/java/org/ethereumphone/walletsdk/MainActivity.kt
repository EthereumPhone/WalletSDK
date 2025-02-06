package org.ethereumphone.walletsdk

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val bundlerRPCURL = BuildConfig.BUNDLER_RPC_URL

        val wallet = WalletSDK(
            context = this,
            bundlerRPCUrl = bundlerRPCURL,
            web3jInstance = Web3j.build(HttpService("https://sepolia.base.org"))
        )

        CoroutineScope(Dispatchers.IO).launch {
            val address = wallet.getAddress()
            Log.d("address", address)

            val res = wallet.sendTransaction(
                to = "0x33351BF3c35184a110fCF7a848b190dDFB33c3aa",
                value = "100000000000000",
                data = "",
                chainId = 84532,
                from = address
            )
            println(res)

            val signedMessage = wallet.signMessage(
                message = "Hello World",
                from = address,
                chainId = 84532
            )

            Log.d("sign", signedMessage)
        }
    }
}



