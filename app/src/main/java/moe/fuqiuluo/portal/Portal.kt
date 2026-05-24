package moe.fuqiuluo.portal

import android.app.Application
import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.tencent.bugly.crashreport.CrashReport
import moe.fuqiuluo.portal.android.Bugly

class Portal: Application() {

    override fun onCreate() {
        super.onCreate()

        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)

        MapsInitializer.setApiKey(AMAP_API_KEY)
        AMapLocationClient.setApiKey(AMAP_API_KEY)
        ServiceSettings.getInstance().setApiKey(AMAP_API_KEY)

        CrashReport.initCrashReport(applicationContext)

        CrashReport.setUserId(applicationContext, Bugly.getUniqueDeviceId(applicationContext))
        CrashReport.setDeviceId(applicationContext, Bugly.getUniqueDeviceId(applicationContext))
        CrashReport.setDeviceModel(applicationContext, Bugly.getDeviceModel())
        CrashReport.setCollectPrivacyInfo(applicationContext, true)

        appContext = applicationContext

        //CrashReport.setAllThreadStackEnable(applicationContext, true, true)
    }

    companion object {
        const val AMAP_API_KEY = "2dc4445618374715234af41ee7d6ae89"

        lateinit var appContext: Context
    }
}
