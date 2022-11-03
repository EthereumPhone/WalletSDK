package org.ethereumphone.walletsdk.model

import dev.pinkroom.walletconnectkit.WalletConnectKitConfig
import android.content.Context

class Config(
    context: Context,
    bridgeUrl: String,
    appUrl: String,
    appName: String,
    appDescription: String
) {
    var walletConnectKitConfig: WalletConnectKitConfig = WalletConnectKitConfig(
        context = context,
        bridgeUrl = bridgeUrl,
        appUrl = appUrl,
        appName = appName,
        appDescription = appDescription
    )
}