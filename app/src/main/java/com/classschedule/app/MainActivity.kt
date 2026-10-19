package com.classschedule.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.content.res.Configuration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import com.classschedule.app.reminder.ReminderReceiver
import com.classschedule.app.ui.MainViewModel
import com.classschedule.app.ui.MineFragment
import com.classschedule.app.ui.OverviewFragment
import com.classschedule.app.ui.ScheduleFragment
import com.classschedule.app.update.UpdateCheckLauncher
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Navigation owns no tab view state. FragmentManager restores the same tagged instances. */
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private var selectedTab = R.id.tab_schedule
    private lateinit var navigation: BottomNavigationView
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { navigation.selectedItemId = R.id.tab_schedule }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(this, if (granted) R.string.reminder_granted else R.string.reminder_denied, Toast.LENGTH_SHORT).show()
        if (granted) ReminderReceiver.reschedule(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        navigation = findViewById(R.id.bottom_navigation)
        onBackPressedDispatcher.addCallback(this, back)
        selectedTab = savedInstanceState?.getInt("tab", R.id.tab_schedule) ?: R.id.tab_schedule
        if (selectedTab !in listOf(R.id.tab_schedule, R.id.tab_overview, R.id.tab_mine)) selectedTab = R.id.tab_schedule
        navigation.selectedItemId = selectedTab
        selectTab(selectedTab)
        navigation.setOnItemSelectedListener { selectTab(it.itemId) }
        val prefs = getSharedPreferences("permissions", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notification_asked", false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("notification_asked", true).apply()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!viewModel.startupUpdateChecked) {
            viewModel.startupUpdateChecked = true
            window.decorView.post { if (!isFinishing && !isDestroyed) checkForUpdate(false) }
        }
    }
    private fun selectTab(id: Int): Boolean {
        if (supportFragmentManager.isStateSaved) return false
        val tag = "tab_$id"
        val target = supportFragmentManager.findFragmentByTag(tag) ?: when (id) {
            R.id.tab_overview -> OverviewFragment()
            R.id.tab_mine -> MineFragment()
            else -> ScheduleFragment()
        }
        val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)
        supportFragmentManager.fragments.filter { it != target }.forEach {
            transaction.hide(it).setMaxLifecycle(it, Lifecycle.State.STARTED)
        }
        if (!target.isAdded) {
            transaction.add(R.id.tab_container, target, tag)
        }
        transaction.show(target).setMaxLifecycle(target, Lifecycle.State.RESUMED)
            .setPrimaryNavigationFragment(target).commitNow()
        selectedTab = id
        back.isEnabled = id != R.id.tab_schedule
        return true
    }
    fun checkForUpdate(userInitiated: Boolean) {
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: return
        UpdateCheckLauncher.check(this, version, userInitiated)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", selectedTab)
        super.onSaveInstanceState(outState)
    }
}
