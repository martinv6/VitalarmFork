package com.example.vitalarmapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vitalarmapp.databinding.ActivityLanMenuBinding
import com.example.vitalarmapp.navigation.BottomNavigationHelper
import com.example.vitalarmapp.ui.add.AddMainTabFragment
import com.example.vitalarmapp.ui.home.HomeTabFragment
import com.example.vitalarmapp.ui.profile.ProfileTabFragment
import com.example.vitalarmapp.utils.firebase.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LanMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanMenuBinding
    private var currentTabId: Int = R.id.nav_home
    private var userName: String? = null
    private var isUserNameLoading: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanMenuBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        setUserNameLoadingState(true)

        setupBottomNavigation()
        setupAppBar()

        val initialTab = savedInstanceState?.getInt(SELECTED_TAB_KEY) ?: R.id.nav_home
        currentTabId = initialTab
        binding.lanMenuBottomNavigation.selectedItemId = initialTab
        switchToTab(initialTab)

        loadUserNameIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentTab()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SELECTED_TAB_KEY, binding.lanMenuBottomNavigation.selectedItemId)
    }

    private fun setupAppBar() {
        binding.lanMenuToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_notifications -> {
                    startActivity(Intent(this, NotificationsActivity::class.java))
                    true
                }
                R.id.action_settings -> {
                    startActivity(SettingsActivity.intent(this))
                    true
                }

                else -> false
            }
        }
    }

    private fun setupBottomNavigation() {
        BottomNavigationHelper.setup(
            bottomNavigationView = binding.lanMenuBottomNavigation,
            onItemSelected = { itemId -> switchToTab(itemId) },
            onItemReselected = { itemId -> handleTabReselected(itemId) }
        )
    }

    private fun switchToTab(itemId: Int): Boolean {
        currentTabId = itemId
        updateAppBarVisibility(itemId)
        val fragment = showFragment(itemId) ?: return false
        if (itemId == R.id.nav_home) {
            loadUserNameIfNeeded()
            updateToolbarGreeting()
            (fragment as? HomeTabFragment)?.refreshContent()
        }
        return true
    }

    private fun refreshCurrentTab() {
        val currentFragment = supportFragmentManager.findFragmentByTag(fragmentTag(currentTabId))
        if (currentTabId == R.id.nav_home) {
            (currentFragment as? HomeTabFragment)?.refreshContent()
            loadUserNameIfNeeded()
            updateToolbarGreeting()
        }
    }

    private fun handleTabReselected(itemId: Int) {
        if (itemId == R.id.nav_home) {
            (supportFragmentManager.findFragmentByTag(fragmentTag(itemId)) as? HomeTabFragment)
                ?.refreshContent()
        }
    }

    private fun showFragment(itemId: Int): Fragment? {
        val tag = fragmentTag(itemId) ?: return null
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
            .setCustomAnimations(
                R.animator.m3_fade_through_enter,
                R.animator.m3_fade_through_exit,
                R.animator.m3_fade_through_enter,
                R.animator.m3_fade_through_exit
            )
            .setReorderingAllowed(true)

        fragmentManager.fragments.forEach { transaction.hide(it) }

        var fragment = fragmentManager.findFragmentByTag(tag)
        if (fragment == null) {
            fragment = when (itemId) {
                R.id.nav_home -> HomeTabFragment()
                R.id.nav_add -> AddMainTabFragment()
                R.id.nav_profile -> ProfileTabFragment()
                else -> null
            }
            fragment?.let { transaction.add(R.id.fragmentContainer, it, tag) }
        } else {
            transaction.show(fragment)
        }

        transaction.commit()
        return fragment
    }

    private fun updateAppBarVisibility(itemId: Int) {
        binding.lanMenuAppBar.isVisible = itemId == R.id.nav_home
    }

    private fun loadUserNameIfNeeded() {
        if (userName != null || isUserNameLoading) return
        isUserNameLoading = true
        setUserNameLoadingState(true)

        lifecycleScope.launch {
            val fetchedName = runCatching {
                withContext(Dispatchers.IO) {
                    FirebaseManager.getCurrentUserName()
                }
            }.getOrNull()

            userName = fetchedName
            updateToolbarGreeting()
            setUserNameLoadingState(false)
            isUserNameLoading = false
            (supportFragmentManager.findFragmentByTag(fragmentTag(R.id.nav_home)) as? HomeTabFragment)
                ?.onUserNameLoaded()
        }
    }

    private fun updateToolbarGreeting() {
        val toolbarTitle = userName?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.home_greeting, it) }
            ?: getString(R.string.home_greeting_fallback)
        binding.lanMenuToolbar.title = toolbarTitle
    }

    private fun setUserNameLoadingState(isLoading: Boolean) {
        binding.lanMenuLoadingIndicator.isVisible = isLoading
        binding.lanMenuContentGroup.isVisible = !isLoading
        (supportFragmentManager.findFragmentByTag(fragmentTag(R.id.nav_home)) as? HomeTabFragment)
            ?.setUserNameLoading(isLoading)
    }

    private fun fragmentTag(itemId: Int): String? = when (itemId) {
        R.id.nav_home -> TAG_HOME
        R.id.nav_add -> TAG_ADD
        R.id.nav_profile -> TAG_PROFILE
        else -> null
    }

    companion object {
        private const val SELECTED_TAB_KEY = "selected_tab"
        private const val TAG_HOME = "home_tab"
        private const val TAG_ADD = "add_tab"
        private const val TAG_PROFILE = "profile_tab"
    }
}
