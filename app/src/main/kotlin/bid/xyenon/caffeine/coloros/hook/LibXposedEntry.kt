package bid.xyenon.caffeine.coloros.hook

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class LibXposedEntry : XposedModule() {

    companion object {
        private const val TAG = "Caffeine:LibXposedEntry"
        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val PACKAGE_ANDROID = "android"
        private const val PACKAGE_SELF = "bid.xyenon.caffeine.coloros"
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        HookBridge.libXposedModule = this
        Log.i(TAG, "LibXposed Module loaded in framework $frameworkName API $apiVersion for process ${param.processName}")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        HookBridge.libXposedModule = this
        when (param.packageName) {
            PACKAGE_SYSTEMUI -> {
                Log.i(TAG, "LibXposed onPackageReady: SystemUI (${param.packageName})")
                try {
                    SystemUIHook.init(param.classLoader)
                } catch (t: Throwable) {
                    Log.e(TAG, "LibXposed failed to initialize SystemUIHook", t)
                }
            }

            PACKAGE_ANDROID -> {
                Log.i(TAG, "LibXposed onPackageReady: android system_server")
                try {
                    SystemServerHook.init(param.classLoader)
                } catch (t: Throwable) {
                    Log.e(TAG, "LibXposed failed to initialize SystemServerHook", t)
                }
            }

            PACKAGE_SELF -> {
                Log.i(TAG, "LibXposed onPackageReady: Self app (${param.packageName})")
                try {
                    val mainActivityClass = DexHelper.findClassIfExists("bid.xyenon.caffeine.coloros.ui.MainActivity", param.classLoader)
                    val isLSPosedHookActiveMethod = DexHelper.findMethodByName(mainActivityClass, "isLSPosedHookActive")
                    if (isLSPosedHookActiveMethod != null) {
                        HookBridge.hook(isLSPosedHookActiveMethod) { _, _, _ ->
                            true
                        }
                        Log.i(TAG, "Hooked self MainActivity.isLSPosedHookActive -> true")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to hook self MainActivity", t)
                }
            }
        }
    }
}
