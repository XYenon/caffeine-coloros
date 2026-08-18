package bid.xyenon.caffeine.coloros.hook

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class LibXposedEntry : XposedModule() {

    companion object {
        private const val TAG = "Caffeine:LibXposedEntry"
        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        HookBridge.libXposedModule = this
        Log.i(TAG, "LibXposed Module loaded in framework $frameworkName API $apiVersion for process ${param.processName}")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        HookBridge.libXposedModule = this
        if (param.packageName == PACKAGE_SYSTEMUI) {
            Log.i(TAG, "LibXposed onPackageReady: SystemUI (${param.packageName})")
            try {
                SystemUIHook.init(param.classLoader)
            } catch (t: Throwable) {
                Log.e(TAG, "LibXposed failed to initialize SystemUIHook", t)
            }
        }
    }
}
