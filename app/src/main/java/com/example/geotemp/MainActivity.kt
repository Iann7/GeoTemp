package com.example.geotemp

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.transition.Visibility
import com.google.android.gms.location.*
import com.google.android.material.textfield.TextInputEditText
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private  var locationResult: LocationResult? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val updateTimer = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val startTime = prefs.getLong("first_timestamp", -1L)

            if (startTime != -1L) {
                val elapsed = System.currentTimeMillis() - startTime
                findViewById<TextView>(R.id.tiempoActualPreview).text = elapsed.toDurationString()
            }

            timerHandler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)
        handlePermissions()
        handleTimestamp()
        handleDestinationPreviewText()
        handleButtonToggling()
        handleUserInput()
        updateTimer.run() // Start immediately
    }


    override fun onRestart() {
        super.onRestart()
        handleTimestamp()
        handleButtonToggling()
        handleDestinationPreviewText()
        updateTimer.run()
    }

    override fun onPause() {
        super.onPause()
        handleTimestamp()
        handleButtonToggling()
        handleDestinationPreviewText()
        timerHandler.removeCallbacks(updateTimer)
    }

    private fun handleDestinationPreviewText() {
        val destinationPreviewText: TextView = findViewById(R.id.destionActualPreview)
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        destinationPreviewText.text =  prefs.getString("dest_display_name","")
    }

    private fun updateTimer() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val firstTimeStamp = prefs.getLong("first_timestamp", -1L)
        if (firstTimeStamp == -1L) return

        val currentTime = System.currentTimeMillis()
        val timeView = findViewById<TextView>(R.id.tiempoActualPreview)
        timeView.text = (currentTime - firstTimeStamp).toDurationString()
    }

    private fun handleTimestamp(){
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        var lastTimestamp = prefs.getLong("destination_timestamp",-1L)
        val firstTimeStamp = prefs.getLong("first_timestamp",-1L)
        if(firstTimeStamp==-1L) return
        if(lastTimestamp == -1L) lastTimestamp = System.currentTimeMillis()
        if (firstTimeStamp>lastTimestamp){
            Log.d("FUTURE","Travelling back in time somehow")
        }
        val time_view = findViewById<TextView>(R.id.tiempoActualPreview)
        time_view.text = (lastTimestamp - firstTimeStamp).toDurationString()
        return
    }

    private fun handleButtonToggling(){
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val firstTimeStamp = prefs.getLong("first_timestamp",-1L)
        val activateButton:View = findViewById(R.id.button)
        val cancelButton:View = findViewById(R.id.button2)
        val timerPreview:View = findViewById(R.id.tiempoActualPreview)
        val writeDestination:View = findViewById(R.id.textInputLayout)
        if (firstTimeStamp == -1L){
            activateButton.visibility = View.VISIBLE
            writeDestination.visibility = View.VISIBLE
            cancelButton.visibility = View.INVISIBLE
            timerPreview.visibility =View.INVISIBLE
        } else{
            activateButton.visibility = View.INVISIBLE
            writeDestination.visibility = View.INVISIBLE
            cancelButton.visibility = View.VISIBLE
            timerPreview.visibility = View.VISIBLE
        }
    }
    fun Long.toDurationString(): String {
        var seconds = this / 1000
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60

        return String.format("%02dh %02dm %02ds", hours, minutes, seconds)
    }


    private fun handlePermissions(){
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            100
        )

      requestBackgroundPermission()
    }

    private fun requestBackgroundPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ (API 29)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                101
            )
        } else {
            // Android 9 or lower → no request needed
            Log.d("PERMISSIONS", "Background location not required on this Android version.")
        }
    }


    private fun handleUserInput() {
        val editText = findViewById<TextInputEditText>(R.id.location_input_text)

        editText.setOnEditorActionListener { _, actionId, event ->
            // When user presses “Enter/Done” on the keyboard
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val text = editText.text.toString()
                fetchLocationIQ(text, {result ->
                    if (result != null) {
                        updateDestination(result)
                    }
                })

                true
            } else {
                false
            }
        }
    }

    private fun updateDestination(result: LocationResult) {
        val destinationTextPreview = findViewById<TextView>(R.id.destionActualPreview)
        destinationTextPreview.text = result.displayName
        // Save to SharedPreferences
        locationResult = result
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            putString("dest_place_id", result.placeId)
            putString("dest_lat", result.lat)
            putString("dest_lon", result.lon)
            putString("dest_display_name", result.displayName)
            putLong("first_timestamp",System.currentTimeMillis())
            apply()
        }
        handleTimestamp()
        handleButtonToggling()
    }

     fun onCancelLoggingButton(view: View){
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit(){
            remove("dest_place_id")
            remove("dest_lat")
            remove("dest_lon")
            remove("dest_display_name")
            remove("first_timestamp")
            apply()
        }
         handleButtonToggling()
         handleDestinationPreviewText()
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceReceiver::class.java)
        PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun onStartGeoLogging(view: View) {
        if (locationResult == null) return
        Log.d("BUTTON", "GEOLOC STARTED")
        val geofenceID = UUID.randomUUID().toString()
        val geofence = Geofence.Builder()
            .setRequestId("destination")
            .setCircularRegion(
                locationResult!!.lat.toDouble(),
                locationResult!!.lon.toDouble(),
                200f   // radius in meters
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setRequestId(geofenceID)
            .build()
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        val geofencingClient = LocationServices.getGeofencingClient(this)
        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("GEOFENCE", "Geofence added successfully!")
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                prefs.edit {
                    putString("geofence_id", geofenceID)
                    apply() }

            }
            .addOnFailureListener { e ->
                Log.e("GEOFENCE", "Failed to add geofence: ${e.message}")
            }
    }


    private fun fetchLocationIQ(query: String, callback: (LocationResult?) -> Unit) {
        Thread {
            try {
                val apiKey = "pk.0ae44342bc6ce3269edd484e76741d9a"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val urlString =
                    "https://us1.locationiq.com/v1/search?key=$apiKey&q=$encodedQuery&format=xml"

                val conn = URL(urlString).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"

                val xml = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                // Parse XML
                val result = parseLocationXml(xml)

                runOnUiThread {
                    callback(result[0])
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    callback(null)
                }
            }
        }.start()
    }


    private fun parseLocationXml(xml: String): List<LocationResult> {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        val results = mutableListOf<LocationResult>()

        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "place") {

                val placeId = parser.getAttributeValue(null, "place_id") ?: ""
                val lat = parser.getAttributeValue(null, "lat") ?: ""
                val lon = parser.getAttributeValue(null, "lon") ?: ""
                val displayName = parser.getAttributeValue(null, "display_name") ?: ""

                results.add(
                    LocationResult(
                        placeId = placeId,
                        lat = lat,
                        lon = lon,
                        displayName = displayName
                    )
                )
            }

            eventType = parser.next()
        }

        return results
    }





}

data class LocationResult(
    val placeId: String,
    val lat: String,
    val lon: String,
    val displayName: String,
)

