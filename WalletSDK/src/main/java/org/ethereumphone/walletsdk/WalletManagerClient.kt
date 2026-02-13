package org.ethereumphone.walletsdk

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import org.ethereumphone.walletsdk.model.OwnedToken
import org.ethereumphone.walletsdk.model.SwapQuote
import org.ethereumphone.walletsdk.model.TokenBalance
import org.ethereumphone.walletsdk.model.TokenMetadata
import java.math.BigDecimal

/**
 * Client for reading token data exposed by the WalletManager app via content providers.
 *
 * Provides access to:
 * - **Token balances** – raw on-chain balances.
 * - **Token metadata** – name, symbol, decimals, logo, price, swappable flag.
 * - **Owned tokens** – combined, display-ready view of tokens the user holds.
 * - **Swap quotes** – DEX quotes powered by 0x.
 *
 * Usage:
 * ```kotlin
 * val client = WalletManagerClient(context)
 *
 * // All tokens the user owns, across every chain
 * val tokens = client.getAllOwnedTokens()
 * tokens.forEach { println("${it.symbol}: ${it.balance} (~$${it.totalValue})") }
 *
 * // Swap quote
 * val quote = client.getSwapQuote(
 *     sellToken  = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
 *     buyToken   = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2",
 *     sellAmount = "100",
 *     chainId    = 1,
 *     sellDecimals = 6,
 *     buyDecimals  = 18
 * )
 * ```
 *
 * @param context              Android context (only the [ContentResolver] is retained).
 * @param ownedTokenAuthority  Authority of the OwnedToken provider. Defaults to the
 *                             production WalletManager package. Override if you use a
 *                             custom build / applicationId.
 */
