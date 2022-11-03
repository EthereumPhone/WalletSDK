package org.ethereumphone.walletsdk

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.ethereumphone.walletsdk.WalletSDK


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TestActivity() }
    }
}

@Composable
fun TestActivity() {
    var con = LocalContext.current
    var test = WalletSDK(con)


    // Sign Message
    test.signMessage(
        message = "Launch control this is Houston, we are go for launch."
    ).whenComplete { s, throwable ->
        // Returns signed message
        println(s)
    }

    // Send Transactions
    test.sendTransaction(
        to = "0x3a4e6ed8b0f02bfbfaa3c6506af2db939ea5798c",
        value = "1000000000000000000",
        data = ""
    ).whenComplete {s, throwable ->
        // Returns tx-id
        println(s)
    }

}

