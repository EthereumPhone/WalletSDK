package org.ethereumphone.walletsdk.model

import java.math.BigDecimal

/**
 * Raw token balance as stored by WalletManager.
 *
 * @property contractAddress ERC-20 contract address (or native-token sentinel).
 * @property chainId        Blockchain network ID (1 = Ethereum, 8453 = Base, …).
 * @property balance        Raw balance in the token's smallest unit (wei-equivalent).
 */
data class TokenBalance(
    val contractAddress: String,
    val chainId: Int,
    val balance: BigDecimal
)
