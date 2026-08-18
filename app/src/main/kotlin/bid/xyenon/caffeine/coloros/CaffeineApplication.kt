package bid.xyenon.caffeine.coloros

import android.app.Application
import android.os.Handler
import android.os.Looper
import bid.xyenon.caffeine.coloros.core.CaffeineConfig
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class CaffeineApplication : Application(), XposedServiceHelper.OnServiceListener {

    fun interface ServiceListener {
        fun onServiceChanged()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val services = linkedSetOf<XposedService>()
    private val listeners = linkedSetOf<ServiceListener>()

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        synchronized(services) {
            services.add(service)
        }
        notifyListeners()
    }

    override fun onServiceDied(service: XposedService) {
        synchronized(services) {
            services.remove(service)
        }
        notifyListeners()
    }

    fun addServiceListener(listener: ServiceListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeServiceListener(listener: ServiceListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun hasBoundService(): Boolean = synchronized(services) {
        services.isNotEmpty()
    }

    fun isSystemUiInScope(): Boolean = synchronized(services) {
        services.any { service ->
            runCatching { CaffeineConfig.SYSTEM_UI_PACKAGE in service.scope }.getOrDefault(false)
        }
    }

    private fun notifyListeners() {
        mainHandler.post {
            val snapshot = synchronized(listeners) { listeners.toList() }
            snapshot.forEach(ServiceListener::onServiceChanged)
        }
    }
}
