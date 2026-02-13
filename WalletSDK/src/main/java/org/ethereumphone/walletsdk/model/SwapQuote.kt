package org.ethereumphone.walletsdk.model

/**
 * A DEX swap quote obtained from WalletManager's 0x-backed provider.
 *
 * Check [isSuccess] before reading price fields — if `false`, only [error] is meaningful.
 */
data class SwapQuote(
    val sellToken: String,
    val buyToken: String,
    val sellAmount: String,
    val buyAmount: String,
    val minBuyAmount: String,
    val price: String,
    val guaranteedPrice: String,
    val estimatedPriceImpact: String,
    val liquidityAvailable: Boolean,
    val gas: String,
    val gasPrice: String,
    val totalNetworkFee: String,
    val allowanceTarget: String,
    val chainId: Int,
    val error: String
) {
    /** `true` when the quote was fetched successfully. */
    val isSuccess: Boolean get() = error.isEmpty()
}
