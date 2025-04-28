package com.example.pillware.ui.locations

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pillware.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class LocationsFragment : Fragment(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var lastKnownLocation: Location? = null
    private val DEFAULT_ZOOM = 15f
    private val LOCATION_PERMISSION_REQUEST_CODE = 123

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_locations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
            getDeviceLocationAndNearbyPharmacies()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocationAndNearbyPharmacies() {
        fusedLocationClient.lastLocation.addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful && task.result != null) {
                lastKnownLocation = task.result
                val currentLatLng = LatLng(task.result.latitude, task.result.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, DEFAULT_ZOOM))
                findNearbyPharmacies(currentLatLng)
            } else {
                Log.d(TAG, "Current location is null. Using defaults.")
                val defaultLocation = LatLng(20.6468, -100.4548)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, DEFAULT_ZOOM))
                googleMap.uiSettings.isMyLocationButtonEnabled = false
            }
        }
    }

    private fun findNearbyPharmacies(location: LatLng) {
        val url =
            "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                    "location=${location.latitude},${location.longitude}" +
                    "&radius=1500" +
                    "&keyword=farmacia" +
                    "&key=${getString(R.string.google_maps_key)}"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Places API search failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { responseBody ->
                    val responseData = responseBody.string()

                    try {
                        val jsonObject = JSONObject(responseData)
                        val results = jsonObject.getJSONArray("results")

                        for (i in 0 until results.length()) {
                            val place = results.getJSONObject(i)
                            val name = place.getString("name")

                            if (name.contains("veterinaria", ignoreCase = true)) continue

                            val geometry = place.getJSONObject("geometry")
                            val locationObj = geometry.getJSONObject("location")
                            val lat = locationObj.getDouble("lat")
                            val lng = locationObj.getDouble("lng")
                            val placeLatLng = LatLng(lat, lng)

                            fetchPharmacyLogo(name) { bitmapDescriptor ->
                                requireActivity().runOnUiThread {
                                    googleMap.addMarker(
                                        MarkerOptions()
                                            .position(placeLatLng)
                                            .title(name)
                                            .icon(bitmapDescriptor)
                                    )
                                }
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error parsing Places API response: ${e.message}")
                    }
                }
            }
        })
    }

    private fun fetchPharmacyLogo(pharmacyName: String, callback: (BitmapDescriptor) -> Unit) {
        firestore.collection("farmacias")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val dbName = document.getString("nombre") ?: continue
                    if (pharmacyName.contains(dbName, ignoreCase = true)) {
                        val logoUrl = document.getString("logo") ?: break
                        val storageRef = storage.getReferenceFromUrl(logoUrl)
                        storageRef.getBytes(1024 * 1024)
                            .addOnSuccessListener { bytes ->
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                val markerBitmap = getCustomMarkerBitmap(bitmap)
                                callback(markerBitmap)
                            }
                            .addOnFailureListener { exception ->
                                Log.e(TAG, "Error loading logo from Firebase Storage: ${exception.message}")
                                callback(getDefaultMarkerBitmap())
                            }
                        return@addOnSuccessListener
                    }
                }
                callback(getDefaultMarkerBitmap())
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error fetching pharmacy logo: ${exception.message}")
                callback(getDefaultMarkerBitmap())
            }
    }

    private fun getCustomMarkerBitmap(logoBitmap: Bitmap): BitmapDescriptor {
        val customMarkerView = LayoutInflater.from(requireContext()).inflate(R.layout.pharmacy_marker, null)
        val logoImageView = customMarkerView.findViewById<ImageView>(R.id.logo)
        logoImageView.setImageBitmap(logoBitmap)

        customMarkerView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        customMarkerView.layout(0, 0, customMarkerView.measuredWidth, customMarkerView.measuredHeight)

        val bitmap = Bitmap.createBitmap(
            customMarkerView.measuredWidth,
            customMarkerView.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        customMarkerView.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun getDefaultMarkerBitmap(): BitmapDescriptor {
        return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::googleMap.isInitialized) {
                try {
                    googleMap.isMyLocationEnabled = true
                    getDeviceLocationAndNearbyPharmacies()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Location permission was granted but still had a SecurityException: ${e.message}")
                }
            }
        } else {
            Log.i(TAG, "Location permissions denied.")
            val defaultLocation = LatLng(20.6468, -100.4548)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, DEFAULT_ZOOM))
        }
    }

    companion object {
        private const val TAG = "LocationsFragment"
    }
}
