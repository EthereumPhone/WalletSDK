# WalletSDK Transaction Guide

This guide explains how the WalletManager Android app performs transactions using the **WalletSDK** library. The SDK abstracts away the complexity of ERC-4337 (Account Abstraction) and UserOperations, allowing developers to send transactions through a smart wallet.

## Table of Contents

1. [Overview](#overview)
2. [Dependencies](#dependencies)
3. [WalletSDK Initialization](#walletsdk-initialization)
4. [Transaction Types](#transaction-types)
   - [Native ETH Transfers](#native-eth-transfers)
   - [ERC20 Token Transfers](#erc20-token-transfers)
   - [Batched Transactions (Swaps)](#batched-transactions-swaps)
5. [Gas Estimation](#gas-estimation)
6. [Chain Switching](#chain-switching)
7. [Key Data Structures](#key-data-structures)
8. [Complete Code Examples](#complete-code-examples)
9. [GasEstimationHelper Implementation](#gasestimationhelper-implementation)

---

## Overview

The WalletSDK is an ERC-4337 compatible SDK that enables:

- **Smart wallet transactions** via UserOperations
- **Multi-chain support** (Ethereum, Optimism, Arbitrum, Polygon, Base)
- **Batched transactions** (e.g., approve + swap in a single UserOp)
- **Gas estimation** via bundler RPC calls

The SDK uses **Pimlico** as the bundler service for submitting UserOperations to the mempool.

---

## Dependencies

Add to your `build.gradle`:

```kotlin
// WalletSDK from GitHub
implementation("com.github.EthereumPhone:WalletSDK:<version>")

// Web3j for Ethereum interactions
implementation("org.web3j:core:4.9.4")

// OkHttp for HTTP requests
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Gson for JSON parsing
implementation("com.google.code.gson:gson:2.13.2")
```

Version reference from `libs.versions.toml`:
```toml
walletsdk = "b50e1fa116"
ethos-wallet = { group = "com.github.EthereumPhone", name = "WalletSDK", version.ref = "walletsdk" }
```

---

## WalletSDK Initialization

### Basic Initialization

```kotlin
import org.ethereumphone.walletsdk.WalletSDK
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

// Initialize Web3j with an RPC endpoint
val web3j = Web3j.build(HttpService("https://eth-mainnet.g.alchemy.com/v2/YOUR_API_KEY"))

// Initialize WalletSDK
val walletSDK = WalletSDK(
    context = applicationContext,
    web3jInstance = web3j,
    bundlerRPCUrl = "https://api.pimlico.io/v2/1/rpc?apikey=YOUR_PIMLICO_KEY"
)
```

### Bundler URL Pattern

The app uses Pimlico as the bundler service:

```kotlin
fun chainIdToBundler(chainId: Int): String {
    return "https://api.pimlico.io/v2/$chainId/rpc?apikey=${BuildConfig.BUNDLER_API}"
}
```

### RPC URL Pattern

```kotlin
fun chainIdToRPC(chainId: Int): String {
    val networkName = chainIdToName(chainId)
    return "https://${networkName}.g.alchemy.com/v2/${YOUR_ALCHEMY_KEY}"
}

fun chainIdToName(chainId: Int): String = when(chainId) {
    1 -> "eth-mainnet"
    10 -> "opt-mainnet"
    42161 -> "arb-mainnet"
    137 -> "polygon-mainnet"
    8453 -> "base-mainnet"
    11155111 -> "eth-sepolia"
    else -> ""
}
```

---

## Transaction Types

### Native ETH Transfers

Send native ETH using `sendTransaction()`:

```kotlin
suspend fun transferEth(toAddress: String, amountInWei: String, chainId: Int): String {
    val walletSDK = WalletSDK(
        context = context,
        web3jInstance = Web3j.build(HttpService(chainIdToRPC(chainId))),
        bundlerRPCUrl = chainIdToBundler(chainId)
    )
    
    return walletSDK.sendTransaction(
        to = toAddress,
        value = amountInWei,  // Amount in WEI as a string
        data = "",            // Empty data for simple transfers
        callGas = null,       // Let gasProvider estimate
        chainId = chainId,
        gasProvider = ::gasProvider
    )
}
```

### ERC20 Token Transfers

For ERC20 transfers, encode the `transfer()` function call:

```kotlin
suspend fun sendErc20Token(
    toAddress: String,
    erc20ContractAddress: String,
    amount: Double,
    decimals: Int,
    chainId: Int
): String {
    val web3j = Web3j.build(HttpService(chainIdToRPC(chainId)))
    val walletSDK = WalletSDK(
        context = context,
        web3jInstance = web3j,
        bundlerRPCUrl = chainIdToBundler(chainId)
    )
    
    // Load the ERC20 contract to encode the transfer function
    val contract = ERC20.load(
        erc20ContractAddress,
        web3j,
        credentials,
        DefaultGasProvider()
    )
    
    // Convert human-readable amount to smallest unit
    val realAmount = BigDecimal(amount.toString())
        .multiply(BigDecimal.TEN.pow(decimals))
    
    // Encode the transfer function call
    val data = contract.transfer(
        toAddress,
        realAmount.toBigInteger()
    ).encodeFunctionCall()
    
    return walletSDK.sendTransaction(
        to = erc20ContractAddress,  // Send to the contract address
        value = "0",                 // No ETH value for ERC20 transfers
        data = data,                 // Encoded transfer function
        callGas = null,
        chainId = chainId,
        gasProvider = ::gasProvider
    )
}
```

### Batched Transactions (Swaps)

For operations requiring multiple transactions (e.g., approve + swap), use `TxParams` list:

```kotlin
suspend fun executeSwap(chainId: Int, quote: SwapQuote): String {
    val walletSDK = getWalletSdkForChain(chainId)
    
    // Build transaction list
    val txList = if (quote.allowanceTransaction != null) {
        // Include approval transaction first
        listOf(
            WalletSDK.TxParams(
                to = quote.allowanceTransaction.to,
                value = "0",
                data = quote.allowanceTransaction.data
            ),
            WalletSDK.TxParams(
                to = quote.transaction.to,
                value = quote.transaction.value,
                data = quote.transaction.data
            )
        )
    } else {
        // Only the swap transaction
        listOf(
            WalletSDK.TxParams(
                to = quote.transaction.to,
                value = quote.transaction.value,
                data = quote.transaction.data
            )
        )
    }
    
    // Send batched transaction
    val txHash = walletSDK.sendTransaction(
        txParamsList = txList,
        callGas = null,
        chainId = chainId,
        gasProvider = { userOp -> gasProvider(chainId, userOp) }
    )
    
    return txHash
}
```

### Building Approval Transactions

```kotlin
private fun buildApprovalTransaction(
    tokenAddress: String,
    spenderAddress: String
): TxParams {
    // Max uint256 for unlimited approval
    val approvalAmount = BigInteger("2").pow(256).subtract(BigInteger.ONE)
    
    val function = Function(
        "approve",
        listOf(Address(spenderAddress), Uint256(approvalAmount)),
        emptyList<TypeReference<*>>()
    )
    val encodedFunction = FunctionEncoder.encode(function)
    
    return TxParams(
        to = tokenAddress,
        data = encodedFunction,
        value = "0"
    )
}
```

---

## Gas Estimation

The app uses a custom `GasEstimationHelper` to estimate gas for UserOperations by calling the bundler's `eth_estimateUserOperationGas` RPC method.

### Gas Provider Function

Each transaction method accepts a `gasProvider` lambda:

```kotlin
suspend fun gasProvider(userOp: WalletSDK.UserOperation): WalletSDK.GasEstimation {
    val rpcUrl = chainIdToRPC(currentChainId)
    return GasEstimationHelper.estimateGas(userOp, rpcUrl)
}
```

### GasEstimation Return Type

```kotlin
WalletSDK.GasEstimation(
    preVerificationGas = BigInteger,     // Gas for bundler processing
    verificationGasLimit = BigInteger,   // Gas for signature verification
    callGasLimit = BigInteger            // Gas for actual call execution
)
```

### Important Note on Gas Doubling

**The WalletSDK internally doubles `preVerificationGas` and `verificationGasLimit`**, so the `GasEstimationHelper` returns half of the desired final values for these fields. The `callGasLimit` is NOT doubled.

---

## Chain Switching

Switch chains dynamically using `changeChain()`:

```kotlin
suspend fun switchToChain(chainId: Int): Boolean {
    val rpcUrl = chainIdToRPC(chainId)
    val bundlerUrl = chainIdToBundler(chainId)
    
    val result = walletSDK.changeChain(chainId, rpcUrl, bundlerUrl)
    
    return result != WalletSDK.DECLINE
}
```

Check current chain:

```kotlin
val currentChainId = walletSDK.getChainId()
if (currentChainId != targetChainId) {
    walletSDK.changeChain(targetChainId, rpcUrl, bundlerUrl)
}
```

---

## Key Data Structures

### UserOperation

The `WalletSDK.UserOperation` contains all fields for an ERC-4337 UserOperation:

```kotlin
WalletSDK.UserOperation(
    sender: String,              // Smart wallet address
    nonce: BigInteger,           // Account nonce
    initCode: String,            // Empty for existing accounts
    callData: String,            // Encoded call(s)
    callGasLimit: BigInteger,    // Gas for call execution
    verificationGasLimit: BigInteger,
    preVerificationGas: BigInteger,
    maxFeePerGas: BigInteger,
    maxPriorityFeePerGas: BigInteger,
    paymasterAndData: String,    // Empty if no paymaster
    signature: String            // User signature
)
```

### TxParams

For batched transactions:

```kotlin
WalletSDK.TxParams(
    to: String,     // Target contract address
    value: String,  // ETH value in WEI
    data: String    // Encoded function call data
)
```

### GasEstimation

```kotlin
WalletSDK.GasEstimation(
    preVerificationGas: BigInteger,
    verificationGasLimit: BigInteger,
    callGasLimit: BigInteger
)
```

---

## Complete Code Examples

### Example 1: Simple ETH Transfer

```kotlin
class EthTransferService(private val context: Context) {
    
    suspend fun sendEth(
        toAddress: String,
        amountEth: String,
        chainId: Int
    ): String {
        // Convert ETH to WEI
        val amountWei = BigDecimal(amountEth)
            .movePointRight(18)
            .setScale(0, RoundingMode.DOWN)
            .toPlainString()
        
        val walletSDK = WalletSDK(
            context = context,
            web3jInstance = Web3j.build(HttpService(chainIdToRPC(chainId))),
            bundlerRPCUrl = chainIdToBundler(chainId)
        )
        
        return walletSDK.sendTransaction(
            to = toAddress,
            value = amountWei,
            data = "",
            callGas = null,
            chainId = chainId,
            gasProvider = { userOp -> 
                GasEstimationHelper.estimateGas(userOp, chainIdToRPC(chainId))
            }
        )
    }
}
```

### Example 2: WalletConnect Transaction Handler

```kotlin
suspend fun handleWalletConnectTransaction(
    tx: TransactionParams,
    chainId: Int,
    rpcUrl: String,
    bundlerUrl: String
): String {
    // Parse value from hex
    val valueInWei = tx.value?.let { hexValue ->
        if (hexValue.startsWith("0x")) {
            BigInteger(hexValue.removePrefix("0x"), 16).toString()
        } else {
            hexValue
        }
    } ?: "0"
    
    // Parse gas limit if provided
    val callGas = tx.gas?.let { gasHex ->
        BigInteger(gasHex.removePrefix("0x"), 16)
    }
    
    // Create gas provider
    val gasProvider: suspend (WalletSDK.UserOperation) -> WalletSDK.GasEstimation = { userOp ->
        GasEstimationHelper.estimateGas(userOp, rpcUrl)
    }
    
    // Send transaction
    val userOpHash = sdk.sendTransaction(
        to = tx.to,
        value = valueInWei,
        data = tx.data ?: "0x",
        callGas = callGas,
        chainId = chainId,
        gasProvider = gasProvider
    )
    
    return userOpHash
}
```

### Example 3: Getting Transaction Hash from UserOp Hash

After submitting a UserOperation, poll for the transaction hash:

```kotlin
suspend fun getTxHashForUserOp(
    bundlerRPC: String,
    userOpHash: String,
    chainId: Int
): String {
    val client = OkHttpClient()
    var attempts = 0
    
    while (attempts < 10) {
        val requestBody = """
        {
            "jsonrpc": "2.0",
            "method": "pimlico_getUserOperationStatus",
            "params": ["$userOpHash"],
            "id": 1
        }
        """.trimIndent()
        
        val request = Request.Builder()
            .url(bundlerRPC)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            val jsonResponse = response.body?.string()
            val result = JsonParser.parseString(jsonResponse)
                .asJsonObject
                .get("result")
                ?.asJsonObject
            
            val status = result?.get("status")?.asString
            if (status == "included" || status == "confirmed") {
                return result?.get("transactionHash")?.asString ?: userOpHash
            }
        }
        
        attempts++
        delay(2000) // Wait 2 seconds between polls
    }
    
    return userOpHash // Fallback to userOpHash
}
```

---

## GasEstimationHelper Implementation

Below is the complete `GasEstimationHelper` implementation used by this app:

```kotlin
package com.core.data.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ethereumphone.walletsdk.WalletSDK
import java.math.BigInteger

/**
 * Helper class for estimating gas for UserOperations.
 * Uses exact values from API but always sets verificationGasLimit to 800k.
 * 
 * NOTE: WalletSDK internally doubles preVerificationGas and verificationGasLimit,
 * so we return half of the desired final values for these fields.
 */
object GasEstimationHelper {
    
    // EntryPoint v0.6 address
    private const val ENTRY_POINT = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789"
    
    // Default fallback values if API fails
    private val DEFAULT_PRE_VERIFICATION_GAS = BigInteger.valueOf(70000) // 70k
    private val DEFAULT_CALL_GAS_LIMIT = BigInteger.valueOf(200000) // 200k
    
    /**
     * Estimates gas for a UserOperation by calling eth_estimateUserOperationGas RPC.
     * Uses exact API values except for verificationGasLimit which is hardcoded to result in 800k after doubling.
     * 
     * Note: WalletSDK doubles preVerificationGas and verificationGasLimit internally,
     * so we return 400k for verificationGasLimit to achieve a final value of 800k.
     */
    suspend fun estimateGas(
        userOp: WalletSDK.UserOperation,
        rpcUrl: String
    ): WalletSDK.GasEstimation = withContext(Dispatchers.IO) {
        
        // Create UserOperation JSON object with proper field names and hex encoding
        val userOpJson = JsonObject().apply {
            addProperty("sender", userOp.sender)
            addProperty("nonce", "0x" + userOp.nonce.toString(16))
            addProperty("initCode", userOp.initCode.ifEmpty { "0x" })
            addProperty("callData", userOp.callData.ifEmpty { "0x" })
            addProperty("callGasLimit", "0x" + userOp.callGasLimit.toString(16))
            addProperty("verificationGasLimit", "0x" + userOp.verificationGasLimit.toString(16))
            addProperty("preVerificationGas", "0x" + userOp.preVerificationGas.toString(16))
            addProperty("maxFeePerGas", "0x" + userOp.maxFeePerGas.toString(16))
            addProperty("maxPriorityFeePerGas", "0x" + userOp.maxPriorityFeePerGas.toString(16))
            addProperty("paymasterAndData", userOp.paymasterAndData.ifEmpty { "0x" })
            addProperty("signature", userOp.signature)
        }
        
        // Create JSON-RPC request
        val requestJson = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "eth_estimateUserOperationGas")
            add("params", JsonArray().apply {
                add(userOpJson)
                add(ENTRY_POINT)
            })
            addProperty("id", 1)
        }
        
        // Initialize with defaults
        var preVerificationGas = DEFAULT_PRE_VERIFICATION_GAS
        var callGasLimit = DEFAULT_CALL_GAS_LIMIT
        
        try {
            // Make HTTP request to RPC endpoint
            val client = OkHttpClient()
            val contentType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(rpcUrl)
                .post(requestJson.toString().toRequestBody(contentType))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                
                if (!responseBody.isNullOrEmpty()) {
                    try {
                        // Parse response
                        val responseJson = JsonParser.parseString(responseBody).asJsonObject
                        
                        if (responseJson.has("error")) {
                            // Log error but use default values
                            val error = responseJson.getAsJsonObject("error")
                            val errorMessage = error.get("message")?.asString ?: "Unknown error"
                            println("GasEstimationHelper: API error: $errorMessage, using default values")
                        } else if (responseJson.has("result")) {
                            val result = responseJson.getAsJsonObject("result")
                            
                            // Extract exact values from API (no buffer applied)
                            result.get("preVerificationGas")?.asString?.let { 
                                try {
                                    preVerificationGas = BigInteger(it.removePrefix("0x"), 16)
                                    println("GasEstimationHelper: Using exact preVerificationGas from API: $preVerificationGas")
                                } catch (e: Exception) {
                                    println("GasEstimationHelper: Failed to parse preVerificationGas: ${e.message}")
                                }
                            }
                            
                            result.get("callGasLimit")?.asString?.let {
                                try {
                                    callGasLimit = BigInteger(it.removePrefix("0x"), 16)
                                    println("GasEstimationHelper: Using exact callGasLimit from API: $callGasLimit")
                                } catch (e: Exception) {
                                    println("GasEstimationHelper: Failed to parse callGasLimit: ${e.message}")
                                }
                            }
                            
                            // Note: We ignore verificationGasLimit from API and use fixed value
                            result.get("verificationGasLimit")?.asString?.let {
                                println("GasEstimationHelper: API returned verificationGasLimit but using fixed 800k instead")
                            }
                        }
                    } catch (e: Exception) {
                        println("GasEstimationHelper: Failed to parse response: ${e.message}")
                    }
                }
            } else {
                println("GasEstimationHelper: HTTP request failed with code ${response.code}, using defaults")
            }
        } catch (e: Exception) {
            println("GasEstimationHelper: Exception during gas estimation: ${e.message}, using defaults")
        }
        
        // WalletSDK internally doubles preVerificationGas and verificationGasLimit
        // So we need to return half of what we want the final values to be
        
        // We want final verificationGasLimit to be 800k, so return 400k
        val verificationGasLimitValue = BigInteger.valueOf(400000) // Will be doubled to 800k by WalletSDK
        
        // PreVerificationGas will also be doubled, so keep it as-is from API (it will be doubled)
        // The API already returns the base value which will be doubled
        
        println("GasEstimationHelper: Final values (will be doubled by WalletSDK):")
        println("  - preVerificationGas: $preVerificationGas (will become ${preVerificationGas.multiply(BigInteger.valueOf(2))})")
        println("  - verificationGasLimit: $verificationGasLimitValue (will become ${verificationGasLimitValue.multiply(BigInteger.valueOf(2))}) - HARDCODED to become 800k")
        println("  - callGasLimit: $callGasLimit (will NOT be doubled)")
        
        val gasEstimation = WalletSDK.GasEstimation(
            preVerificationGas = preVerificationGas,
            verificationGasLimit = verificationGasLimitValue, // 400k -> will be doubled to 800k
            callGasLimit = callGasLimit
        )
        
        // Log what we're actually returning
        println("GasEstimationHelper: Returning GasEstimation with verificationGasLimit = ${gasEstimation.verificationGasLimit} (will be doubled to ${gasEstimation.verificationGasLimit.multiply(BigInteger.valueOf(2))})")
        
        gasEstimation
    }
}
```

---

## Error Handling

Common error responses to handle:

```kotlin
val result = walletSDK.sendTransaction(...)

when {
    result.startsWith("0x") -> {
        // Success - this is the transaction/userOp hash
    }
    result.equals("decline", ignoreCase = true) -> {
        // User declined the transaction
    }
    result.contains("AA21") -> {
        // Not enough gas/funds for the transaction
    }
    result == "error" -> {
        // Generic error occurred
    }
}
```

---

## Supported Chains

| Chain | Chain ID | Network Name |
|-------|----------|--------------|
| Ethereum Mainnet | 1 | eth-mainnet |
| Optimism | 10 | opt-mainnet |
| Polygon | 137 | polygon-mainnet |
| Arbitrum | 42161 | arb-mainnet |
| Base | 8453 | base-mainnet |
| Sepolia (Testnet) | 11155111 | eth-sepolia |

---

## Summary

1. **Initialize WalletSDK** with context, Web3j instance, and bundler URL
2. **Use `sendTransaction()`** for single transactions with `to`, `value`, `data`, and `gasProvider`
3. **Use `sendTransaction()` with `txParamsList`** for batched transactions
4. **Provide a `gasProvider`** lambda that returns `GasEstimation` using `GasEstimationHelper`
5. **Handle chain switching** with `changeChain()` before transactions
6. **Poll for transaction hash** using `pimlico_getUserOperationStatus` after submission

The WalletSDK abstracts ERC-4337 complexity, making it easy to send smart wallet transactions across multiple chains.

