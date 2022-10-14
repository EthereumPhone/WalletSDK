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

    println(test.createSession())

}

