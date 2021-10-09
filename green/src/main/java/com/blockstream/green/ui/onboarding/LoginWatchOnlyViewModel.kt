package com.blockstream.green.ui.onboarding

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.blockstream.green.database.CredentialType
import com.blockstream.green.database.LoginCredentials
import com.blockstream.green.database.Wallet
import com.blockstream.green.database.WalletRepository
import com.blockstream.green.gdk.GreenSession
import com.blockstream.green.gdk.SessionManager
import com.blockstream.green.gdk.observable
import com.blockstream.green.settings.SettingsManager
import com.blockstream.green.ui.AppViewModel
import com.blockstream.green.utils.AppKeystore
import com.blockstream.green.utils.ConsumableEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

@HiltViewModel
class LoginWatchOnlyViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val sessionManager: SessionManager,
    private val appKeystore: AppKeystore
) : AppViewModel() {

    var username = MutableLiveData("")
    var password = MutableLiveData("")
    val isRememberMe = MutableLiveData(true)
    val isTestnet = MutableLiveData(false)
    val newWallet: MutableLiveData<Wallet> = MutableLiveData()

    val isLoginEnabled: LiveData<Boolean> by lazy {
        MediatorLiveData<Boolean>().apply {
            val block = { _: Any? ->
                value =
                    !username.value.isNullOrBlank() && !password.value.isNullOrBlank() && !onProgress.value!!
            }

            addSource(username, block)
            addSource(password, block)
            addSource(onProgress, block)
        }
    }


    fun login() {
        val session: GreenSession = sessionManager.getOnBoardingSession()

        session.observable {
            val network = if (isTestnet.value == true) it.networks.testnetGreen else it.networks.bitcoinGreen
            val loginData = it.loginWatchOnly(network, username.value ?: "", password.value ?: "")

            val wallet = Wallet(
                walletHashId = loginData.walletHashId,
                name = "Bitcoin Watch Only",
                network = session.network.id,
                isRecoveryPhraseConfirmed = true,
                watchOnlyUsername = username.value
            )

            wallet.id = walletRepository.addWallet(wallet)

            if (isRememberMe.value == true) {
                val encryptedData = appKeystore.encryptData(password.value!!.toByteArray())
                walletRepository.addLoginCredentialsSync(
                    LoginCredentials(
                        walletId = wallet.id,
                        credentialType = CredentialType.KEYSTORE,
                        encryptedData = encryptedData
                    )
                )
            }

            sessionManager.upgradeOnBoardingSessionToWallet(wallet)

            wallet

        }.doOnSubscribe {
            onProgress.postValue(true)
        }.doOnTerminate {
            onProgress.postValue(false)
        }.subscribeBy(
            onError = {
                onError.postValue(ConsumableEvent(it))
            },
            onSuccess = {
                newWallet.postValue(it)
            }
        ).addTo(disposables)
    }
}