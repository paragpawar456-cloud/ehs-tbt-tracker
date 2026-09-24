package com.ehs.tbttracker.data.photo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Float?)

interface LocationProvider {
    suspend fun currentLocation(): GeoPoint?
}

@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission") // checked below
    override suspend fun currentLocation(): GeoPoint? {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        return runCatching {
            val cts = CancellationTokenSource()
            val fresh = withTimeoutOrNull(6_000) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            } ?: run { cts.cancel(); client.lastLocation.await() }
            fresh?.let { GeoPoint(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null) }
        }.getOrNull()
    }
}
