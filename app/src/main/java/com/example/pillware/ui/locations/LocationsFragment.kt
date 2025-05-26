package com.example.pillware.ui.locations

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import java.util.Locale
import java.util.Calendar
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.pillware.R
import com.example.pillware.databinding.FragmentLocationsBinding
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import android.graphics.drawable.Drawable
import android.content.Intent
import android.net.Uri
import com.google.android.gms.maps.model.Marker

// No es necesario importar OpeningHours de Places API si no la estás usando directamente
// import com.google.android.libraries.places.api.model.OpeningHours

class LocationsFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentLocationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null
    private val DEFAULT_ZOOM = 15f
    private val LOCATION_PERMISSION_REQUEST_CODE = 123
    private val firestore = FirebaseFirestore.getInstance()
    private val pharmacyLogosCache = mutableMapOf<String, String?>() // Cache para los logos
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private lateinit var bottomSheet: View // Declarar la vista del bottom sheet
    private var selectedMarker: Marker? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationsBinding.inflate(inflater, container, false)
        bottomSheet = binding.bottomSheet // Inicializar la vista del bottom sheet aquí
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            peekHeight = 1
            state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocationAndNearbyPharmacies() {
        // Verificar si el fragmento sigue adjunto antes de usar requireActivity()
        if (!isAdded) {
            Log.w(TAG, "Fragment not added, skipping getDeviceLocationAndNearbyPharmacies.")
            return
        }

        try {
            val locationResult = fusedLocationClient.lastLocation
            locationResult.addOnCompleteListener(requireActivity()) { task ->
                // Verificar si el fragmento sigue adjunto antes de interactuar con la UI
                if (!isAdded) {
                    Log.w(TAG, "Fragment not added after location task, skipping UI update.")
                    return@addOnCompleteListener
                }

                if (task.isSuccessful) {
                    lastKnownLocation = task.result
                    if (lastKnownLocation != null) {
                        val currentLatLng = LatLng(
                            lastKnownLocation!!.latitude,
                            lastKnownLocation!!.longitude
                        )
                        googleMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                currentLatLng,
                                DEFAULT_ZOOM
                            )
                        )
                        findNearbyPharmacies(currentLatLng)
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.")
                        val juriquilla = LatLng(20.6468, -100.4548)
                        googleMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                juriquilla,
                                DEFAULT_ZOOM
                            )
                        )
                        googleMap.uiSettings.isMyLocationButtonEnabled = false
                    }
                } else {
                    Log.d(TAG, "Failed to get current location.")
                    Log.e(TAG, "Exception: %s", task.exception)
                    val juriquilla = LatLng(20.6468, -100.4548)
                    googleMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            juriquilla,
                            DEFAULT_ZOOM
                        )
                    )
                    googleMap.uiSettings.isMyLocationButtonEnabled = false
                }
            }
        } catch (e: SecurityException) {
            Log.e("Location", "Location permission not granted: " + e.message)
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

        val request = Request.Builder()
            .url(url)
            .build()

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
                            val pharmacyNameFromMaps = place.getString("name").toLowerCase(Locale.ROOT) // Usar Locale.ROOT
                            // En Android es mejor usar toLowerCase(Locale.getDefault()) si el contexto es una UI que cambia con el locale del usuario
                            // pero para una API externa, ROOT es más seguro.

                            if (pharmacyNameFromMaps.contains("veterinaria") || pharmacyNameFromMaps.lowercase(Locale.ROOT)
                                    .contains("acupuntura") || pharmacyNameFromMaps.lowercase(Locale.ROOT)
                                    .contains("dermatológica")
                            ) {
                                continue
                            }

                            val geometry = place.getJSONObject("geometry")
                            val locationObj = geometry.getJSONObject("location")
                            val lat = locationObj.getDouble("lat")
                            val lng = locationObj.getDouble("lng")
                            val placeLatLng = LatLng(lat, lng)
                            val placeId = place.getString("place_id")

                            // Buscar el logo en Firestore
                            findPharmacyLogo(pharmacyNameFromMaps) { logoUrl ->
                                // Verificar si el fragmento sigue adjunto antes de llamar a getCustomMarkerBitmap
                                if (!isAdded) {
                                    Log.w(TAG, "Fragment not added, skipping getCustomMarkerBitmap call from findPharmacyLogo.")
                                    return@findPharmacyLogo
                                }
                                getCustomMarkerBitmap(logoUrl) { bitmapDescriptor ->
                                    // Verificar si el fragmento sigue adjunto antes de interactuar con el mapa en el hilo principal
                                    if (isAdded) { // Usar isAdded aquí también
                                        requireActivity().runOnUiThread {
                                            val marker = googleMap.addMarker(
                                                MarkerOptions()
                                                    .position(placeLatLng)
                                                    .title(place.getString("name"))
                                                    .icon(bitmapDescriptor)
                                            )
                                            marker?.tag = placeId
                                        }
                                    } else {
                                        Log.w(TAG, "Fragment not added, skipping adding marker.")
                                    }
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

    private fun fetchPlaceDetails(placeId: String, callback: (Double?, String?) -> Unit) {
        val url = "https://maps.googleapis.com/maps/api/place/details/json?" +
                "place_id=${placeId}" +
                "&fields=rating,opening_hours" +
                "&key=${getString(R.string.google_maps_key)}"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Places Details API failed for ID: $placeId, error: ${e.message}")
                // Verificar si el fragmento sigue adjunto antes de llamar al callback de UI
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        callback(null, null)
                        onSelectedPharmacy(null, null, null) // Pasar null para borrar info si hubo error
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { responseBody ->
                    val responseData = responseBody.string()
                    try {
                        val jsonObject = JSONObject(responseData)
                        if (jsonObject.getString("status") == "OK") {
                            val result = jsonObject.getJSONObject("result")
                            val pharmacyName = result.optString("name")
                            val rating = result.optDouble("rating").takeIf { it != 0.0 }
                            val openingHoursObject = result.optJSONObject("opening_hours")
                            var todayOpeningHours: String? = null

                            if (openingHoursObject != null && openingHoursObject.has("weekday_text")) {
                                val weekdayTextArray = openingHoursObject.getJSONArray("weekday_text")
                                val calendar = Calendar.getInstance()
                                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

                                // El array weekday_text comienza con Domingo en la posición 0 (según la documentación)
                                val index = when (dayOfWeek) {
                                    Calendar.SUNDAY -> 0
                                    Calendar.MONDAY -> 1
                                    Calendar.TUESDAY -> 2
                                    Calendar.WEDNESDAY -> 3
                                    Calendar.THURSDAY -> 4
                                    Calendar.FRIDAY -> 5
                                    Calendar.SATURDAY -> 6
                                    else -> -1
                                }

                                if (index in 0 until weekdayTextArray.length()) {
                                    todayOpeningHours = weekdayTextArray.getString(index)
                                    // Extraer solo el horario (ej: "8:00 AM – 9:00 PM")
                                    val parts = todayOpeningHours.split(": ")
                                    if (parts.size > 1) {
                                        todayOpeningHours = parts[1]
                                    }
                                }
                            }

                            // Ejecutar la actualización de la UI en el hilo principal
                            // Verificar si el fragmento sigue adjunto antes de llamar a runOnUiThread
                            if (isAdded) {
                                requireActivity().runOnUiThread {
                                    Log.d(TAG, "Nombre de farmacia obtenido: $pharmacyName")
                                    callback(rating, todayOpeningHours)
                                    onSelectedPharmacy(pharmacyName, rating, todayOpeningHours)
                                }
                            } else {
                                Log.w(TAG, "Fragment not added, skipping UI update for place details.")
                            }
                        } else {
                            Log.w(TAG, "Places Details API status not OK for ID: $placeId, status: ${jsonObject.getString("status")}")
                            // Verificar si el fragmento sigue adjunto antes de llamar al callback de UI
                            if (isAdded) {
                                requireActivity().runOnUiThread {
                                    callback(null, null)
                                    onSelectedPharmacy(null, null, null) // Pasar null para borrar info si hubo error
                                }
                            } else {
                                Log.w(TAG, "Fragment not added, skipping UI update for place details.")
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error parsing Places Details API response for ID: $placeId, error: ${e.message}")
                        // Verificar si el fragmento sigue adjunto antes de llamar al callback de UI
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                callback(null, null)
                                onSelectedPharmacy(null, null, null) // Pasar null para borrar info si hubo error
                            }
                        } else {
                            Log.w(TAG, "Fragment not added, skipping UI update for place details.")
                        }
                    }
                }
            }
        })
    }
    private fun findPharmacyLogo(pharmacyName: String, callback: (String?) -> Unit) {
        // Verificar primero en la caché
        pharmacyLogosCache[pharmacyName]?.let {
            callback(it)
            return
        }

        firestore.collection("Farmacia")
            .get()
            .addOnSuccessListener { querySnapshot ->
                var foundLogoUrl: String? = null
                for (document in querySnapshot) {
                    val nombreEnDb = document.getString("nombre")?.lowercase(Locale.getDefault())
                    val logoUrl = document.getString("logo")

                    if (!nombreEnDb.isNullOrEmpty() && pharmacyName.contains(nombreEnDb)) {
                        foundLogoUrl = logoUrl
                        break // Encontramos una coincidencia, podemos detener la búsqueda
                    }
                }
                pharmacyLogosCache[pharmacyName] = foundLogoUrl // Guardar en caché
                callback(foundLogoUrl)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al buscar logo de farmacia en Firestore: ", exception)
                pharmacyLogosCache[pharmacyName] = null // Guardar null en caché en caso de error
                callback(null)
            }
    }

    private fun getCustomMarkerBitmap(logoUrl: String?, callback: (BitmapDescriptor) -> Unit) {
        // Verificar si el fragmento sigue adjunto antes de inflar la vista y usar el contexto
        if (context == null) { // Usar 'context' directamente es mejor que 'requireContext()' si puede ser nulo.
            Log.w(TAG, "Context is null, cannot create custom marker bitmap.")
            // Asegurarse de devolver un marcador por defecto si el contexto no está disponible
            callback(BitmapDescriptorFactory.defaultMarker())
            return
        }

        val customMarkerView = LayoutInflater.from(context).inflate(R.layout.pharmacy_marker, null)
        val pharmacyLogoImageView = customMarkerView.findViewById<ImageView>(R.id.logo)

        if (!logoUrl.isNullOrEmpty()) {
            Glide.with(requireContext()) // Usar 'context'
                .load(logoUrl)
                .addListener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e(TAG, "Glide load failed for URL: $logoUrl", e)
                        // Llamar al callback con un marcador por defecto en caso de fallo
                        // Verificar si el fragmento sigue adjunto antes de llamar al callback
                        if (context != null) {
                            callback(getDefaultMarkerBitmap())
                        } else {
                            Log.w(TAG, "Context is null, cannot provide default marker after Glide fail.")
                        }
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        // Dibujar el Drawable en la vista y obtener el BitmapDescriptor
                        // Verificar si el fragmento sigue adjunto antes de usar el contexto
                        if (context != null) {
                            pharmacyLogoImageView.setImageDrawable(resource)
                            val bitmapDescriptor = createBitmapDescriptorFromView(customMarkerView)
                            callback(bitmapDescriptor)
                        } else {
                            Log.w(TAG, "Context is null, cannot create bitmap descriptor from resource.")
                        }
                        return false
                    }
                })
                .into(pharmacyLogoImageView)
        } else {
            // Llamar al callback inmediatamente con el marcador por defecto
            // Verificar si el fragmento sigue adjunto antes de llamar al callback
            if (context != null) {
                callback(getDefaultMarkerBitmap())
            } else {
                Log.w(TAG, "Context is null, cannot provide default marker.")
            }
        }
    }

    private fun createBitmapDescriptorFromView(view: View): BitmapDescriptor {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(
            view.measuredWidth,
            view.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun getDefaultMarkerBitmap(): BitmapDescriptor {
        // Verificar si el fragmento sigue adjunto antes de inflar la vista y usar el contexto
        if (context == null) {
            Log.w(TAG, "Context is null, cannot create default marker bitmap.")
            return BitmapDescriptorFactory.defaultMarker() // Retornar un marcador genérico de Google Maps
        }
        val customMarkerView = LayoutInflater.from(context).inflate(R.layout.pharmacy_marker, null)
        val pharmacyLogoImageView = customMarkerView.findViewById<ImageView>(R.id.logo)
        pharmacyLogoImageView.setImageResource(R.drawable.pharmacy)
        return createBitmapDescriptorFromView(customMarkerView)
    }

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map

        // Verificar si el fragmento sigue adjunto antes de usar requireContext() y requireActivity()
        if (!isAdded) {
            Log.w(TAG, "Fragment not added, skipping map setup.")
            return
        }

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
        // Aquí se establece el listener para los clicks en los marcadores
        googleMap.setOnMarkerClickListener { marker ->
            val pharmacyName = marker.title
            Log.d(TAG, "Se hizo click en el marcador: ${pharmacyName}")
            val placeId = marker.tag as? String
            selectedMarker = marker // Guarda el marcador seleccionado
            if (!placeId.isNullOrEmpty()) {
                fetchPlaceDetails(placeId) { rating, openingHours ->
                    // La actualización de la UI (llamada a onSelectedPharmacy) ya está dentro de runOnUiThread
                }
            } else {
                // Verificar si el fragmento sigue adjunto antes de llamar a runOnUiThread
                if (isAdded) {
                    requireActivity().runOnUiThread { // Asegurar que onSelectedPharmacy se llama en el hilo principal
                        onSelectedPharmacy(pharmacyName, null, null) // Si no hay placeId, mostrar solo el nombre
                    }
                }
            }
            true
        }
    }

    private fun onSelectedPharmacy(pharmacyName: String?, rating: Double?, openingHours: String?) {
        // Verificar si el fragmento sigue adjunto antes de interactuar con las vistas
        if (!isAdded || _binding == null) {
            Log.w(TAG, "Fragment not added or binding is null, skipping onSelectedPharmacy.")
            return
        }

        BottomSheetBehavior.from(bottomSheet).apply {
            this.state = BottomSheetBehavior.STATE_EXPANDED
        }
        val nombreFarmaciaTextView = binding.bottomSheet.findViewById<TextView>(R.id.nombre_far)
        val ratingTextView = binding.bottomSheet.findViewById<TextView>(R.id.rating)
        val horarioTextView = binding.bottomSheet.findViewById<TextView>(R.id.horario)

        nombreFarmaciaTextView?.text = pharmacyName.takeIf { !it.isNullOrBlank() } ?: selectedMarker?.title ?: "Nombre no disponible"
        ratingTextView?.text = rating?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "Sin rating" // Usar Locale.getDefault()
        horarioTextView?.text = openingHours ?: "Horario no disponible"

        binding.bottomSheet.findViewById<TextView>(R.id.como_llegar)?.setOnClickListener {
            selectedMarker?.position?.let { latLng ->
                openGoogleMapsDirections(lastKnownLocation?.latitude, lastKnownLocation?.longitude, latLng.latitude, latLng.longitude)
            } ?: run {
                Log.w(TAG, "No se pudo obtener la ubicación de la farmacia para la ruta.")
            }
        }
    }

    private fun openGoogleMapsDirections(startLatitude: Double?, startLongitude: Double?, endLatitude: Double, endLongitude: Double) {
        // Verificar si el fragmento sigue adjunto antes de iniciar una actividad
        if (!isAdded) {
            Log.w(TAG, "Fragment not added, skipping openGoogleMapsDirections.")
            return
        }

        if (startLatitude != null && startLongitude != null) {
            val gmmIntentUri = Uri.parse("google.navigation:q=$endLatitude,$endLongitude&dirflg=d") // 'd' para ruta en coche
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        } else {
            // Si no se tiene la ubicación actual, se abre Google Maps solo mostrando el destino
            val gmmIntentUri = Uri.parse("geo:$endLatitude,$endLongitude?q=$endLatitude,$endLongitude")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
            Log.w(TAG, "No se pudo obtener la ubicación actual para iniciar la navegación. Mostrando solo el destino.")
            // Opcional: Mostrar un mensaje al usuario
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        // Verificar si el fragmento sigue adjunto antes de interactuar con googleMap
        if (!isAdded) {
            Log.w(TAG, "Fragment not added, skipping onRequestPermissionsResult logic.")
            return
        }

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (::googleMap.isInitialized) {
                        try {
                            googleMap.isMyLocationEnabled = true
                            getDeviceLocationAndNearbyPharmacies()
                        } catch (e: SecurityException) {
                            Log.e(
                                "Location",
                                "Location permission was granted but still had a SecurityException: " + e.message
                            )
                        }
                    }
                } else {
                    Log.i(TAG, "Location permissions denied.")
                    val juriquilla = LatLng(20.6468, -100.4548)
                    if (::googleMap.isInitialized) { // También verificar si googleMap está inicializado
                        googleMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                juriquilla,
                                DEFAULT_ZOOM
                            )
                        )
                    }
                }
            }

            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "LocationsFragment"
    }
}