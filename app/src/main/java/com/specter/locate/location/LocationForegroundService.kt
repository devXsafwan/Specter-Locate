package com.specter.locate.location

import android.Manifest
import android.app.*
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.specter.locate.data.*
import com.specter.locate.net.ApiClient
import kotlinx.coroutines.*

class LocationForegroundService:Service(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private lateinit var fused:FusedLocationProviderClient
 private lateinit var callback:LocationCallback
 private lateinit var api:ApiClient
 private val prefs by lazy{getSharedPreferences("specter_user",MODE_PRIVATE)}

 override fun onCreate(){
  super.onCreate()
  api=ApiClient{prefs.getString("token",null)}
  fused=LocationServices.getFusedLocationProviderClient(this)
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel("location","Location tracking",NotificationManager.IMPORTANCE_LOW))
  startForeground(701,NotificationCompat.Builder(this,"location").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("SPECTER LOCATE").setContentText("Authorized location sharing is active").setOngoing(true).build())
  startUpdates()
 }
 private fun startUpdates(){
  val fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
  val coarse=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
  if(!fine&&!coarse){stopSelf();return}
  val request=LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,30000L).setMinUpdateIntervalMillis(10000L).build()
  callback=object:LocationCallback(){
   override fun onLocationResult(result:LocationResult){
    result.locations.forEach{location->scope.launch{
     val deviceId=prefs.getString("deviceId",null)
     AppDatabase.get(applicationContext).locationDao().insert(LocationPoint(latitude=location.latitude,longitude=location.longitude,accuracy=location.accuracy))
     if(!deviceId.isNullOrBlank()){
      api.uploadLocation(deviceId,location.latitude,location.longitude,location.accuracy,System.currentTimeMillis())
      val bm=getSystemService(BATTERY_SERVICE) as BatteryManager
      val level=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0,100)
      val charging=bm.isChargingCompat()
      api.heartbeat(deviceId,level,charging)
     }
    }}
   }
  }
  fused.requestLocationUpdates(request,callback,mainLooper)
 }
 override fun onDestroy(){if(::callback.isInitialized)fused.removeLocationUpdates(callback);scope.cancel();super.onDestroy()}
 override fun onBind(intent:android.content.Intent?):IBinder?=null
 private fun BatteryManager.isChargingCompat():Boolean {
  return if(android.os.Build.VERSION.SDK_INT>=23) isCharging else false
 }
}