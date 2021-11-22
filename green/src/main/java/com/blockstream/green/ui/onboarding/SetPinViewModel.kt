package com.blockstream.green.ui.onboarding

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.blockstream.green.data.OnboardingOptions
import com.blockstream.green.database.Wallet
import com.blockstream.green.database.WalletRepository
import com.blockstream.green.gdk.SessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

class SetPinViewModel @AssistedInject constructor(
    sessionManager: SessionManager,
    walletRepository: WalletRepository,
    @Assisted val onboardingOptions: OnboardingOptions,
    @Assisted restoreWallet: Wallet?
) : OnboardingViewModel(sessionManager, walletRepository, restoreWallet) {

    val isPinVerified = MutableLiveData(false)

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(
            onboardingOptions: OnboardingOptions,
            restoreWallet: Wallet?
        ): SetPinViewModel
    }

    companion object {
        fun provideFactory(
            assistedFactory: AssistedFactory,
            onboardingOptions: OnboardingOptions,
            restoreWallet: Wallet?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(onboardingOptions, restoreWallet) as T
            }
        }
    }
}