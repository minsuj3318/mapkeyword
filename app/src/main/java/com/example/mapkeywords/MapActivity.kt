package com.example.mapkeywords

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos

/**
 * 현재 위치 기준 반경 안의 키워드(여러 개 가능) 장소를 지도에 표시한다.
 * 키워드마다 마커 색이 다르고, 현재 위치는 방향(나침반)에 따라 회전하는 화살표로 표시된다.
 */
class MapActivity : AppCompatActivity(), LocationListener, SensorEventListener {

    private lateinit var map: MapView
    private lateinit var statusText: TextView
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    private lateinit var keywords: List<String>
    private var radius = 500
    private var mode = "driving"

    private var myMarker: Marker? = null
    private var circle: Polygon? = null
    private val poiMarkers = mutableListOf<Marker>()

    private var lastLocation: Location? = null
    private var lastQueryLocation: Location? = null
    private var nextQueryAllowedAt = 0L
    private var queryRunning = false
    private var firstFix = true
    private var permissionRequested = false
    private var heading = 0f

    private val executor = Executors.newSingleThreadExecutor()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 지도 타일 캐시는 앱 전용 캐시 폴더에 저장
        Configuration.getInstance().apply {
            // OSM 타일 서버는 앱을 식별할 수 있는 User-Agent가 없으면 접속을 차단한다
            userAgentValue = "MapKeywords/1.0 (+https://github.com/minsuj3318/mapkeyword)"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        setContentView(R.layout.activity_map)
        ApiKeys.load(this)

        val list = intent.getStringArrayExtra(EXTRA_KEYWORDS)
            ?.filter { it.isNotBlank() }
            ?.distinct()
        if (list.isNullOrEmpty()) {
            finish()
            return
        }
        keywords = list
        radius = intent.getIntExtra(EXTRA_RADIUS, 500)
        mode = intent.getStringExtra(EXTRA_MODE) ?: "driving"

        title = keywords.joinToString(", ")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        statusText = findViewById(R.id.statusText)
        setStatus(getString(R.string.finding_location, keywords.joinToString(", "), formatDistance(radius)))

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)

