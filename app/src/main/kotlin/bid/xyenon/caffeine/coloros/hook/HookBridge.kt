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
        val module = libXposedModule ?: return
        try {
            module.hook(method).intercept { chain ->
                @Suppress("UNCHECKED_CAST")
                val argsArray = chain.args.toTypedArray() as Array<Any?>
                try {
                    interceptor(chain.thisObject, argsArray) {
                        chain.proceed()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in hook interceptor for ${method.name}, proceeding default", t)
                    chain.proceed()
                }
            }
            Log.d(TAG, "Hooked: ${method.declaringClass.name}.${method.name}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ${method.name}", t)
        }
    }

    fun hookAfter(
        method: Method,
        callback: (thisObject: Any?, args: Array<Any?>, result: Any?) -> Unit
    ) {
        val module = libXposedModule ?: return
        try {
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    @Suppress("UNCHECKED_CAST")
                    val argsArray = chain.args.toTypedArray() as Array<Any?>
                    callback(chain.thisObject, argsArray, result)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in hookAfter callback for ${method.name}", t)
                }
                result
            }
            Log.d(TAG, "Hooked after: ${method.declaringClass.name}.${method.name}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hookAfter ${method.name}", t)
        }
    }

    fun hookBefore(
        method: Method,
        callback: (thisObject: Any?, args: Array<Any?>) -> Unit
    ) {
        val module = libXposedModule ?: return
        try {
            module.hook(method).intercept { chain ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val argsArray = chain.args.toTypedArray() as Array<Any?>
                    callback(chain.thisObject, argsArray)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in hookBefore callback for ${method.name}", t)
                }
                chain.proceed()
            }
            Log.d(TAG, "Hooked before: ${method.declaringClass.name}.${method.name}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hookBefore ${method.name}", t)
        }
    }
}
