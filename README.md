## WalletSDK

SDK for interacting with the ethOS system wallet using Account Abstraction (ERC-4337). Supports batched transactions, automatic gas estimation via a bundler, message signing, chain switching, and address utilities.

Also provides access to token data from the **WalletManager** app — balances, metadata, owned-token portfolio, and DEX swap quotes — all through Android content providers.

Minimum Android SDK: 27

### Install

Add JitPack to `settings.gradle` in both `pluginManagement` and `dependencyResolutionManagement`:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add dependencies in your app/module `build.gradle`:

```groovy
// Required by WalletSDK (library declares web3j as compileOnly)
implementation 'org.web3j:core:4.9.4'

// WalletSDK
implementation 'com.github.EthereumPhone:WalletSDK:0.3.0'
```

### Configure bundler RPC URL

WalletSDK requires a bundler RPC URL. Best compatibility is reached with a pimlico URL, which you can just put here for ERC-4337 operations. Example using `local.properties` → `BuildConfig`:

```properties
# local.properties (do not commit secrets)
BUNDLER_RPC_URL=https://your-bundler.example
```

```groovy
// app/build.gradle
android {
  defaultConfig {
    Properties props = new Properties()
    props.load(project.rootProject.file('local.properties').newDataInputStream())
    buildConfigField 'String', 'BUNDLER_RPC_URL', '"' + props.getProperty('BUNDLER_RPC_URL') + '"'
  }
}
```

### Initialize

```kotlin
val wallet = WalletSDK(
    context = context,
    bundlerRPCUrl = BuildConfig.BUNDLER_RPC_URL,
    // optional: override default web3 provider used for reads (eth_call, code, etc.)
    web3jInstance = Web3j.build(HttpService("https://base.llamarpc.com"))
)
```

---

### System wallet

#### Get address

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val address = wallet.getAddress()
}
```

#### Sign message

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val signature = wallet.signMessage(
        message = "Message to sign",
        chainId = 1, // required
        // type = "personal_sign" // optional (default)
    )
}
```

#### Send transaction (single)

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val userOpHashOrError = wallet.sendTransaction(
        to = "0x3a4e6ed8b0f02bfbfaa3c6506af2db939ea5798c",
        value = "1000000000000000000", // wei
        data = "0",
        callGas = null,                // null → auto-estimate via bundler
        chainId = 1,
        rpcEndpoint = "https://rpc.ankr.com/eth"
    )
}
```

#### Send transaction (batch)

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val txs = listOf(
        WalletSDK.TxParams(
            to = "0x...",
            value = "0",
            data = "0x..."
        ),
        WalletSDK.TxParams(
            to = "0x...",
            value = "12345",
            data = "0"
        )
    )
    val userOpHash = wallet.sendTransaction(
        txParamsList = txs,
        callGas = null,
        chainId = 1,
        rpcEndpoint = "https://rpc.ankr.com/eth"
    )
}
```

#### Chain management

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val current = wallet.getChainId()
    val result = wallet.changeChain(
        chainId = 8453,
        rpcEndpoint = "https://base.llamarpc.com",
        mBundlerRPCUrl = "https://your-bundler.for-base"
    )
}
```

#### Other utilities

- `isWalletConnected(): Boolean`
- `switchAccount(index: Int): String`
- `getNonce(senderAddress: String, rpcEndpoint: String?): BigInteger`
- `getPrecomputedAddress(pubKeyX: BigInteger, pubKeyY: BigInteger, salt: BigInteger = BigInteger.ZERO): String`

Notes
- Works on ethOS devices with the system wallet service available. Construction will throw `NoSysWalletException` if the system service is unavailable.
- `sendTransaction` returns a user operation hash on success, or the string `decline` if the user rejected the request.

---

### WalletManager token data

All token-data methods are called directly on the `WalletSDK` instance.

```kotlin
// Check if WalletManager's providers are reachable
if (!wallet.isWalletManagerAvailable()) {
    Log.w("WM", "WalletManager not installed or providers unavailable")
}
```

#### Owned tokens (the all-in-one view)

The most common use case — get every token the user holds, with display-ready balances and prices:

```kotlin
val tokens = wallet.getAllOwnedTokens()
tokens.forEach { t ->
    Log.d("Portfolio",
        "${t.symbol} on chain ${t.chainId}: ${t.balance} (~$${t.totalValue})")
}

// Filter to a single chain
val baseTokens = wallet.getOwnedTokensByChain(chainId = 8453)

