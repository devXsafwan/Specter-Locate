package com.specter.locate.location
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.specter.locate.data.*
import kotlinx.coroutines.*
class LocationForegroundService:Service(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private lateinit var fused:FusedLocationProviderClient
 private lateinit var callback:LocationCallback
 override fun onCreate(){
  super.onCreate(); fused=LocationServices.getFusedLocationProviderClient(this)
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel("location","Location tracking",NotificationManager.IMPORTANCE_LOW))
  startForeground(701,NotificationCompat.Builder(this,"location").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("SPECTER LOCATE").setContentText("Location sharing is active").setOngoing(true).build())
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
     AppDatabase.get(applicationContext).locationDao().insert(LocationPoint(latitude=location.latitude,longitude=location.longitude,accuracy=location.accuracy))
    }}
   }
  }
  fused.requestLocationUpdates(request,callback,mainLooper)
 }
 override fun onDestroy(){if(::callback.isInitialized)fused.removeLocationUpdates(callback);scope.cancel();super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}