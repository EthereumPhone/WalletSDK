package org.ethereumphone.walletsdk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wallet = WalletSDK(this)
        println("Chain ID: ${wallet.getChainId()}")
        wallet.changeChainid(5).whenComplete { s, throwable ->
             if (throwable != null) {
                println("Error: $throwable")
            } else {
                println("Chain ID: ${wallet.getChainId()}")
            }
        }
        wallet.signMessage(
            message = "Launch control, this is Houston. We have go for launch."
        ).whenComplete { s, throwable ->
            if (s == WalletSDK.DECLINE) {
                println("Signing declined")
            } else {
                println("Signed message: $s")
            }
        }
        wallet.sendTransaction(
            to = "0x3a4e6ed8b0f02bfbfaa3c6506af2db939ea5798c", // mhaas.eth
            value = "1000000000000000000", // One eth in wei
            data = ""
        ).whenComplete {t, bhrowable ->
            // Returns tx-id
            if (t == WalletSDK.DECLINE) {
                println("Send transaction has been declined")
            } else {
                println(t)
            }
        }

    }
}


