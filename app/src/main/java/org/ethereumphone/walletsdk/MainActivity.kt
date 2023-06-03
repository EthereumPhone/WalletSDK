package org.ethereumphone.walletsdk

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch



class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val wallet = WalletSDK(this)

        CoroutineScope(Dispatchers.IO).launch {
            val res1 = wallet.changeChain(
                5,
                "https://rpc.ankr.com/eth_goerli"
            )
            Log.d("changeChain", res1)

            val res2 = wallet.signMessage("Launch control, this is Houston. We have go for launch.")
            Log.d("sign", res2)

            val res3 = wallet.sendTransaction(
                to = "0x3a4e6ed8b0f02bfbfaa3c6506af2db939ea5798c",
                value = "1000000000000000000", // One eth in wei
                data = "",
            )
            Log.d("sendTransaction", res3)
        }
    }
}



