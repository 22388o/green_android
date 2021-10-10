package com.blockstream.green.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.GravityCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.blockstream.green.R
import com.blockstream.green.databinding.MainActivityBinding
import com.blockstream.green.devices.DeviceManager
import com.blockstream.green.gdk.SessionManager
import com.blockstream.green.utils.ConsumableEvent
import com.blockstream.green.utils.getVersionName
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import mu.KLogging
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppActivity() {

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var sessionManager: SessionManager

    private lateinit var binding: MainActivityBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = MainActivityBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        supportActionBar?.also {
            // Prevent replacing title from NavController
            it.setDisplayShowTitleEnabled(false)
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.overviewFragment, R.id.loginFragment, R.id.introFragment),
            binding.drawerLayout
        )

        binding.toolbar.setupWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.appBarLayout.isInvisible =
                (destination.id == R.id.introFragment || destination.id == R.id.onBoardingCompleteFragment)
        }

        // Set version into the main VM
        viewModel.buildVersion.value =
            getString(R.string.id_version_1s_2s).format(getVersionName(this), "")

        setupSecureScreenListener()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Handle BIP-21 uri
        intent?.data?.let {
            sessionManager.pendingBip21Uri.postValue(ConsumableEvent(it.toString()))

            if(navController.currentDestination?.id == R.id.introFragment) {
                Snackbar.make(
                    binding.root,
                    R.string.id_you_have_clicked_a_payment_uri,
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.id_cancel) {
                    sessionManager.pendingBip21Uri.postValue(null)
                }.show()
            }
        }
    }

    override fun isDrawerOpen(): Boolean = binding.drawerLayout.isDrawerOpen(GravityCompat.START)

    override fun lockDrawer(isLocked: Boolean) {
        binding.drawerLayout.setDrawerLockMode(if (isLocked) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    override fun closeDrawer() {
        binding.drawerLayout.closeDrawers()
    }

    override fun setToolbar(
        title: String?, subtitle: String?, drawable: Drawable?, button: CharSequence?,
        buttonListener: View.OnClickListener?
    ){
        binding.toolbar.set(title, subtitle, drawable, null, button, buttonListener)
    }

    override fun setToolbarVisibility(isVisible: Boolean) {
        binding.appBarLayout.isVisible = isVisible
    }

    override fun onBackPressed() {
        if (isDrawerOpen()) {
            closeDrawer()
        } else {
            super.onBackPressed()
        }
    }

    companion object: KLogging()
}