## WalletSDK

SDK for interacting with the ethOS system wallet using Account Abstraction (ERC-4337). Supports batched transactions, automatic gas estimation via a bundler, message signing, chain switching, and address utilities.

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
implementation 'com.github.EthereumPhone:WalletSDK:0.1.0'
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

### Get address

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val address = wallet.getAddress()
}
```

### Sign message

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val signature = wallet.signMessage(
        message = "Message to sign",
        chainId = 1, // required
        // type = "personal_sign" // optional (default)
    )
}
```

### Send transaction (single)

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

### Send transaction (batch)

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

### Chain management

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

### Other utilities

- `isWalletConnected(): Boolean`
- `switchAccount(index: Int): String`
- `getNonce(senderAddress: String, rpcEndpoint: String?): BigInteger`
- `getPrecomputedAddress(pubKeyX: BigInteger, pubKeyY: BigInteger, salt: BigInteger = BigInteger.ZERO): String`

Notes
- Works on ethOS devices with the system wallet service available. Construction will throw `NoSysWalletException` if the system service is unavailable.
- `sendTransaction` returns a user operation hash on success, or the string `decline` if the user rejected the request.
