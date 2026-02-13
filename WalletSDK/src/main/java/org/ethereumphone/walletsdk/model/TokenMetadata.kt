package org.ethereumphone.walletsdk.model

/**
 * Static metadata for a token known to WalletManager.
 *
 * @property contractAddress ERC-20 contract address.
 * @property chainId        Chain where this metadata applies.
 * @property decimals       Number of decimal places (e.g. 18 for ETH, 6 for USDC).
 * @property name           Human-readable name ("USD Coin").
 * @property symbol         Ticker symbol ("USDC").
 * @property logo           URL (or `android.resource://…` URI) for the token logo, may be null.
 * @property swappable      Whether the token is available for on-chain swaps.
 * @property price          Current USD price per whole token.
 */
data class TokenMetadata(
    val contractAddress: String,
    val chainId: Int,
    val decimals: Int,
    val name: String,
    val symbol: String,
    val logo: String?,
    val swappable: Boolean,
    val price: Double
)
