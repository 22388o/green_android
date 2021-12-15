package com.blockstream.green.data

import android.content.Context
import android.os.Parcelable
import com.blockstream.gdk.GreenWallet
import com.blockstream.gdk.data.Network
import com.blockstream.green.database.Wallet
import com.blockstream.green.utils.isProductionFlavor
import kotlinx.parcelize.Parcelize

@Parcelize
data class OnboardingOptions constructor(
    val isRestoreFlow: Boolean,
    val isWatchOnly: Boolean = false,
    val isSingleSig: Boolean = false,
    val networkType: String? = null,
    val network: Network? = null,
    val walletName: String? = null
) : Parcelable{
    fun createCopyForNetwork(greenWallet: GreenWallet, networkType: String, isElectrum: Boolean): OnboardingOptions {
        val id = when (networkType) {
            Network.GreenMainnet -> {
                if (isElectrum) Network.ElectrumMainnet else Network.GreenMainnet
            }
            Network.GreenLiquid -> {
                if (isElectrum) Network.ElectrumLiquid else Network.GreenLiquid
            }
            Network.GreenTestnetLiquid -> {
                if (isElectrum) Network.ElectrumTestnetLiquid else Network.GreenTestnetLiquid
            }
            Network.GreenTestnet -> {
                if (isElectrum) Network.ElectrumTestnet else Network.GreenTestnet
            }else -> {
                networkType
            }
        }

        return copy(network = greenWallet.networks.getNetworkById(id), networkType = networkType)
    }

    companion object{
        fun fromWallet(wallet: Wallet, network: Network): OnboardingOptions {

            return OnboardingOptions(
                isRestoreFlow = true,
                isWatchOnly = false,
                networkType = wallet.network,
                network = network,
                walletName = wallet.name
            )

        }
    }
}