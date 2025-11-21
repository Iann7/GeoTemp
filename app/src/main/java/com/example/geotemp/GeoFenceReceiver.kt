    package com.example.geotemp

    import android.Manifest
    import android.content.BroadcastReceiver
    import android.content.Context
    import android.content.Intent
    import android.util.Log
    import androidx.annotation.RequiresPermission
    import com.google.android.gms.location.Geofence
    import com.google.android.gms.location.GeofencingEvent
    import androidx.core.content.edit
    import com.google.android.gms.location.LocationServices

    class GeofenceReceiver : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        override fun onReceive(context: Context, intent: Intent) {

            val event = GeofencingEvent.fromIntent(intent) ?: run {
                Log.e("GEOFENCE", "GeofencingEvent is null")
                return
            }

            if (event.hasError()) {
                Log.e("GEOFENCE", "Error: ${event.errorCode}")
                return
            }

            when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    Log.d("GEOFENCE", "User entered the destination!")
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    prefs.edit {
                        putLong("destination_timestamp", System.currentTimeMillis())
                    }
                    val geofencingClient = LocationServices.getGeofencingClient(context)
                    val geofenceID = prefs.getString("geofence_id","0")
                    if (geofenceID.equals("0")) return
                    geofencingClient.removeGeofences(listOf(geofenceID))
                }
            }
            NotificationHelper.showGeofenceNotification(context)
        }
    }

