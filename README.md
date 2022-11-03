# WalletSDK

This is an SDK to use the system-level wallet on ethOS.

### Initialize SDK

```kotlin
// You just need to supply a context
val wallet = WalletSDK(context)
```

### Sign message

```kotlin
// How to sign Message
wallet.signMessage(
    message = "Message to sign"
).whenComplete { s, throwable ->
    // Returns signed message
    println(s)
}
```

### Send Transaction

```kotlin
// How to send send Transactions
test.sendTransaction(
    to = "0x3a4e6ed8b0f02bfbfaa3c6506af2db939ea5798c", // mhaas.eth
    value = "1000000000000000000", // 1 eth in wei
    data = ""
).whenComplete {s, throwable ->
    // Returns tx-id
    println(s)
}
```