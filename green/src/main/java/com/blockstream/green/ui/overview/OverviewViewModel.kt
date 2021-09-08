package com.blockstream.green.ui.overview

import androidx.lifecycle.*


import com.blockstream.gdk.*
import com.blockstream.gdk.data.SubAccount
import com.blockstream.gdk.data.Transaction
import com.blockstream.green.database.Wallet
import com.blockstream.green.database.WalletRepository
import com.blockstream.green.gdk.SessionManager
import com.blockstream.green.ui.items.AlertType
import com.blockstream.green.ui.wallet.AbstractWalletViewModel
import com.blockstream.green.utils.ConsumableEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.addTo
import kotlin.properties.Delegates


class OverviewViewModel @AssistedInject constructor(
    sessionManager: SessionManager,
    walletRepository: WalletRepository,
    @Assisted wallet: Wallet,
) : AbstractWalletViewModel(sessionManager, walletRepository, wallet) {

    enum class State {
        Overview, Account, Asset
    }

    val isWatchOnly: LiveData<Boolean> = MutableLiveData(wallet.isWatchOnly)


    private val systemMessage: MutableLiveData<String?> = MutableLiveData()
    private val state: MutableLiveData<State> = MutableLiveData(State.Overview)
    private val filteredSubAccounts: MutableLiveData<List<SubAccount>> = MutableLiveData()

    private var allBalances: Balances = linkedMapOf(BalanceLoading)
    private val shownBalances: MutableLiveData<Balances> = MutableLiveData(allBalances)
    private val alerts = MutableLiveData<List<AlertType>>(listOf())
    private val transactions: MutableLiveData<List<Transaction>> = MutableLiveData(listOf(
        Transaction.LoadingTransaction))

    private val assetsUpdated: MutableLiveData<ConsumableEvent<Boolean>> = MutableLiveData()
    private val selectedAsset = MutableLiveData<String?>()

    fun getSystemMessage(): LiveData<String?> = systemMessage
    fun getState(): LiveData<State> = state
    fun getFilteredSubAccounts(): LiveData<List<SubAccount>> = filteredSubAccounts
    fun getBalancesLiveData(): LiveData<Balances> = shownBalances
    fun getAlerts(): LiveData<List<AlertType>> = alerts
    fun isMainnet(): LiveData<Boolean> = MutableLiveData(session.isMainnet)
    fun isLiquid(): LiveData<Boolean> = MutableLiveData(wallet.isLiquid)
    fun getTransactions(): LiveData<List<Transaction>> = transactions

    fun getSelectedAsset(): LiveData<String?> = selectedAsset
    fun getAssetsUpdated(): MutableLiveData<ConsumableEvent<Boolean>> = assetsUpdated

    private var pendingSubAccountSwitch: Long = -1

    private var allSubAccounts: List<SubAccount> by Delegates.observable(listOf()) { _, _, newValue ->
        filteredSubAccounts.value = filterSubAccounts(newValue)
    }

    init {
        session.setActiveAccount(wallet.activeAccount)

        session
            .getBalancesObservable()
            .subscribe {
                allBalances = it
                shownBalances.postValue(it)
            }.addTo(disposables)

        session
            .getTransationsObservable()
            .subscribe {
                transactions.postValue(it)
            }.addTo(disposables)

        session.getAssetsObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                assetsUpdated.postValue(ConsumableEvent(true))
            }.addTo(disposables)

        session
            .getSubAccountsObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                allSubAccounts = it

                if (pendingSubAccountSwitch >= 0) {
                    allSubAccounts.find { subAccount ->
                        subAccount.pointer == pendingSubAccountSwitch
                    }?.let { subAccount ->
                        selectSubAccount(subAccount)
                    }

                    pendingSubAccountSwitch = -1
                }
            }.addTo(disposables)

        session
            .getSystemMessageObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                systemMessage.postValue(it)
            }.addTo(disposables)

        session
            .getTwoFactorResetObservable()
            .subscribe {
                if (it.isActive){
                    val list = mutableListOf<AlertType>()
                    if(it.isDisputed){
                        list += AlertType.Dispute2FA(it)
                    }else{
                        list += AlertType.Reset2FA(it)
                    }
                    alerts.postValue(list)
                }
            }.addTo(disposables)
    }

    fun refresh(){
        session.updateSubAccounts()
        session.updateTransactionsAndBalance(isReset = false, isLoadMore = false)
    }

    private fun filterSubAccounts(subAccounts: List<SubAccount>): List<SubAccount> {
        return subAccounts.filter { it.pointer != wallet.activeAccount }
    }

    fun setSubAccount(index: Long) {
        val subAccount = allSubAccounts.find {
            it.pointer == index
        }

        if (subAccount != null) {
            selectSubAccount(subAccount)
        } else {
            pendingSubAccountSwitch = index
            session.updateSubAccounts()
        }
    }

    override fun selectSubAccount(account: SubAccount) {
        super.selectSubAccount(account)

        allBalances = linkedMapOf(BalanceLoading)
        shownBalances.postValue(allBalances)

        allSubAccounts.let {
            filteredSubAccounts.value = filterSubAccounts(it)
        }
    }

    fun setAsset(balancePair: BalancePair) {
        // the ordering is important
        setState(State.Asset)
        selectedAsset.value = balancePair.first
        // shownBalances.value = linkedMapOf(balancePair)
    }

    fun setState(newState: State) {
//        val oldState = state.value!!

        state.value = newState

//        if (newState == State.Overview && oldState == State.Asset) {
//            shownBalances.value = allBalances
//        }
    }

    fun refreshTransactions(){
        session.updateTransactionsAndBalance(isReset = false, isLoadMore = false)
    }

    fun loadMoreTransactions(){
        logger.info { "loadMoreTransactions" }
        // session.loadMoreTransactions()
        session.updateTransactionsAndBalance(isReset = false, isLoadMore = true)
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(
            wallet: Wallet,
        ): OverviewViewModel
    }

    companion object {
        fun provideFactory(
            assistedFactory: AssistedFactory,
            wallet: Wallet,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                return assistedFactory.create(wallet) as T
            }
        }
    }
}