package bid.xyenon.caffeine.coloros.hook

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import bid.xyenon.caffeine.coloros.core.CaffeineConfig
import bid.xyenon.caffeine.coloros.core.CaffeineEngine
import bid.xyenon.caffeine.coloros.core.TimeFormatter
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Locale

object SystemUIHook {
    private const val TAG = "Caffeine:SystemUIHook"
    private const val PACKAGE_NAME = "bid.xyenon.caffeine.coloros"
    private const val TILE_SERVICE_CLASS = "bid.xyenon.caffeine.coloros.service.CaffeineTileService"

    private val activeTiles = mutableSetOf<WeakReference<Any>>()
    private var engineListenerRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(classLoader: ClassLoader) {
        Log.i(TAG, "Initializing SystemUI Hook for OxygenOS / ColorOS safely...")

        try {
            hookQSTileClasses(classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook QS Tile classes", t)
        }

        try {
            hookQSFactory(classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook QSFactory", t)
        }

        try {
            hookQSTileHost(classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook QSTileHost", t)
        }
    }

    @SuppressLint("MissingPermission") // Runs inside the privileged SystemUI process.
    private fun hookQSTileClasses(classLoader: ClassLoader) {
        val targetClasses = listOf(
            "com.android.systemui.qs.tileimpl.QSTileImpl",
            "com.oplus.systemui.qs.tileimpl.OplusQSTileImpl",
            "com.android.systemui.qs.external.CustomTile",
            "com.oplus.systemui.qs.external.OplusCustomTile"
        )

        val hookedClasses = mutableSetOf<Class<*>>()

        for (name in targetClasses) {
            val clazz = DexHelper.findClassIfExists(name, classLoader) ?: continue
            if (!hookedClasses.add(clazz)) continue

            Log.i(TAG, "Hooking QS Tile class: $name")

            // 1. Hook handleClick(View) or handleClick(Expandable)
            val handleClickMethods = clazz.declaredMethods.filter { it.name == "handleClick" }
            for (method in handleClickMethods) {
                HookBridge.hook(method) { tile, args, proceed ->
                    if (tile != null && isCaffeineTile(tile)) {
                        try {
                            handleCaffeineClick(tile, args)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error handling caffeine click", t)
                        }
                        // Consume click for Caffeine
                        null
                    } else {
                        // CRITICAL: Always proceed for all other system tiles (Wi-Fi, Bluetooth, etc.)!
                        proceed()
                    }
                }
            }

            // 2. Hook handleUpdateState(State, Object)
            val handleUpdateStateMethods = clazz.declaredMethods.filter { it.name == "handleUpdateState" }
            for (method in handleUpdateStateMethods) {
                HookBridge.hookAfter(method) { tile, args, _ ->
                    if (tile == null || !isCaffeineTile(tile)) return@hookAfter

                    try {
                        val state = args.getOrNull(0) ?: return@hookAfter
                        val context = getTileContext(tile) ?: return@hookAfter
                        val engine = CaffeineEngine.getInstance(context)

                        registerEngineListenerIfNeeded(context)
                        trackTileInstance(tile)

                        // Update State fields
                        val isActive = engine.isActive
                        val remaining = engine.secondsRemaining

                        // State.state: 1 = INACTIVE, 2 = ACTIVE
                        val tileState = if (isActive) 2 else 1
                        DexHelper.setFieldValue(state, "state", tileState)

                        val isZh = Locale.getDefault().language == "zh"
                        val baseLabel = if (isZh) "咖啡因" else "Caffeine"
                        val offText = if (isZh) "关闭" else "Off"

                        val subLabel = when {
                            !isActive -> offText
                            engine.isInfinite -> "∞"
                            else -> TimeFormatter.formatDuration(remaining, offText, "∞")
                        }

                        // In OxygenOS / ColorOS 12/13/14/15, 1x1 circular tiles ONLY display 'label'
                        val displayLabel = if (isActive) {
                            "$baseLabel ($subLabel)"
                        } else {
                            baseLabel
                        }

                        DexHelper.setFieldValue(state, "label", displayLabel)
                        DexHelper.setFieldValue(state, "secondaryLabel", subLabel)
                        DexHelper.setFieldValue(state, "contentDescription", "$baseLabel, $subLabel")

                        try {
                            DexHelper.setFieldValue(state, "handlesLongClick", true)
                        } catch (t: Throwable) {
                            // Ignore
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in handleUpdateState for Caffeine", t)
                    }
                }
            }

            // 3. Hook getLongClickIntent
            val getLongClickIntentMethods = clazz.declaredMethods.filter { it.name == "getLongClickIntent" }
            for (method in getLongClickIntentMethods) {
                HookBridge.hook(method) { tile, _, proceed ->
                    if (tile != null && isCaffeineTile(tile)) {
                        Intent().apply {
                            setComponent(ComponentName(PACKAGE_NAME, "bid.xyenon.caffeine.coloros.ui.MainActivity"))
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                    } else {
                        // CRITICAL: Always return normal intent for other system tiles!
                        proceed()
                    }
                }
            }

            val handleLongClickMethods = clazz.declaredMethods.filter { it.name == "handleLongClick" }
            for (method in handleLongClickMethods) {
                HookBridge.hook(method) { tile, args, proceed ->
                    if (tile != null && isCaffeineTile(tile)) {
                        val context = getTileContext(tile)
                        if (context != null) {
                            try {
                                val intent = Intent().apply {
                                    setComponent(ComponentName(PACKAGE_NAME, "bid.xyenon.caffeine.coloros.ui.MainActivity"))
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }

                                // 1. Try ActivityStarter inside tile
                                var started = false
                                val activityStarter = DexHelper.getFieldValue(tile, "mActivityStarter")
                                if (activityStarter != null) {
                                    val startMethod = DexHelper.findMethodByName(activityStarter.javaClass, "postStartActivityDismissingKeyguard")
                                        ?: DexHelper.findMethodByName(activityStarter.javaClass, "startActivity")
                                    if (startMethod != null) {
                                        val viewArg = args.firstOrNull { it is View } as? View
                                        if (startMethod.parameterTypes.size == 3) {
                                            startMethod.invoke(activityStarter, intent, 0, null)
                                            started = true
                                        } else if (startMethod.parameterTypes.size == 2) {
                                            startMethod.invoke(activityStarter, intent, 0)
                                            started = true
                                        }
                                    }
                                }

                                // 2. Try collapsing panels via mHost
                                val host = DexHelper.getFieldValue(tile, "mHost")
                                if (host != null) {
                                    DexHelper.findMethodByName(host.javaClass, "collapsePanels")?.invoke(host)
                                }

                                // 3. Send close system dialogs broadcast
                                try {
                                    @Suppress("DEPRECATION")
                                    context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                                } catch (t: Throwable) {
                                    // Ignore
                                }

                                if (!started) {
                                    context.startActivity(intent)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Failed to launch Caffeine MainActivity on long click", t)
                            }
                        }
                        null
                    } else {
                        // CRITICAL: Always proceed for other system tiles!
                        proceed()
                    }
                }
            }
        }
    }

    private fun handleCaffeineClick(tile: Any, args: Array<Any?>) {
        val context = getTileContext(tile) ?: return
        val engine = CaffeineEngine.getInstance(context)
        Log.d(TAG, "Caffeine tile clicked")

        // Perform haptic feedback safely
        try {
            val viewArg = args.firstOrNull { it is View } as? View
            if (viewArg != null) {
                viewArg.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                val vibrator = context.getSystemService(Vibrator::class.java)
                vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (t: Throwable) {
            // Ignore haptic failure
        }

        // Cycle next duration in SystemUI
        engine.cycleNext()

        // Refresh tile state immediately
        refreshTile(tile)
    }

    private fun hookQSFactory(classLoader: ClassLoader) {
        val qsFactoryClassNames = listOf(
            "com.android.systemui.qs.tileimpl.QSFactoryImpl",
            "com.oplus.systemui.qs.tileimpl.OplusQSFactoryImpl",
            "com.oplus.systemui.qs.tileimpl.QSFactoryImpl"
        )

        for (name in qsFactoryClassNames) {
            val factoryClass = DexHelper.findClassIfExists(name, classLoader) ?: continue
            val createMethods = factoryClass.declaredMethods.filter {
                (it.name == "createTile" || it.name == "createTileInternal") &&
                        it.parameterTypes.isNotEmpty() &&
                        it.parameterTypes[0] == String::class.java
            }

            for (method in createMethods) {
                HookBridge.hookBefore(method) { _, args ->
                    val spec = args.getOrNull(0) as? String ?: return@hookBefore
                    if (spec == "caffeine") {
                        Log.i(TAG, "QSFactory requested 'caffeine' spec, transforming to custom tile")
                        args[0] = "custom($PACKAGE_NAME/$TILE_SERVICE_CLASS)"
                    }
                }
            }
        }
    }

    private fun hookQSTileHost(classLoader: ClassLoader) {
        val tileHostClassNames = listOf(
            "com.android.systemui.qs.QSTileHost",
            "com.oplus.systemui.qs.OplusQSTileHost",
            "com.oplus.systemui.qs.QSTileHost"
        )

        for (name in tileHostClassNames) {
            val hostClass = DexHelper.findClassIfExists(name, classLoader) ?: continue
            val loadSpecsMethods = hostClass.declaredMethods.filter {
                (it.name == "loadTileSpecs" || it.name == "getDefaultTileList" || it.name == "changeTiles")
            }

            for (method in loadSpecsMethods) {
                HookBridge.hookAfter(method) { _, _, result ->
                    if (result is MutableList<*>) {
                        @Suppress("UNCHECKED_CAST")
                        val list = result as MutableList<String>
                        val customSpec = "custom($PACKAGE_NAME/$TILE_SERVICE_CLASS)"
                        if (!list.contains(customSpec) && !list.contains("caffeine")) {
                            Log.d(TAG, "Available tile specs queried in SystemUI")
                        }
                    }
                }
            }
        }
    }

    fun isCaffeineTile(tile: Any?): Boolean {
        if (tile == null) return false
        return try {
            val spec = (DexHelper.findMethodByName(tile.javaClass, "getTileSpec")?.invoke(tile) as? String)
                ?: (DexHelper.getFieldValue(tile, "mTileSpec") as? String)
                ?: (DexHelper.getFieldValue(tile, "tileSpec") as? String)
                ?: (DexHelper.getFieldValue(tile, "mSpec") as? String)
            if (spec != null && (spec.contains(PACKAGE_NAME) || spec.contains("caffeine"))) {
                return true
            }

            val component = DexHelper.getFieldValue(tile, "mComponent") as? ComponentName
                ?: DexHelper.getFieldValue(tile, "component") as? ComponentName
            if (component != null && (component.packageName == PACKAGE_NAME || component.className.contains("Caffeine"))) {
                return true
            }

            val str = tile.toString()
            str.contains(PACKAGE_NAME) || str.contains("Caffeine")
        } catch (t: Throwable) {
            false
        }
    }

    private fun getTileContext(tile: Any): Context? {
        return try {
            DexHelper.getFieldValue(tile, "mContext") as? Context
                ?: DexHelper.getFieldValue(tile, "context") as? Context
                ?: (DexHelper.getFieldValue(tile, "mHost")?.let { host ->
                    DexHelper.getFieldValue(host, "mContext") as? Context
                        ?: DexHelper.getFieldValue(host, "context") as? Context
                })
        } catch (t: Throwable) {
            null
        }
    }

    private fun trackTileInstance(tile: Any) {
        synchronized(activeTiles) {
            val iterator = activeTiles.iterator()
            var exists = false
            while (iterator.hasNext()) {
                val ref = iterator.next().get()
                if (ref == null) {
                    iterator.remove()
                } else if (ref === tile) {
                    exists = true
                }
            }
            if (!exists) {
                activeTiles.add(WeakReference(tile))
            }
        }
    }

    private fun registerEngineListenerIfNeeded(context: Context) {
        if (engineListenerRegistered) return
        engineListenerRegistered = true

        val engine = CaffeineEngine.getInstance(context)
        engine.addListener(object : CaffeineEngine.StateListener {
            override fun onStateChanged(isActive: Boolean, duration: Int, secondsRemaining: Int) {
                mainHandler.post { refreshAllActiveTiles() }
            }

            override fun onTick(secondsRemaining: Int, formattedTime: String) {
                mainHandler.post { refreshAllActiveTiles() }
            }
        })
    }

    private fun refreshAllActiveTiles() {
        synchronized(activeTiles) {
            val iterator = activeTiles.iterator()
            while (iterator.hasNext()) {
                val tile = iterator.next().get()
                if (tile != null) {
                    refreshTile(tile)
                } else {
                    iterator.remove()
                }
            }
        }
    }

    private fun refreshTile(tile: Any) {
        try {
            // 1. Try public refreshState()
            val refreshMethod = DexHelper.findMethodByName(tile.javaClass, "refreshState")
            if (refreshMethod != null) {
                if (refreshMethod.parameterTypes.isEmpty()) {
                    refreshMethod.invoke(tile)
                    return
                } else if (refreshMethod.parameterTypes.size == 1) {
                    refreshMethod.invoke(tile, null)
                    return
                }
            }

            // 2. Try handleRefreshState(arg)
            val handleRefreshMethod = DexHelper.findMethodByName(tile.javaClass, "handleRefreshState")
            if (handleRefreshMethod != null) {
                if (handleRefreshMethod.parameterTypes.isEmpty()) {
                    handleRefreshMethod.invoke(tile)
                    return
                } else if (handleRefreshMethod.parameterTypes.size == 1) {
                    handleRefreshMethod.invoke(tile, null)
                    return
                }
            }

            // 3. Fallback: post directly via tile's mHandler
            val handler = DexHelper.getFieldValue(tile, "mHandler") as? Handler
            handler?.post {
                try {
                    val state = DexHelper.getFieldValue(tile, "mState")
                    val updateMethod = DexHelper.findMethodByName(tile.javaClass, "handleUpdateState")
                    if (state != null && updateMethod != null) {
                        if (updateMethod.parameterTypes.size == 2) {
                            updateMethod.invoke(tile, state, null)
                        } else if (updateMethod.parameterTypes.size == 1) {
                            updateMethod.invoke(tile, state)
                        }
                    }
                    val stateChangedMethod = DexHelper.findMethodByName(tile.javaClass, "handleStateChanged")
                    stateChangedMethod?.invoke(tile)
                } catch (t: Throwable) {
                    // Ignore
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to invoke refresh on tile", t)
        }
    }
}
