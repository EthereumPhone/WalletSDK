package org.ethereumphone.walletsdk.model

import java.math.BigDecimal

/**
 * Display-ready view of a token the user actually owns (balance > 0).
 *
 * Combines metadata and balance data; the [balance] is already decimal-adjusted
 * (e.g. `1.5` means 1.5 USDC, not 1500000).
 *
 * @property contractAddress ERC-20 contract address.
 * @property chainId        Chain ID this balance lives on.
 * @property decimals       Token decimals.
 * @property name           Human-readable name.
 * @property symbol         Ticker symbol.
 * @property logo           Logo URL or null.
 * @property swappable      Whether the token can be swapped.
 * @property balance        Decimal-adjusted balance (display-ready).
 * @property price          Current USD price per whole token.
 * @property chains         All chain IDs where this token has a positive balance.
 */
data class OwnedToken(
    val contractAddress: String,
    val chainId: Int,
    val decimals: Int,
    val name: String,
    val symbol: String,
    val logo: String?,
    val swappable: Boolean,
    val balance: BigDecimal,
    val price: Double,
    val chains: List<Int>
) {
    /** Total USD value of this holding. */
    val totalValue: Double get() = balance.toDouble() * price
}
