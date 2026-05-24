package moe.fuqiuluo.portal.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapOptions
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.MainActivity
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.bdmap.locateMe
import moe.fuqiuluo.portal.bdmap.setMapConfig
import moe.fuqiuluo.portal.databinding.FragmentHomeBinding
import moe.fuqiuluo.portal.ext.gcj02
import moe.fuqiuluo.portal.ext.mapType
import moe.fuqiuluo.portal.ext.rawHistoricalLocations
import moe.fuqiuluo.portal.ext.selectRoute
import moe.fuqiuluo.portal.ext.wgs84
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.viewmodel.BaiduMapViewModel
import moe.fuqiuluo.portal.ui.viewmodel.HomeViewModel
import java.math.BigDecimal
import kotlin.random.Random

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val homeViewModel by viewModels<HomeViewModel>()
    private lateinit var mLocationClient: AMapLocationClient
    private val baiduMapViewModel by activityViewModels<BaiduMapViewModel>()
    private var moveToCurrentLocationOnNextFix = false

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        binding.bmapView.onCreate(savedInstanceState)

        // Fixed the issue that the Fab was opening incorrectly after switching back to Home for Fragments
        homeViewModel.mFabOpened = false

        with(baiduMapViewModel) {
            isExists = true
            baiduMap = binding.bmapView.map
        }

        with(binding.bmapView) {
            map.uiSettings.setZoomControlsEnabled(true)
            map.uiSettings.setScaleControlsEnabled(true)
            map.uiSettings.setLogoPosition(AMapOptions.LOGO_POSITION_BOTTOM_RIGHT)
        }

        with(binding.bmapView.map) {
            moveCamera(CameraUpdateFactory.zoomTo(19f))

            mapType = context?.mapType ?: AMap.MAP_TYPE_NORMAL
            uiSettings.setCompassEnabled(true)
            uiSettings.setTiltGesturesEnabled(true)
            setMyLocationEnabled(true)

            setMapConfig(
                baiduMapViewModel.perspectiveState,
                if (Random.nextBoolean()) R.drawable.icon_my_location else null
            )

            setOnMapClickListener { loc ->
                // 默认获取的gcj02坐标，需要转换一下
                baiduMapViewModel.markedLoc = loc.wgs84

                lifecycleScope.launch {
                    baiduMapViewModel.showDetailView = false
                    reverseGeocode(loc)
                }

                // Fixed the issue that getting geolocation information was stuck
                lifecycleScope.launch {
                    markMap()
                }
            }

            setOnMapLongClickListener { loc ->
                // 默认获取的gcj02坐标，需要转换一下
                baiduMapViewModel.markedLoc = loc.wgs84
                lifecycleScope.launch {
                    baiduMapViewModel.showDetailView = true
                    reverseGeocode(loc)
                }
                lifecycleScope.launch {
                    markMap()
                }
            }

            binding.mapTypeGroup.check(
                when (mapType) {
                    AMap.MAP_TYPE_NORMAL -> R.id.map_type_normal
                    AMap.MAP_TYPE_SATELLITE -> R.id.map_type_satellite
                    else -> R.id.map_type_normal
                }
            )
        }

        mLocationClient = AMapLocationClient(requireContext())
        val option = AMapLocationClientOption()
            .setGpsFirst(true)
            .setMockEnable(true)
            .setNeedAddress(true)
            .setSensorEnable(true)
            .setOffset(true)
            .setInterval(1000L)
            .setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy)
        mLocationClient.setLocationOption(option)
        mLocationClient.setLocationListener { loc ->
            if (loc == null) return@setLocationListener
            if (loc.errorCode != AMapLocation.LOCATION_SUCCESS) {
                Log.e(
                    "HomeFragment",
                    "AMap location error: code=${loc.errorCode}, info=${loc.errorInfo}, " +
                            "detail=${loc.locationDetail}, type=${loc.locationType}, provider=${loc.provider}"
                )
                if (moveToCurrentLocationOnNextFix) {
                    moveToCurrentLocationOnNextFix = false
                    val context = context ?: return@setLocationListener
                    Toast.makeText(
                        context,
                        "定位失败: ${loc.errorInfo ?: loc.errorCode}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@setLocationListener
            }

            if (loc.city != null)
                MainActivity.mCityString = loc.city

            with(baiduMapViewModel) {
                currentLocation = loc.wgs84
                val mapLocation = LatLng(loc.latitude, loc.longitude)
                baiduMap.setCurrentLocation(mapLocation)
                if (moveToCurrentLocationOnNextFix) {
                    moveToCurrentLocationOnNextFix = false
                    baiduMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mapLocation, 19f))
                }
            }
        }
        baiduMapViewModel.mLocationClient = mLocationClient
        mLocationClient.startLocation()


        binding.mapTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.map_type_normal -> {
                    binding.bmapView.map.mapType = AMap.MAP_TYPE_NORMAL
                }

                R.id.map_type_satellite -> {
                    binding.bmapView.map.mapType = AMap.MAP_TYPE_SATELLITE
                }

                else -> {
                    Log.e("HomeFragment", "Unknown location view mode: $checkedId")
                }
            }
            context?.mapType = binding.bmapView.map.mapType
        }

        binding.fab.setOnClickListener { view ->
            val subFabList = arrayOf(
                binding.fabMyLocation,
                binding.fabGoto,
                binding.fabAdd
            )

            if (!homeViewModel.mFabOpened) {
                homeViewModel.mFabOpened = true

                val rotateMainFab = ObjectAnimator.ofFloat(view, "rotation", 0f, 90f)
                rotateMainFab.duration = 200

                val animators = arrayListOf<ObjectAnimator>()
                animators.add(rotateMainFab)
                subFabList.forEachIndexed { index, fab ->
                    fab.visibility = View.VISIBLE
                    fab.alpha = 1f
                    fab.scaleX = 1f
                    fab.scaleY = 1f
                    val translationX =
                        ObjectAnimator.ofFloat(fab, "translationX", 0f, 20f + index * 8f)
                    translationX.duration = 200
                    animators.add(translationX)
                }

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animators.toList())
                animatorSet.interpolator = DecelerateInterpolator()
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.isClickable = true
                    }
                })
                view.isClickable = false
                animatorSet.start()
            } else {
                homeViewModel.mFabOpened = false

                val rotateMainFab = ObjectAnimator.ofFloat(view, "rotation", 90f, 0f)
                rotateMainFab.duration = 200

                val animators = arrayListOf<ObjectAnimator>()
                animators.add(rotateMainFab)
                subFabList.forEachIndexed { index, fab ->
                    val transX = ObjectAnimator.ofFloat(fab, "translationX", 0f, -20f - index * 8f)
                    transX.duration = 150
                    val scaleX = ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0f)
                    scaleX.duration = 200
                    val scaleY = ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0f)
                    scaleY.duration = 200
                    val alpha = ObjectAnimator.ofFloat(fab, "alpha", 1f, 0f)
                    alpha.duration = 200
                    animators.add(transX)
                    animators.add(scaleX)
                    animators.add(scaleY)
                    animators.add(alpha)
                }

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animators.toList())
                animatorSet.interpolator = DecelerateInterpolator()
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        subFabList.forEach { it.visibility = View.GONE }
                        view.isClickable = true
                    }
                })
                view.isClickable = false
                animatorSet.start()
            }
        }

        binding.fabMyLocation.setOnClickListener {
            locateCurrentPosition()
        }

        binding.fabGoto.setOnClickListener {
            showInputCoordinatesDialog()
        }

        binding.fabAdd.setOnClickListener {
            if (!showAddLocationDialog()) {
                Toast.makeText(requireContext(), "选择位置异常", Toast.LENGTH_SHORT).show()
            }
        }

        binding.showRoute.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requireContext().selectRoute?.route?.let {
                    previewRoute(it)
                    // 选中路线后，将视角移动到起点
                    baiduMapViewModel.baiduMap.moveCamera(
                        CameraUpdateFactory.newLatLng(it.first().gcj02)
                    )
                }
            } else {
                baiduMapViewModel.baiduMap.clear()
            }
        }

        return root
    }

    private fun previewRoute(points: kotlin.collections.List<Pair<Double, Double>>) {
        baiduMapViewModel.baiduMap.clear() // 清除之前的所有覆盖物

        // 绘制之前记录的点到点的线
        for (i in 0 until points.size - 1) {
            baiduMapViewModel.baiduMap.addPolyline(
                PolylineOptions()
                    .color(Color.argb(178, 0, 78, 255))
                    .width(10f)
                    .addAll(listOf(points[i].gcj02, points[i + 1].gcj02))
            )
        }
    }

    private fun locateCurrentPosition() {
        baiduMapViewModel.baiduMap.locateMe()
        baiduMapViewModel.currentLocation?.let {
            baiduMapViewModel.baiduMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(it.gcj02, 19f)
            )
            return
        }

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            if (!MockServiceHelper.isServiceInit()) {
                MockServiceHelper.tryInitService(locationManager)
            }
            MockServiceHelper.getLocation(locationManager)
                ?.takeIf { (lat, lon) -> lat != 0.0 || lon != 0.0 }
                ?.let {
                    baiduMapViewModel.currentLocation = it
                    baiduMapViewModel.baiduMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(it.gcj02, 19f)
                    )
                    return
                }
        }

        moveToCurrentLocationOnNextFix = true
        if (this::mLocationClient.isInitialized) {
            mLocationClient.startLocation()
        }
    }

    @SuppressLint("SetTextI18n", "MissingInflatedId", "MutatingSharedPrefs")
    private fun showAddLocationDialog(): Boolean {
        fun checkLatLon(lat: Double?, lon: Double?): Boolean {
            return (lat != null && lon != null) && lat in -90.0..90.0 && lon in -180.0..180.0
        }

        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_add_location, null)
        val editName = dialogView.findViewById<TextInputEditText>(R.id.etLocationName)
        editName.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editName.error = "名称不能为空"
            }
        }
        val editAddress = dialogView.findViewById<TextInputEditText>(R.id.etLocationAddress)
        editAddress.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editAddress.error = "地址不能为空"
            }
        }
        val editLatLon = dialogView.findViewById<TextInputEditText>(R.id.etLocationLatLon)
        editLatLon.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editLatLon.error = "经纬度不能为空"
            } else {
                val latLonArray = it.toString().split(",")
                if (latLonArray.size != 2) {
                    editLatLon.error = "经纬度格式错误"
                } else {
                    val lon = latLonArray[0].trim().toDoubleOrNull()
                    val lat = latLonArray[1].trim().toDoubleOrNull()
                    if (!checkLatLon(lat, lon)) {
                        editLatLon.error = "经纬度格式错误"
                    }
                }
            }
        }

        with(baiduMapViewModel) {
            if (markedLoc == null) {
                currentLocation?.let {
                    showDetailView = false
                    reverseGeocode(it.gcj02)
                    markedLoc = it
                }
                editName.setText("当前位置-" + System.currentTimeMillis())
            } else {
                editName.setText("标点位置-" + System.currentTimeMillis())
            }

            val lat = BigDecimal.valueOf(markedLoc?.first ?: return false)
            val lon = BigDecimal.valueOf(markedLoc?.second ?: return false)

            editAddress.setText(markName ?: "位置地址")
            editLatLon.setText("${lon.toPlainString()}, ${lat.toPlainString()}")

            val builder = MaterialAlertDialogBuilder(requireContext())
            builder.setTitle(null)
            builder
                .setCancelable(false)
                .setView(dialogView)
                .setPositiveButton("保存") { _, _ ->
                    val latLonArray = editLatLon.text.toString().split(",")
                    val newLon = latLonArray[0].trim().toDoubleOrNull()
                    val newLat = latLonArray[1].trim().toDoubleOrNull()
                    var name = editName.text?.toString()
                    val address = editAddress.text?.toString()
                    if (name.isNullOrBlank()) {
                        Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (address.isNullOrBlank()) {
                        Toast.makeText(requireContext(), "地址不能为空", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (!checkLatLon(newLat, newLon)) {
                        Toast.makeText(requireContext(), "经纬度格式错误", Toast.LENGTH_SHORT)
                            .show()
                        return@setPositiveButton
                    }

                    fun MutableSet<String>.addLocation(
                        name: String,
                        address: String,
                        lat: Double,
                        lon: Double
                    ): Boolean {
                        if (any { it.split(",")[0] == name }) {
                            return false
                        }
                        add(
                            "$name,$address,${
                                BigDecimal.valueOf(lat).toPlainString()
                            },${BigDecimal.valueOf(lon).toPlainString()}"
                        )
                        return true
                    }

                    with(requireContext()) {
                        val locations = rawHistoricalLocations.toMutableSet()
                        var count = 0
                        while (!locations.addLocation(name!!, address, newLat!!, newLon!!)) {
                            name = "$name(${++count})"
                        }
                        rawHistoricalLocations = locations
                    }

                    Toast.makeText(requireContext(), "位置已保存", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        return true
    }

    @SuppressLint("MissingInflatedId")
    private fun showInputCoordinatesDialog() {
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_coordinates, null)

        val latitudeEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextLatitude)
        val longitudeEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextLongitude)

        baiduMapViewModel.currentLocation?.let {
            latitudeEditText.setText(BigDecimal.valueOf(it.first).toPlainString())
            longitudeEditText.setText(BigDecimal.valueOf(it.second).toPlainString())
        }

        latitudeEditText.addTextChangedListener {
            if (it.isNullOrBlank()) {
                latitudeEditText.error = "纬度不能为空"
            } else {
                val lat = it.toString().toDoubleOrNull()
                if (lat == null || lat !in -90.0..90.0) {
                    latitudeEditText.error = "纬度格式错误"
                }
            }
        }

        longitudeEditText.addTextChangedListener {
            if (it.isNullOrBlank()) {
                longitudeEditText.error = "经度不能为空"
            } else {
                val lon = it.toString().toDoubleOrNull()
                if (lon == null || lon !in -180.0..180.0) {
                    longitudeEditText.error = "经度格式错误"
                }
            }
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle("输入经纬度(WGS84)")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                kotlin.runCatching {
                    val latitude = latitudeEditText.text.toString()
                    val longitude = longitudeEditText.text.toString()

                    if (latitude.isNotEmpty() && longitude.isNotEmpty()) with(baiduMapViewModel) {
                        val lat = latitude.toDoubleOrNull()
                        val lon = longitude.toDoubleOrNull()
                        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                            throw IllegalArgumentException("Invalid latitude or longitude")
                        }

                        this.markedLoc = lat to lon

                        markMap(true)

                        if (perspectiveState == MyLocationStyle.LOCATION_TYPE_FOLLOW) {
                            perspectiveState = MyLocationStyle.LOCATION_TYPE_SHOW
                        }

                        reverseGeocode((lat to lon).gcj02)
                    } else {
                        Toast.makeText(requireContext(), "请输入有效的经纬度！", Toast.LENGTH_SHORT)
                            .show()
                    }
                }.onFailure {
                    Toast.makeText(requireContext(), "请输入有效的经纬度！", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun markMap(moveEyes: Boolean = false) = with(baiduMapViewModel) {
        val loc = markedLoc!!.gcj02
        val ooA = MarkerOptions()
            .position(loc)
            .icon(mMapIndicator)
        baiduMap.clear()
        baiduMap.addMarker(ooA)

        if (moveEyes) {
            baiduMap.moveCamera(CameraUpdateFactory.newLatLng(loc))
        }
    }

    private fun reverseGeocode(loc: LatLng) {
        baiduMapViewModel.mGeoCoder?.getFromLocationAsyn(
            RegeocodeQuery(
                LatLonPoint(loc.latitude, loc.longitude),
                200f,
                GeocodeSearch.AMAP
            )
        )
    }

    override fun onResume() {
        super.onResume()

        if (_binding != null)
            binding.bmapView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()

        if (_binding != null) {
            binding.bmapView.onPause()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (_binding != null) {
            // Switching fragments causes the _binding to be empty, and the method is inexplicably triggered,
            // which does not conform to the google's lifecycle diagram representation
            binding.bmapView.onSaveInstanceState(outState)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        baiduMapViewModel.isExists = false
        if (this::mLocationClient.isInitialized) {
            if (mLocationClient.isStarted)
                mLocationClient.stopLocation()
            mLocationClient.onDestroy()
        }
        binding.bmapView.map.setMyLocationEnabled(false)
        binding.bmapView.onDestroy()
        _binding = null
    }
}
