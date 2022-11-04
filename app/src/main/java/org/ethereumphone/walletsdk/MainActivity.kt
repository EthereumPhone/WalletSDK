package org.ethereumphone.walletsdk

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TestActivity() }
    }
}

@Composable
fun TestActivity() {
    val context = LocalContext.current
    val wallet = WalletSDK(context)


    // How to sign Message
    wallet.signMessage(
        message = "Launch control this is Houston, we are go for launch."
    ).whenComplete { s, throwable ->
        // Returns signed message
        if (s == WalletSDK.DECLINE) {
            println("Sign message has been declined")
        } else {
            println(s)
        }
    }

    // How to send send Transactions
    wallet.sendTransaction(
        to = "0x3a4e6ed8b0f02bfbfaa3c6506af2db939ea5798c", // mhaas.eth
        value = "1000000000000000000", // One eth in wei
        data = ""
    ).whenComplete {s, throwable ->
        // Returns tx-id
        if (s == WalletSDK.DECLINE) {
            println("Send transaction has been declined")
        } else {
            println(s)
        }
    }

}

