package bid.xyenon.caffeine.coloros.hook

import android.util.Log
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object HookBridge {
    private const val TAG = "Caffeine:HookBridge"

    @Volatile
    var libXposedModule: XposedModule? = null

    /**
     * Intercept a method call with full control over proceeding or replacing.
     * Guaranteed safe: if an exception occurs in callback or fallback, it automatically proceeds.
     */
    fun hook(
        method: Method,
        interceptor: (thisObject: Any?, args: Array<Any?>, proceed: () -> Any?) -> Any?
    ) {
        val module = libXposedModule
        if (module != null) {
            try {
                module.hook(method).intercept { chain ->
                    @Suppress("UNCHECKED_CAST")
                    val argsList = chain.args
                    val argsArray = if (argsList is List<*>) argsList.toTypedArray() as Array<Any?> else emptyArray<Any?>()
                    try {
                        interceptor(chain.thisObject, argsArray) {
                            chain.proceed()
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in hook interceptor for ${method.name}, proceeding default", t)
                        chain.proceed()
                    }
                }
                Log.d(TAG, "Hooked (LibXposed): ${method.declaringClass.name}.${method.name}")
                return
            } catch (t: Throwable) {
                Log.w(TAG, "LibXposed hook failed for ${method.name}, trying classic", t)
            }
        }

        // Classic Xposed fallback
        try {
            val xcMethodHookClass = DexHelper.findClassIfExists("de.robv.android.xposed.XC_MethodHook", method.declaringClass.classLoader)
            val xposedBridgeClass = DexHelper.findClassIfExists("de.robv.android.xposed.XposedBridge", method.declaringClass.classLoader)
            if (xcMethodHookClass != null && xposedBridgeClass != null) {
                de.robv.android.xposed.XposedBridge.hookMethod(method, object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            var replaced = false
                            var replacedResult: Any? = null
                            val result = interceptor(param.thisObject, param.args) {
                                // Default proceed
                                null
                            }
                            // If interceptor returned a non-null replacement or explicitly handled
                            if (result != null) {
                                param.result = result
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in classic beforeHookedMethod for ${method.name}", t)
                        }
                    }
                })
                Log.d(TAG, "Hooked (Classic): ${method.declaringClass.name}.${method.name}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to classic hook ${method.name}", t)
        }
    }

    fun hookAfter(
        method: Method,
        callback: (thisObject: Any?, args: Array<Any?>, result: Any?) -> Unit
    ) {
        val module = libXposedModule
        if (module != null) {
            try {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val argsList = chain.args
                        val argsArray = if (argsList is List<*>) argsList.toTypedArray() as Array<Any?> else emptyArray<Any?>()
                        callback(chain.thisObject, argsArray, result)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in hookAfter callback for ${method.name}", t)
                    }
                    result
                }
                Log.d(TAG, "Hooked (LibXposed) after: ${method.declaringClass.name}.${method.name}")
                return
            } catch (t: Throwable) {
                Log.w(TAG, "LibXposed hookAfter failed for ${method.name}, trying classic", t)
            }
        }

        // Classic Xposed fallback
        try {
            val xcMethodHookClass = DexHelper.findClassIfExists("de.robv.android.xposed.XC_MethodHook", method.declaringClass.classLoader)
            val xposedBridgeClass = DexHelper.findClassIfExists("de.robv.android.xposed.XposedBridge", method.declaringClass.classLoader)
            if (xcMethodHookClass != null && xposedBridgeClass != null) {
                de.robv.android.xposed.XposedBridge.hookMethod(method, object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            callback(param.thisObject, param.args, param.result)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in classic afterHookedMethod for ${method.name}", t)
                        }
                    }
                })
                Log.d(TAG, "Hooked (Classic) after: ${method.declaringClass.name}.${method.name}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to classic hookAfter ${method.name}", t)
        }
    }

    fun hookBefore(
        method: Method,
        callback: (thisObject: Any?, args: Array<Any?>) -> Unit
    ) {
        val module = libXposedModule
        if (module != null) {
            try {
                module.hook(method).intercept { chain ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val argsList = chain.args
                        val argsArray = if (argsList is List<*>) argsList.toTypedArray() as Array<Any?> else emptyArray<Any?>()
                        callback(chain.thisObject, argsArray)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in hookBefore callback for ${method.name}", t)
                    }
                    chain.proceed()
                }
                Log.d(TAG, "Hooked (LibXposed) before: ${method.declaringClass.name}.${method.name}")
                return
            } catch (t: Throwable) {
                Log.w(TAG, "LibXposed hookBefore failed for ${method.name}, trying classic", t)
            }
        }

        // Classic Xposed fallback
        try {
            val xcMethodHookClass = DexHelper.findClassIfExists("de.robv.android.xposed.XC_MethodHook", method.declaringClass.classLoader)
            val xposedBridgeClass = DexHelper.findClassIfExists("de.robv.android.xposed.XposedBridge", method.declaringClass.classLoader)
            if (xcMethodHookClass != null && xposedBridgeClass != null) {
                de.robv.android.xposed.XposedBridge.hookMethod(method, object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            callback(param.thisObject, param.args)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in classic beforeHookedMethod for ${method.name}", t)
                        }
                    }
                })
                Log.d(TAG, "Hooked (Classic) before: ${method.declaringClass.name}.${method.name}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to classic hookBefore ${method.name}", t)
        }
    }
}
