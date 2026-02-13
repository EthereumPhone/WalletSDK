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
            web3jInstance = Web3j.build(HttpService("https://base.llamarpc.com"))
        )

        CoroutineScope(Dispatchers.IO).launch {
            val address = wallet.getAddress()
            Log.d("address", address)

            val signedMessage = wallet.signMessage(
                message = "Hello World",
                chainId = 8453
            )
            Log.d("sign", signedMessage)

            // List every token the user owns
            val owned = wallet.getAllOwnedTokens()
            owned.forEach { token ->
                Log.d("OwnedToken",
                    "${token.symbol} on chain ${token.chainId}: " +
                    "${token.balance} (~$${token.totalValue})"
                )
            }

            // Get a swap quote: 100 USDC → WETH on Ethereum
            val quote = wallet.getSwapQuote(
                sellToken    = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                buyToken     = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2",
                sellAmount   = "100",
                chainId      = 1,
                sellDecimals = 6,
                buyDecimals  = 18,
                sellSymbol   = "USDC",
                buySymbol    = "WETH"
            )
            if (quote != null && quote.isSuccess) {
                Log.d("SwapQuote", "100 USDC → ${quote.buyAmount} WETH (price: ${quote.price})")
            }
        }
    }
}
