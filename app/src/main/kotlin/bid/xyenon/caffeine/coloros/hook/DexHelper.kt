package bid.xyenon.caffeine.coloros.hook

import android.util.Log
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

object DexHelper {
    private const val TAG = "Caffeine:DexHelper"

    fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            Class.forName(className, false, classLoader)
        } catch (e: ClassNotFoundException) {
            null
        } catch (t: Throwable) {
            Log.w(TAG, "Error finding class $className", t)
            null
        }
    }

    fun findMethodExactIfExists(clazz: Class<*>?, methodName: String, vararg parameterTypes: Class<*>): Method? {
        if (clazz == null) return null
        return try {
            clazz.getDeclaredMethod(methodName, *parameterTypes).apply {
                isAccessible = true
            }
        } catch (e: NoSuchMethodException) {
            // Check superclass
            var current: Class<*>? = clazz.superclass
            while (current != null && current != Any::class.java) {
                try {
                    return current.getDeclaredMethod(methodName, *parameterTypes).apply {
                        isAccessible = true
                    }
                } catch (e2: NoSuchMethodException) {
                    current = current.superclass
                }
            }
            null
        } catch (t: Throwable) {
            Log.w(TAG, "Error finding method $methodName on ${clazz.name}", t)
            null
        }
    }

    fun findMethodByName(clazz: Class<*>?, methodName: String): Method? {
        if (clazz == null) return null
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (m in current.declaredMethods) {
                if (m.name == methodName) {
                    m.isAccessible = true
                    return m
                }
            }
            current = current.superclass
        }
        return null
    }

    fun getFieldValue(target: Any, fieldName: String): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        return null
    }

    fun setFieldValue(target: Any, fieldName: String, value: Any?) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
    }
}
