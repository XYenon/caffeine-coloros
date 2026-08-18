package bid.xyenon.caffeine.coloros.ui

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import bid.xyenon.caffeine.coloros.R
import bid.xyenon.caffeine.coloros.core.CaffeineConfig
import bid.xyenon.caffeine.coloros.core.CaffeineEngine
import bid.xyenon.caffeine.coloros.core.TimeFormatter
import bid.xyenon.caffeine.coloros.databinding.ActivityMainBinding
import bid.xyenon.caffeine.coloros.service.CaffeineForegroundService
import bid.xyenon.caffeine.coloros.service.CaffeineTileService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var engine: CaffeineEngine

    private val stateListener = object : CaffeineEngine.StateListener {
        override fun onStateChanged(isActive: Boolean, duration: Int, secondsRemaining: Int) {
            runOnUiThread { updateUI() }
        }

        override fun onTick(secondsRemaining: Int, formattedTime: String) {
            runOnUiThread { updateUI() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine = CaffeineEngine.getInstance(this, ownsState = !isLSPosedHookActive())

        setupCollapsingToolbar()
        setupStatusCard()
        setupControls()
        setupPreferences()
        setupAddTileButton()
    }

    /**
     * Material Design 3 Collapsing Top App Bar animation.
     * Transitions from large title to small app bar title on scroll without any divider line.
     */
    private fun setupCollapsingToolbar() {
        val density = resources.displayMetrics.density
        val collapseDistance = 48 * density

        binding.topAppBar.alpha = 0f
        binding.tvSmallTitle.alpha = 0f

        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val progress = (scrollY / collapseDistance).coerceIn(0f, 1f)

            // Large title smoothly fades and scales
            binding.containerLargeTitle.alpha = (1f - progress * 1.4f).coerceIn(0f, 1f)

            // Top app bar (background + small title) smoothly fades in together on scroll
            if (progress > 0.3f) {
                val barProgress = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                binding.topAppBar.alpha = barProgress
                binding.tvSmallTitle.alpha = barProgress
                binding.tvSmallTitle.translationY = (1f - barProgress) * (4 * density)
            } else {
                binding.topAppBar.alpha = 0f
                binding.tvSmallTitle.alpha = 0f
            }
        }
    }

    override fun onStart() {
        super.onStart()
        engine.addListener(stateListener)
        engine.requestStateSync()
        updateUI()
    }

    override fun onStop() {
        super.onStop()
        engine.removeListener(stateListener)
    }

    /**
     * Hook target for LSPosed module.
     * When LSPosed hook is active, this method is hooked to return true.
     */
    fun isLSPosedHookActive(): Boolean {
        return false
    }

    private fun setupStatusCard() {
        if (isLSPosedHookActive()) {
            binding.cardStatus.visibility = View.GONE
            return
        }

        binding.ivStatusIcon.setImageResource(R.drawable.ic_caffeine_empty)
        binding.ivStatusIcon.setColorFilter(getColor(R.color.warning))
        binding.tvStatusTitle.setText(R.string.status_lsposed_inactive)
        binding.tvStatusDesc.setText(R.string.status_lsposed_desc_inactive)
    }

    private fun setupControls() {
        binding.btnToggleCaffeine.setOnClickListener {
            performFeedback()
            engine.cycleNext()
            updateUI()

            if (!isLSPosedHookActive()) {
                if (engine.isActive) {
                    CaffeineForegroundService.start(this)
                } else {
                    CaffeineForegroundService.stop(this)
                }
            }
        }
    }

    private fun setupPreferences() {
        val prefs = getSharedPreferences(CaffeineConfig.PREFS_NAME, Context.MODE_PRIVATE)

        val screenOffReset = prefs.getBoolean(CaffeineConfig.KEY_SCREEN_OFF_RESET, true)
        binding.switchScreenOff.isChecked = screenOffReset
        engine.resetOnScreenOff = screenOffReset

        binding.switchScreenOff.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(CaffeineConfig.KEY_SCREEN_OFF_RESET, isChecked).apply()
            engine.resetOnScreenOff = isChecked
        }

        val haptic = prefs.getBoolean(CaffeineConfig.KEY_HAPTIC_FEEDBACK, true)
        binding.switchHaptic.isChecked = haptic

        binding.switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(CaffeineConfig.KEY_HAPTIC_FEEDBACK, isChecked).apply()
        }
    }

    private fun setupAddTileButton() {
        binding.btnAddTile.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusBarManager = getSystemService(StatusBarManager::class.java)
                val component = ComponentName(this, CaffeineTileService::class.java)
                val icon = Icon.createWithResource(this, R.drawable.ic_caffeine_tile)
                statusBarManager?.requestAddTileService(
                    component,
                    getString(R.string.tile_caffeine),
                    icon,
                    Executors.newSingleThreadExecutor()
                ) { resultCode ->
                    runOnUiThread {
                        if (resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                            Toast.makeText(this, R.string.tile_added_success, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                try {
                    val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.help_step2, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateUI() {
        val isActive = engine.isActive
        val remaining = engine.secondsRemaining

        val stateText = when {
            !isActive -> getString(R.string.tile_state_off)
            engine.isInfinite -> getString(R.string.tile_state_infinite)
            else -> TimeFormatter.formatDuration(remaining)
        }

        // Only one primary countdown view on the main screen
        binding.tvStatePreview.text = stateText

        if (isActive) {
            binding.tvStatePreview.setTextColor(getColor(R.color.caffeine_active))
            binding.btnToggleCaffeine.setText(R.string.btn_toggle)
        } else {
            binding.tvStatePreview.setTextColor(getColor(R.color.caffeine_inactive))
            binding.btnToggleCaffeine.setText(R.string.btn_toggle)
        }
        // Keep description static so there is no duplicate countdown
        binding.tvStateDetail.setText(R.string.control_desc)
    }

    private fun performFeedback() {
        val prefs = getSharedPreferences(CaffeineConfig.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(CaffeineConfig.KEY_HAPTIC_FEEDBACK, true)) return

        try {
            binding.btnToggleCaffeine.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (t: Throwable) {
            val vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