// Single token lookup
val usdc = wallet.getOwnedToken(chainId = 1, contractAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
```

`OwnedToken` fields:

| Field | Type | Description |
|---|---|---|
| `contractAddress` | `String` | ERC-20 contract address |
| `chainId` | `Int` | Chain this balance is on |
| `decimals` | `Int` | Token decimals |
| `name` | `String` | Human-readable name |
| `symbol` | `String` | Ticker symbol |
| `logo` | `String?` | Logo URL (or `android.resource://…` URI) |
| `swappable` | `Boolean` | Whether the token can be swapped |
| `balance` | `BigDecimal` | Decimal-adjusted balance (display-ready) |
| `price` | `Double` | Current USD price per token |
| `chains` | `List<Int>` | All chains where this token has balance > 0 |
| `totalValue` | `Double` | Computed: `balance * price` |

#### Token balances (raw)

```kotlin
// Single token on a chain
val bal = wallet.getTokenBalance(chainId = 1, contractAddress = "0xA0b8...")

// All tokens on a chain
val chainBals = wallet.getTokenBalancesByChain(chainId = 8453)

// Every token with a positive balance
val positive = wallet.getPositiveBalances()
```

#### Token metadata

```kotlin
// Single token
val meta = wallet.getTokenMetadata(chainId = 1, contractAddress = "0xA0b8...")
meta?.let { println("${it.name} (${it.symbol}) — $${it.price}") }

// All known tokens on a chain
val allMeta = wallet.getTokenMetadataByChain(chainId = 1)
```

#### Swap quotes

Get a DEX swap quote (backed by 0x API). Amounts are in human-readable form.

```kotlin
val quote = wallet.getSwapQuote(
    sellToken    = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", // USDC
    buyToken     = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", // WETH
    sellAmount   = "100",      // 100 USDC
    chainId      = 1,
    sellDecimals = 6,
    buyDecimals  = 18,
    sellSymbol   = "USDC",     // optional, helps ETH detection
    buySymbol    = "WETH"      // optional
)

if (quote != null && quote.isSuccess) {
    println("Buy amount: ${quote.buyAmount} WETH")
    println("Price: ${quote.price}")
    println("Min buy (slippage-protected): ${quote.minBuyAmount}")
    println("Gas: ${quote.gas}")
    println("Allowance target: ${quote.allowanceTarget}")
} else {
    println("Quote failed: ${quote?.error}")
}
```

`SwapQuote` fields:

| Field | Type | Description |
|---|---|---|
| `sellToken` | `String` | Token address sold |
| `buyToken` | `String` | Token address bought |
| `sellAmount` | `String` | Amount sold (human-readable) |
| `buyAmount` | `String` | Amount received (human-readable) |
| `minBuyAmount` | `String` | Minimum after slippage |
| `price` | `String` | Exchange rate |
| `guaranteedPrice` | `String` | Worst-case price |
| `estimatedPriceImpact` | `String` | Price impact % |
| `liquidityAvailable` | `Boolean` | Whether liquidity exists |
| `gas` | `String` | Estimated gas units |
| `gasPrice` | `String` | Gas price |
| `totalNetworkFee` | `String` | Network fee in sell-token value |
| `allowanceTarget` | `String` | Address to approve for spending |
| `chainId` | `Int` | Chain ID |
| `error` | `String` | Error message (empty on success) |
| `isSuccess` | `Boolean` | Computed: `error.isEmpty()` |

Supported chains for swap quotes: Ethereum (1), Optimism (10), Polygon (137), Arbitrum (42161), Base (8453).

#### Example: read portfolio then swap

```kotlin
val wallet = WalletSDK(context, bundlerRPCUrl = "...")

// 1. Pick a token the user owns
val myUSDC = wallet.getOwnedToken(chainId = 1, contractAddress = USDC_ADDRESS)!!

// 2. Get a swap quote
val quote = wallet.getSwapQuote(
    sellToken = USDC_ADDRESS, buyToken = WETH_ADDRESS,
    sellAmount = myUSDC.balance.toPlainString(),
    chainId = 1, sellDecimals = 6, buyDecimals = 18
)!!

// 3. Build approve + swap transactions and send as a batch
val txs = listOf(
    WalletSDK.TxParams(to = USDC_ADDRESS, value = "0", data = approveCalldata),
    WalletSDK.TxParams(to = swapTarget,   value = "0", data = swapCalldata)
)
val result = wallet.sendTransaction(txParamsList = txs, callGas = null, chainId = 1)
```