class WalletManagerClient(
    context: Context,
    private val ownedTokenAuthority: String = DEFAULT_OWNED_TOKEN_AUTHORITY
) {
    private val resolver: ContentResolver = context.contentResolver

    // ──────────────────────────────────────────────
    //  Token Balances
    // ──────────────────────────────────────────────

    /**
     * Get the raw balance of a single token on a specific chain.
     *
     * @return The balance, or `null` if WalletManager has no record for this token.
     */
    fun getTokenBalance(chainId: Int, contractAddress: String): TokenBalance? {
        val uri = BALANCE_URI.buildUpon()
            .appendPath("balance")
            .appendPath(chainId.toString())
            .appendPath(contractAddress)
            .build()
        return resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.readTokenBalance() else null
        }
    }

    /**
     * Get every token balance WalletManager knows about on [chainId].
     */
    fun getTokenBalancesByChain(chainId: Int): List<TokenBalance> {
        val uri = BALANCE_URI.buildUpon()
            .appendPath("balances")
            .appendPath(chainId.toString())
            .build()
        return resolver.queryList(uri) { readTokenBalance() }
    }

    /**
     * Get all token balances that are greater than zero, across all chains.
     */
    fun getPositiveBalances(): List<TokenBalance> {
        val uri = BALANCE_URI.buildUpon()
            .appendPath("balances")
            .appendPath("positive")
            .build()
        return resolver.queryList(uri) { readTokenBalance() }
    }

    // ──────────────────────────────────────────────
    //  Token Metadata
    // ──────────────────────────────────────────────

    /**
     * Get metadata for a single token on a specific chain.
     *
     * @return Metadata including name, symbol, decimals, price, logo — or `null`.
     */
    fun getTokenMetadata(chainId: Int, contractAddress: String): TokenMetadata? {
        val uri = METADATA_URI.buildUpon()
            .appendPath("token")
            .appendPath(chainId.toString())
            .appendPath(contractAddress)
            .build()
        return resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.readTokenMetadata() else null
        }
    }

    /**
     * Get metadata for every token WalletManager knows about on [chainId].
     */
    fun getTokenMetadataByChain(chainId: Int): List<TokenMetadata> {
        val uri = METADATA_URI.buildUpon()
            .appendPath("tokens")
            .appendPath(chainId.toString())
            .build()
        return resolver.queryList(uri) { readTokenMetadata() }
    }

    // ──────────────────────────────────────────────
    //  Owned Tokens (combined view)
    // ──────────────────────────────────────────────

    /**
     * Get a single owned token by chain and contract address.
     * Only returns a result if the user's balance is positive.
     */
    fun getOwnedToken(chainId: Int, contractAddress: String): OwnedToken? {
        val uri = ownedTokenUri.buildUpon()
            .appendPath("ownedToken")
            .appendPath(chainId.toString())
            .appendPath(contractAddress)
            .build()
        return resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.readOwnedToken() else null
        }
    }

    /**
     * Get all owned tokens on a specific chain (balance > 0).
     */
    fun getOwnedTokensByChain(chainId: Int): List<OwnedToken> {
        val uri = ownedTokenUri.buildUpon()
            .appendPath("ownedTokens")
            .appendPath(chainId.toString())
            .build()
        return resolver.queryList(uri) { readOwnedToken() }
    }

    /**
     * Get every token the user owns across all chains (balance > 0).
     * Each entry is display-ready with decimal-adjusted balance and USD price.
     */
    fun getAllOwnedTokens(): List<OwnedToken> {
        val uri = ownedTokenUri.buildUpon()
            .appendPath("ownedTokens")
            .build()
        return resolver.queryList(uri) { readOwnedToken() }
    }

    // ──────────────────────────────────────────────
    //  Swap Quotes
    // ──────────────────────────────────────────────

    /**
     * Request a DEX swap quote from WalletManager (backed by the 0x API).
     *
     * Amounts are in human-readable form (e.g. `"100"` = 100 USDC, not `100000000`).
     *
     * @param sellToken    Contract address of the token to sell.
     * @param buyToken     Contract address of the token to buy.
     * @param sellAmount   Amount to sell in human-readable decimals.
     * @param chainId      Chain ID (1, 10, 137, 42161, 8453).
     * @param sellDecimals Decimals of the sell token.
     * @param buyDecimals  Decimals of the buy token.
     * @param sellSymbol   Optional symbol (helps with ETH detection).
     * @param buySymbol    Optional symbol (helps with ETH detection).
     * @return A [SwapQuote], or `null` if the provider is unavailable. Check [SwapQuote.isSuccess].
     */
    fun getSwapQuote(
        sellToken: String,
        buyToken: String,
        sellAmount: String,
        chainId: Int,
        sellDecimals: Int,
        buyDecimals: Int,
        sellSymbol: String = "",
        buySymbol: String = ""
    ): SwapQuote? {
        val uri = SWAP_QUOTE_URI.buildUpon()
            .appendPath("quote")
            .appendQueryParameter("sellToken", sellToken)
            .appendQueryParameter("buyToken", buyToken)
            .appendQueryParameter("sellAmount", sellAmount)
            .appendQueryParameter("chainId", chainId.toString())
            .appendQueryParameter("sellDecimals", sellDecimals.toString())
            .appendQueryParameter("buyDecimals", buyDecimals.toString())
            .appendQueryParameter("sellSymbol", sellSymbol)
            .appendQueryParameter("buySymbol", buySymbol)
            .build()
        return resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.readSwapQuote() else null
        }
    }

    // ──────────────────────────────────────────────
    //  Availability check
    // ──────────────────────────────────────────────

    /**
     * Returns `true` if the WalletManager owned-token provider is reachable.
     * A quick way to check whether WalletManager is installed and its providers are available.
     */
    fun isAvailable(): Boolean {
        val uri = ownedTokenUri.buildUpon().appendPath("ownedTokens").build()
        return try {
            resolver.query(uri, null, null, null, null)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────────
    //  Internals
    // ──────────────────────────────────────────────

    private val ownedTokenUri: Uri = Uri.parse("content://$ownedTokenAuthority")

    /** Read a [TokenBalance] from the current cursor row. */
    private fun Cursor.readTokenBalance() = TokenBalance(
        contractAddress = getString(getColumnIndexOrThrow(COL_CONTRACT_ADDRESS)),
        chainId = getInt(getColumnIndexOrThrow(COL_CHAIN_ID)),
        balance = getString(getColumnIndexOrThrow(COL_TOKEN_BALANCE)).toBigDecimal()
    )

    /** Read a [TokenMetadata] from the current cursor row. */
    private fun Cursor.readTokenMetadata() = TokenMetadata(
        contractAddress = getString(getColumnIndexOrThrow(COL_CONTRACT_ADDRESS)),
        chainId = getInt(getColumnIndexOrThrow(COL_CHAIN_ID)),
        decimals = getInt(getColumnIndexOrThrow(COL_DECIMALS)),
        name = getString(getColumnIndexOrThrow(COL_NAME)),
        symbol = getString(getColumnIndexOrThrow(COL_SYMBOL)),
        logo = getString(getColumnIndexOrThrow(COL_LOGO)),
        swappable = getInt(getColumnIndexOrThrow(COL_SWAPPABLE)) == 1,
        price = getDouble(getColumnIndexOrThrow(COL_PRICE))
    )

    /** Read an [OwnedToken] from the current cursor row. */
    private fun Cursor.readOwnedToken() = OwnedToken(
        contractAddress = getString(getColumnIndexOrThrow(COL_CONTRACT_ADDRESS)),
        chainId = getInt(getColumnIndexOrThrow(COL_CHAIN_ID)),
        decimals = getInt(getColumnIndexOrThrow(COL_DECIMALS)),
        name = getString(getColumnIndexOrThrow(COL_NAME)),
        symbol = getString(getColumnIndexOrThrow(COL_SYMBOL)),
        logo = getString(getColumnIndexOrThrow(COL_LOGO)),
        swappable = getInt(getColumnIndexOrThrow(COL_SWAPPABLE)) == 1,
        balance = getString(getColumnIndexOrThrow(COL_BALANCE)).toBigDecimal(),
        price = getDouble(getColumnIndexOrThrow(COL_PRICE)),
        chains = getString(getColumnIndexOrThrow(COL_CHAINS))
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    )

    /** Read a [SwapQuote] from the current cursor row. */
    private fun Cursor.readSwapQuote() = SwapQuote(
        sellToken = getString(getColumnIndexOrThrow(COL_SELL_TOKEN)),
        buyToken = getString(getColumnIndexOrThrow(COL_BUY_TOKEN)),
        sellAmount = getString(getColumnIndexOrThrow(COL_SELL_AMOUNT)),
        buyAmount = getString(getColumnIndexOrThrow(COL_BUY_AMOUNT)),
        minBuyAmount = getString(getColumnIndexOrThrow(COL_MIN_BUY_AMOUNT)),
        price = getString(getColumnIndexOrThrow(COL_PRICE)),
        guaranteedPrice = getString(getColumnIndexOrThrow(COL_GUARANTEED_PRICE)),
        estimatedPriceImpact = getString(getColumnIndexOrThrow(COL_ESTIMATED_PRICE_IMPACT)),
        liquidityAvailable = getInt(getColumnIndexOrThrow(COL_LIQUIDITY_AVAILABLE)) == 1,
        gas = getString(getColumnIndexOrThrow(COL_GAS)),
        gasPrice = getString(getColumnIndexOrThrow(COL_GAS_PRICE)),
        totalNetworkFee = getString(getColumnIndexOrThrow(COL_TOTAL_NETWORK_FEE)),
        allowanceTarget = getString(getColumnIndexOrThrow(COL_ALLOWANCE_TARGET)),
        chainId = getInt(getColumnIndexOrThrow(COL_CHAIN_ID)),
        error = getString(getColumnIndexOrThrow(COL_ERROR))
    )

    /** Query helper that collects every row into a list. */
    private inline fun <T> ContentResolver.queryList(
        uri: Uri,
        crossinline rowMapper: Cursor.() -> T
    ): List<T> {
        return query(uri, null, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.rowMapper())
                }
            }
        } ?: emptyList()
    }

    companion object {
        // Authorities
        private const val BALANCE_AUTHORITY = "com.walletmanager.tokenbalance.provider"
        private const val METADATA_AUTHORITY = "com.walletmanager.tokenmetadata.provider"
        private const val SWAP_QUOTE_AUTHORITY = "com.walletmanager.swapquote.provider"
        private const val DEFAULT_OWNED_TOKEN_AUTHORITY =
            "org.ethereumphone.walletmanager.ownedtokens.provider"

        // Base URIs
        private val BALANCE_URI = Uri.parse("content://$BALANCE_AUTHORITY")
        private val METADATA_URI = Uri.parse("content://$METADATA_AUTHORITY")
        private val SWAP_QUOTE_URI = Uri.parse("content://$SWAP_QUOTE_AUTHORITY")

        // Column names — token balance
        private const val COL_CONTRACT_ADDRESS = "contract_address"
        private const val COL_CHAIN_ID = "chain_id"
        private const val COL_TOKEN_BALANCE = "token_balance"

        // Column names — token metadata
        private const val COL_DECIMALS = "decimals"
        private const val COL_NAME = "name"
        private const val COL_SYMBOL = "symbol"
        private const val COL_LOGO = "logo"
        private const val COL_SWAPPABLE = "swappable"
        private const val COL_PRICE = "price"

        // Column names — owned tokens
        private const val COL_BALANCE = "balance"
        private const val COL_CHAINS = "chains"

        // Column names — swap quote
        private const val COL_SELL_TOKEN = "sell_token"
        private const val COL_BUY_TOKEN = "buy_token"
        private const val COL_SELL_AMOUNT = "sell_amount"
        private const val COL_BUY_AMOUNT = "buy_amount"
        private const val COL_MIN_BUY_AMOUNT = "min_buy_amount"
        private const val COL_GUARANTEED_PRICE = "guaranteed_price"
        private const val COL_ESTIMATED_PRICE_IMPACT = "estimated_price_impact"
        private const val COL_LIQUIDITY_AVAILABLE = "liquidity_available"
        private const val COL_GAS = "gas"
        private const val COL_GAS_PRICE = "gas_price"
        private const val COL_TOTAL_NETWORK_FEE = "total_network_fee"
        private const val COL_ALLOWANCE_TARGET = "allowance_target"
        private const val COL_ERROR = "error"
    }
}
