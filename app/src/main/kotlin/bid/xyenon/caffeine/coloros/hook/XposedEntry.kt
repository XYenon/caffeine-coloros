package bid.xyenon.caffeine.coloros.hook

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        private const val TAG = "Caffeine:XposedEntry"
        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val PACKAGE_ANDROID = "android"
        private const val PACKAGE_SELF = "bid.xyenon.caffeine.coloros"
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        Log.i(TAG, "initZygote: Caffeine Xposed Entry loaded")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            PACKAGE_SYSTEMUI -> {
                Log.i(TAG, "Loading Caffeine hooks into SystemUI (${lpparam.processName})...")
                try {
                    SystemUIHook.init(lpparam.classLoader)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to initialize SystemUIHook", t)
                }
            }

            PACKAGE_ANDROID -> {
                Log.i(TAG, "Loading Caffeine hooks into SystemServer (${lpparam.processName})...")
                try {
                    SystemServerHook.init(lpparam.classLoader)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to initialize SystemServerHook", t)
                }
            }

            PACKAGE_SELF -> {
                try {
                    val mainActivityClass = DexHelper.findClassIfExists("bid.xyenon.caffeine.coloros.ui.MainActivity", lpparam.classLoader)
                    if (mainActivityClass != null) {
                        XposedHelpers.findAndHookMethod(
                            mainActivityClass,
                            "isLSPosedHookActive",
                            XC_MethodReplacement.returnConstant(true)
                        )
                        Log.i(TAG, "Hooked self MainActivity.isLSPosedHookActive -> true")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to hook self", t)
                }
            }
        }
    }
}
