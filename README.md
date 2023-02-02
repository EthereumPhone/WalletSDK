# WalletSDK

This is an SDK to use the system-level wallet on ethOS.

### How to add the WalletSDK into your app

First, go into your `settings.gradle` file and add the line `maven { url 'https://jitpack.io' }` to the `pluginManagement` and the `dependencyResolutionManagement` section like this:

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

Then go to your module-level `build.gradle` file and add the following line to the `dependencies` section:

```groovy
// Web3j needed for the WalletSDK
implementation 'org.web3j:core:4.8.8-android'
implementation 'com.github.EthereumPhone:WalletSDK:0.0.7'
```

### How to initialize SDK

```kotlin
// You just need to supply a context
val wallet = WalletSDK(context)

// You can also supply your own web3 rpc-url, like this:
val wallet = WalletSDK(
    context = context,
    web3RPC = "YOUR_URL"
)
```

### How to sign a message

```kotlin
// How to sign Message
wallet.signMessage(
    message = "Message to sign"
).whenComplete { s, throwable ->
    // Returns signed message
    if (s == WalletSDK.DECLINE) {
        println("Sign message has been declined")
    } else {
        println(s)
    }
}
```

### How to send a Transaction

```kotlin
// How to send send Transactions
wallet.sendTransaction(
    to = "0x3a4e6ed8b0f02bfbfaa3c6506af2db939ea5798c", // mhaas.eth
    value = "1000000000000000000", // 1 eth in wei
    data = ""
).whenComplete {s, throwable ->
    // Returns tx-id
    if (s == WalletSDK.DECLINE) {
        println("Send transaction has been declined")
    } else {
        println(s)
    }
}
```
