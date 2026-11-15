package io.github.beretta299.sharejoy.data.link

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

/**
 * Pairing on Android, through Nearby Connections. Create it in `onCreate`, before the activity starts, because
 * it registers for the permission dialog's result.
 */
class AndroidPairingPlatform(private val activity: ComponentActivity) : PairingPlatform {
    override val technologyName = "Nearby Connections"
    override val deviceName: String = Build.MODEL ?: "Android phone"

    private var pendingRequest: CompletableDeferred<Boolean>? = null
    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            pendingRequest?.complete(results.values.all { it })
            pendingRequest = null
        }

    override suspend fun ensurePermissions(): Boolean {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        val request = CompletableDeferred<Boolean>()
        pendingRequest = request
        permissionLauncher.launch(missing.toTypedArray())
        return request.await()
    }

    override fun createLink(): PeerLink = NearbyPeerLink(activity)

    /** Nearby Connections needs different runtime permissions on each generation of Android. */
    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
