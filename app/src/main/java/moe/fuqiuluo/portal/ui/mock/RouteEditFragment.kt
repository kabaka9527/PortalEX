package moe.fuqiuluo.portal.ui.mock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapOptions
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.MainActivity
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.bdmap.Poi
import moe.fuqiuluo.portal.bdmap.locateMe
import moe.fuqiuluo.portal.bdmap.setMapConfig
import moe.fuqiuluo.portal.bdmap.toPoi
import moe.fuqiuluo.portal.databinding.FragmentRouteEditBinding
import moe.fuqiuluo.portal.ext.gcj02
import moe.fuqiuluo.portal.ext.jsonHistoricalRoutes
import moe.fuqiuluo.portal.ext.mapType
import moe.fuqiuluo.portal.ext.wgs84
import moe.fuqiuluo.portal.ui.viewmodel.BaiduMapViewModel
import moe.fuqiuluo.portal.ui.viewmodel.HomeViewModel
import java.math.BigDecimal
import kotlin.random.Random


class RouteEditFragment : Fragment() {
    private var _binding: FragmentRouteEditBinding? = null
    private val binding get() = _binding!!

    private val routeEditViewModel by viewModels<HomeViewModel>()
    private lateinit var mLocationClient: AMapLocationClient
    private var mInputtips: Inputtips? = null
    private val baiduMapViewModel by activityViewModels<BaiduMapViewModel>()

