package com.competra.core.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Подписка на GPS через [LocationManager] — не Google Play Services: приложение распространяется
 * во флейворах gplay/rustore/huawei, на Huawei без HMS play-services ненадёжны.
 *
 * Общая для трекинга тренировки и онлайн-трекинга на соревновании.
 */
class GpsLocationSource(private val context: Context) {

    private val locationManager by lazy { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private var listener: LocationListener? = null

    /** Есть ли разрешение на точную геолокацию. */
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    /** Включён ли GPS в настройках телефона. */
    fun isGpsEnabled(): Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    /**
     * Начинает получать фиксы GPS (колбэк — на главном потоке). Повторный вызов заменяет подписку.
     *
     * @return `false`, если нет разрешения или GPS выключен — подписки нет.
     */
    fun start(minTimeMs: Long, minDistanceM: Float, onLocation: (Location) -> Unit): Boolean {
        stop()
        if (!hasPermission() || !isGpsEnabled()) return false
        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(location)

            @Deprecated("Устарел с API 29, но метод всё ещё часть интерфейса")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        return try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, newListener, Looper.getMainLooper()
            )
            listener = newListener
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Останавливает подписку (безопасно вызывать повторно). */
    fun stop() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }
}
