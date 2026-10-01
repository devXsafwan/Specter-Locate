package com.specter.locate
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.specter.locate.data.*
import com.specter.locate.location.LocationForegroundService
class MainActivity:ComponentActivity(){
 private var startAfterPermission=false
 private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
  if(startAfterPermission && hasLocation()){ContextCompat.startForegroundService(this,Intent(this,LocationForegroundService::class.java))}
  startAfterPermission=false
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent{
   val dao=remember{AppDatabase.get(this).locationDao()}
   val points by dao.observe().collectAsState(initial=emptyList())
   var active by remember{mutableStateOf(false)}
   MaterialTheme(colorScheme=darkColorScheme()){
    Surface(Modifier.fillMaxSize()){
     LazyColumn(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
      item{Text("SPECTER LOCATE",style=MaterialTheme.typography.headlineMedium);Text("Your location. Under your control.")}
      item{Text(if(active)"LIVE SHARING ACTIVE" else "LOCATION SHARING OFF")}
      item{
       Button(onClick={
        if(hasLocation()){ContextCompat.startForegroundService(this@MainActivity,Intent(this@MainActivity,LocationForegroundService::class.java));active=true}
        else{startAfterPermission=true;permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.POST_NOTIFICATIONS))}
       },Modifier.fillMaxWidth()){Text("START LOCATION SHARING")}
      }
      item{OutlinedButton(onClick={stopService(Intent(this@MainActivity,LocationForegroundService::class.java));active=false},Modifier.fillMaxWidth()){Text("STOP LOCATION SHARING")}}
      item{Text("RECENT LOCATION HISTORY",style=MaterialTheme.typography.titleMedium)}
      items(points.take(30),key={it.id}){p->Text("%.6f, %.6f  |  %.1fm".format(p.latitude,p.longitude,p.accuracy),Modifier.fillMaxWidth().padding(10.dp))}
     }
    }
   }
  }
 }
 private fun hasLocation()=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
}