    private var mPoints: ArrayList<Pair<Double, Double>> = arrayListOf()
    private var isDrawing = false
    private var lastPoint: Pair<Double, Double>? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteEditBinding.inflate(inflater, container, false)
        binding.bmapView.onCreate(savedInstanceState)

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
                if (Random.nextBoolean()) moe.fuqiuluo.portal.R.drawable.icon_my_location else null
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
                    AMap.MAP_TYPE_NORMAL -> moe.fuqiuluo.portal.R.id.map_type_normal
                    AMap.MAP_TYPE_SATELLITE -> moe.fuqiuluo.portal.R.id.map_type_satellite
                    else -> moe.fuqiuluo.portal.R.id.map_type_normal
                }
            )
        }

        binding.fab.setOnClickListener { view ->
            val subFabList = arrayOf(
                binding.fabSearch,
                binding.fabStart,
                binding.fabRollback,
                binding.fabComplete,
                binding.fabMyLocation
            )

            if (!routeEditViewModel.mFabOpened) {
                routeEditViewModel.mFabOpened = true

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
                routeEditViewModel.mFabOpened = false

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

        mLocationClient = AMapLocationClient(requireContext())
        val option = AMapLocationClientOption()
            .setGpsFirst(true)
            .setMockEnable(false)
            .setNeedAddress(true)
            .setSensorEnable(true)
            .setOffset(true)
            .setInterval(1000L)
            .setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy)
        mLocationClient.setLocationOption(option)
        mLocationClient.setLocationListener { loc ->
            if (loc == null) return@setLocationListener
            if (loc.errorCode != AMapLocation.LOCATION_SUCCESS) {
                Log.e("RouteEditFragment", "AMap location error: ${loc.errorCode}, ${loc.errorInfo}")
                return@setLocationListener
            }

            if (loc.city != null)
                MainActivity.mCityString = loc.city

            with(baiduMapViewModel) {
                currentLocation = loc.wgs84
                baiduMap.setCurrentLocation(LatLng(loc.latitude, loc.longitude))
            }
        }
        baiduMapViewModel.mLocationClient = mLocationClient
        mLocationClient.startLocation()

        binding.mapTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                moe.fuqiuluo.portal.R.id.map_type_normal -> {
                    binding.bmapView.map.mapType = AMap.MAP_TYPE_NORMAL
                }

                moe.fuqiuluo.portal.R.id.map_type_satellite -> {
                    binding.bmapView.map.mapType = AMap.MAP_TYPE_SATELLITE
                }

                else -> {
                    Log.e("HomeFragment", "Unknown location view mode: $checkedId")
                }
            }
            context?.mapType = binding.bmapView.map.mapType
        }

        baiduMapViewModel.baiduMap.setOnMapTouchListener {
            if (isDrawing) {
                val currentPoint = baiduMapViewModel.baiduMap.cameraPosition.target.wgs84

                when (it.action) {
                    MotionEvent.ACTION_DOWN -> { // 新增 DOWN 事件处理
                        if (mPoints.size <= 0) {
                            mPoints.add(currentPoint)
                        }
                        lastPoint = currentPoint
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (lastPoint == null) {
                            lastPoint = currentPoint
                        }
                        lastPoint?.let { lp -> drawLine(lp, currentPoint) }
                    }

                    MotionEvent.ACTION_UP -> {
                        mPoints.add(currentPoint)
                        lastPoint = null // 关键修改：重置起点
                    }
                }
            }
        }

        binding.fabStart.setOnClickListener {
            isDrawing = true;
            mPoints = arrayListOf()
            lastPoint = null; // 重置上一个点
        }

        binding.fabRollback.setOnClickListener {
            // 撤回上一个点并且刷新地图
            if (mPoints.size > 0) {
                mPoints.removeAt(mPoints.size - 1)
                refresh()
            }
        }

        binding.fabComplete.setOnClickListener {
            isDrawing = false
            if (!showAddRouteDialog()) {
                Toast.makeText(requireContext(), "选择路线异常", Toast.LENGTH_SHORT).show()
            }
        }

        binding.fabMyLocation.setOnClickListener {
            baiduMapViewModel.baiduMap.locateMe()
        }

        binding.fabSearch.setOnClickListener {
            showSearchPointDialog()
        }

        return binding.root
    }

    private fun refresh() {
        baiduMapViewModel.baiduMap.clear() // 清除之前的所有覆盖物

        // 绘制之前记录的点到点的线
        for (i in 0 until mPoints.size - 1) {
            baiduMapViewModel.baiduMap.addPolyline(
                PolylineOptions()
                    .color(Color.argb(178, 0, 78, 255))
                    .width(10f)
                    .addAll(listOf(mPoints[i].gcj02, mPoints[i + 1].gcj02))
            )
        }
    }

    private fun drawLine(start: Pair<Double, Double>, end: Pair<Double, Double>) {
        baiduMapViewModel.baiduMap.clear() // 清除之前的所有覆盖物

        // 绘制之前记录的点到点的线
        for (i in 0 until mPoints.size - 1) {
            baiduMapViewModel.baiduMap.addPolyline(
                PolylineOptions()
                    .color(Color.argb(178, 0, 78, 255))
                    .width(10f)
                    .addAll(listOf(mPoints[i].gcj02, mPoints[i + 1].gcj02))
            )
        }

        baiduMapViewModel.baiduMap.addPolyline(
            PolylineOptions()
                .color(Color.argb(178, 0, 78, 255))
                .width(10f)
                .addAll(listOf(start.gcj02, end.gcj02))
        )
    }

    private fun showSearchPointDialog() {
        val context = requireContext()
        val input = TextInputEditText(context).apply {
            hint = getString(R.string.search_route_point)
            singleLine = true
        }
        val listView = ListView(context)
        val results = mutableListOf<Map<String, Any?>>()
        val adapter = SimpleAdapter(
            context,
            results,
            R.layout.layout_search_poi_item,
            arrayOf(Poi.KEY_NAME, Poi.KEY_ADDRESS, Poi.KEY_LONGITUDE_RAW, Poi.KEY_LATITUDE_RAW, Poi.KEY_TAG),
            intArrayOf(R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude, R.id.poi_tag)
        )
        listView.adapter = adapter

        mInputtips = Inputtips(context, Inputtips.InputtipsListener { tips, resultCode ->
            results.clear()
            if (resultCode == 1000 && !tips.isNullOrEmpty()) {
                results.addAll(tips.toPoi(baiduMapViewModel.currentLocation).map { it.toMap() })
            }
            adapter.notifyDataSetChanged()
        })

        input.addTextChangedListener {
            val keyword = it?.toString()?.trim().orEmpty()
            if (keyword.isBlank()) {
                results.clear()
                adapter.notifyDataSetChanged()
                return@addTextChangedListener
            }
            val query = InputtipsQuery(keyword, MainActivity.mCityString ?: "")
            query.setCityLimit(false)
            mInputtips?.query = query
            mInputtips?.requestInputtipsAsyn()
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(listView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 320).toInt()
            ))
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.search_route_point)
            .setView(content)
            .setNegativeButton("取消", null)
            .create()

        listView.setOnItemClickListener { _, view, _, _ ->
            val lon = view.findViewById<android.widget.TextView>(R.id.poi_longitude).text.toString().toDoubleOrNull()
            val lat = view.findViewById<android.widget.TextView>(R.id.poi_latitude).text.toString().toDoubleOrNull()
            if (lat == null || lon == null) {
                Toast.makeText(context, "地点坐标异常", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            val point = lat to lon
            mPoints.add(point)
            isDrawing = false
            lastPoint = null
            baiduMapViewModel.markedLoc = point
            refresh()
            markMap(true)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            mInputtips = null
        }
        dialog.show()
    }


    private fun markMap(moveEyes: Boolean = false) = with(baiduMapViewModel) {
        val loc = markedLoc!!.gcj02
        val ooA = MarkerOptions()
            .position(loc)
            .icon(mMapIndicator)
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
        mInputtips = null
        binding.bmapView.map.setMyLocationEnabled(false)
        binding.bmapView.onDestroy()
        _binding = null
    }

    @SuppressLint("SetTextI18n", "MissingInflatedId", "MutatingSharedPrefs")
    private fun showAddRouteDialog(): Boolean {
        fun checkLatLon(lat: Double?, lon: Double?): Boolean {
            return (lat != null && lon != null) && lat in -90.0..90.0 && lon in -180.0..180.0
        }

        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_add_route, null)
        val editName = dialogView.findViewById<TextInputEditText>(R.id.etRouteName)
        editName.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editName.error = "名称不能为空"
            }
        }
        val editRoute = dialogView.findViewById<TextInputEditText>(R.id.etRouteSet)
        editRoute.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editRoute.error = "路线经纬度不能为空"
            } else {
                try {
                    val json = it.toString()
                    // 转为 LatLng 数组
                    val points = JSON.parseArray(json)
                    if (points.size < 2) {
                        editRoute.error = "路线经纬度至少需要两个点"
                    }
                    // 循环检查每个点的经纬度是否合法
                    for (point in points) {
                        val jsonObject = point as JSONObject
                        val latitude = jsonObject.getDouble("first")
                        val longitude = jsonObject.getDouble("second")
                        if (!checkLatLon(latitude, longitude)) {
                            editRoute.error = "路线经纬度格式错误"
                            return@addTextChangedListener
                        }
                    }
                } catch (e: Exception) {
                    editRoute.error = "路线经纬度json格式错误"
                }
            }
        }

        editRoute.setText(JSON.toJSONString(mPoints))

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(null)
        builder
            .setCancelable(false)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val routeJson = editRoute.text.toString()

                var name = editName.text?.toString()
                if (name.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val points = JSON.parseArray(routeJson)
                if (points.size < 2) {
                    Toast.makeText(requireContext(), "路线经纬度至少需要两个点", Toast.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }

                // 循环检查每个点的经纬度是否合法
                for (point in points) {
                    val jsonObject = point as JSONObject
                    val latitude = jsonObject.getDouble("first")
                    val longitude = jsonObject.getDouble("second")
                    if (!checkLatLon(latitude, longitude)) {
                        Toast.makeText(requireContext(), "路线经纬度格式错误", Toast.LENGTH_SHORT)
                            .show()
                        return@setPositiveButton
                    }
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

                val route = JSON.toJSONString(points)
                with(requireContext()) {
                    val routes = jsonHistoricalRoutes
                    val jsonArray: JSONArray = if (routes.isNotEmpty()) {
                        JSON.parseArray(routes)
                    } else {
                        JSONArray()
                    }
                    val historicalRoute = HistoricalRoute(name, mPoints)
                    jsonArray.add(historicalRoute)
                    jsonArray.toJSONString().also {
                        jsonHistoricalRoutes = it
                    }
                }

                Toast.makeText(requireContext(), "路线已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()

        return true
    }
}