        findViewById<Button>(R.id.recenterButton).setOnClickListener {
            lastLocation?.let { map.controller.animateTo(GeoPoint(it.latitude, it.longitude)) }
        }

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        if (!::map.isInitialized) return
        map.onResume()
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!::map.isInitialized) return
        map.onPause()
        locationManager.removeUpdates(this)
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        var anyProvider = false
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!locationManager.isProviderEnabled(provider)) continue
            anyProvider = true
            locationManager.requestLocationUpdates(provider, 2000L, 3f, this)
            if (lastLocation == null) {
                locationManager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
            }
        }
        if (!anyProvider) {
            setStatus(getString(R.string.location_off))
        }
    }

    // ---- 위치 ----

    override fun onLocationChanged(location: Location) {
        // GPS 위치가 최근에 있으면 정확도가 낮은 네트워크 위치는 무시
        val prev = lastLocation
        if (prev != null &&
            location.provider == LocationManager.NETWORK_PROVIDER &&
            prev.provider == LocationManager.GPS_PROVIDER &&
            location.time - prev.time < 15_000
        ) return

        lastLocation = location
        val point = GeoPoint(location.latitude, location.longitude)

        // 나침반 센서가 없는 기기는 이동 방향(GPS)으로 화살표를 돌린다
        if (rotationSensor == null && location.hasBearing() && location.speed > 1f) {
            heading = location.bearing
        }

        updateMyMarker(point)
        updateCircle(point)

        if (firstFix) {
            firstFix = false
            zoomToRadius(point)
        }

        val q = lastQueryLocation
        if (!queryRunning &&
            System.currentTimeMillis() >= nextQueryAllowedAt &&
            (q == null || q.distanceTo(location) > REQUERY_METERS)
        ) {
            runQuery(location)
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (hasLocationPermission()) startLocationUpdates()
    }

    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun updateMyMarker(point: GeoPoint) {
        var marker = myMarker
        if (marker == null) {
            marker = Marker(map).apply {
                icon = ContextCompat.getDrawable(this@MapActivity, R.drawable.ic_my_location_arrow)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = getString(R.string.my_location)
                setOnMarkerClickListener { _, _ -> true }
            }
            myMarker = marker
            map.overlays.add(marker)
        }
        marker.position = point
        marker.rotation = heading
        map.invalidate()
    }

    private fun updateCircle(center: GeoPoint) {
        var c = circle
        if (c == null) {
            c = Polygon(map).apply {
                fillPaint.color = Color.argb(35, 26, 115, 232)
                outlinePaint.color = Color.argb(200, 26, 115, 232)
                outlinePaint.strokeWidth = 4f
                setOnClickListener { _, _, _ -> false }
            }
            circle = c
            map.overlays.add(0, c)
        }
        c.setPoints(Polygon.pointsAsCircle(center, radius.toDouble()))
        map.invalidate()
    }

    private fun zoomToRadius(center: GeoPoint) {
        val dLat = radius / 111_320.0
        val dLon = radius / (111_320.0 * cos(Math.toRadians(center.latitude)))
        val box = BoundingBox(
            center.latitude + dLat, center.longitude + dLon,
            center.latitude - dLat, center.longitude - dLon
        )
        map.post { map.zoomToBoundingBox(box, false, 96) }
    }

    // ---- 장소 검색 ----

    private fun runQuery(location: Location) {
        queryRunning = true
        setStatus(getString(R.string.searching, keywords.joinToString(", "), formatDistance(radius)))
        executor.execute {
            // 키워드별로 차례대로 검색. 실패한 키워드는 null
            val results = LinkedHashMap<String, Result<List<Place>>>()
            for (k in keywords) {
                results[k] = runCatching {
                    PlaceSearch.search(k, location.latitude, location.longitude, radius)
                }
            }
            runOnUiThread {
                queryRunning = false
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (results.values.any { it.isFailure }) {
                    // 일부라도 실패하면 잠시 뒤 자동으로 다시 시도
                    nextQueryAllowedAt = System.currentTimeMillis() + RETRY_DELAY_MS
                    statusText.postDelayed({
                        if (!isFinishing && !queryRunning) {
                            lastLocation?.let { runQuery(it) }
                        }
                    }, RETRY_DELAY_MS)
                } else {
                    lastQueryLocation = location
                }

                if (results.values.all { it.isFailure }) {
                    val reason = results.values.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
                    setStatus(getString(R.string.search_failed) + (reason?.let { "\n($it)" } ?: ""))
                } else {
                    showPlaces(results, location)
                }
            }
        }
    }

    private fun showPlaces(results: Map<String, Result<List<Place>>>, from: Location) {
        map.overlays.removeAll(poiMarkers.toSet())
        poiMarkers.clear()

        // 현재 위치 마커 아래에 깔리도록 그 앞에 삽입
        val insertAt = myMarker?.let { map.overlays.indexOf(it) }?.takeIf { it >= 0 } ?: map.overlays.size

        val summary = SpannableStringBuilder(getString(R.string.radius_header, formatDistance(radius)))
        keywords.forEachIndexed { index, keyword ->
            val color = COLORS[index % COLORS.size]
            val outcome = results[keyword]
            val places = outcome?.getOrNull()

            val found = places?.map { p ->
                val d = FloatArray(1)
                Location.distanceBetween(from.latitude, from.longitude, p.lat, p.lon, d)
                p to d[0].toInt()
            }?.filter { it.second <= radius }?.sortedBy { it.second }

            found?.forEach { (place, dist) ->
                val pin = ContextCompat.getDrawable(this, R.drawable.ic_poi_pin)?.mutate()
                pin?.setTint(color)
                val marker = Marker(map).apply {
                    position = GeoPoint(place.lat, place.lon)
                    title = place.name
                    icon = pin
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        showPlaceDialog(keyword, place, dist)
                        true
                    }
                }
                poiMarkers.add(marker)
                map.overlays.add(insertAt, marker)
            }

            // 상단 요약: ● 키워드 · N곳 (● 색 = 지도 마커 색)
            summary.append("\n")
            val start = summary.length
            summary.append("●")
            summary.setSpan(
                ForegroundColorSpan(color), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            summary.append(" ").append(keyword).append(" · ")
            summary.append(
                if (found == null) getString(R.string.failed_reason, outcome?.exceptionOrNull()?.message ?: "?")
                else getString(R.string.count_places, found.size)
            )
        }
        map.invalidate()
        statusText.text = summary
    }

    private fun showPlaceDialog(keyword: String, place: Place, distance: Int) {
        AlertDialog.Builder(this)
            .setTitle(place.name)
            .setMessage(getString(R.string.place_dialog_message, keyword, formatDistance(distance)))
            .setPositiveButton(R.string.directions) { _, _ ->
                Directions.open(this, "${place.lat},${place.lon}", mode)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    private fun formatDistance(meters: Int): String =
        if (meters >= 1000) "%.1fkm".format(meters / 1000.0) else "${meters}m"

    // ---- 방향(나침반) ----

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        SensorManager.getOrientation(matrix, orientation)

        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        // 자북 → 진북 보정
        lastLocation?.let {
            val field = GeomagneticField(
                it.latitude.toFloat(), it.longitude.toFloat(),
                it.altitude.toFloat(), System.currentTimeMillis()
            )
            azimuth += field.declination
        }

        var diff = ((azimuth - heading) % 360f + 540f) % 360f - 180f
        if (abs(diff) < 2f) return // 미세한 흔들림은 무시
        diff *= 0.35f // 부드럽게 따라가도록 보간
        heading = (heading + diff + 360f) % 360f

        myMarker?.let {
            it.rotation = heading
            map.invalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val EXTRA_KEYWORDS = "keywords"
        const val EXTRA_RADIUS = "radius"
        const val EXTRA_MODE = "mode"

        private const val REQUERY_METERS = 50f
        private const val RETRY_DELAY_MS = 10_000L

        // 키워드별 마커 색 (최대 6개 키워드 구분)
        private val COLORS = intArrayOf(
            Color.parseColor("#E53935"), // 빨강
            Color.parseColor("#1E88E5"), // 파랑
            Color.parseColor("#43A047"), // 초록
            Color.parseColor("#FB8C00"), // 주황
            Color.parseColor("#8E24AA"), // 보라
            Color.parseColor("#00ACC1")  // 청록
        )
    }
}
