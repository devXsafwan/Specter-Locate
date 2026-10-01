package com.specter.locate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.specter.locate.data.AppDatabase
import com.specter.locate.data.LocationPoint
import com.specter.locate.location.LocationForegroundService
import kotlinx.coroutines.flow.collectLatest

private val Bg=Color(0xFF070A0F)
private val Surface=Color(0xFF0F141C)
private val Surface2=Color(0xFF151C26)
private val Cyan=Color(0xFF67E8F9)
private val Violet=Color(0xFFA78BFA)
private val Muted=Color(0xFF8B96A7)

class MainActivity:ComponentActivity(){
 private var startAfterPermission=false
 private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(startAfterPermission&&hasLocation())startLocation();startAfterPermission=false}
 override fun onCreate(savedInstanceState:Bundle?){
  installSplashScreen();super.onCreate(savedInstanceState)
  setContent{
   val dao=remember{AppDatabase.get(this).locationDao()}
   var points by remember{mutableStateOf(emptyList<LocationPoint>())}
   var active by remember{mutableStateOf(false)}
   var screen by remember{mutableStateOf("home")}
   LaunchedEffect(Unit){dao.observe().collectLatest{points=it}}
   SpecterTheme{AnimatedContent(targetState=screen,label="navigation"){s->when(s){
    "settings"->SettingsScreen(active,{screen="home"},{screen="developer"},{screen="admin"})
    "developer"->DeveloperProfile{screen="settings"}
    "admin"->AdminPanel{screen="settings"}
    else->Dashboard(active,points,{if(hasLocation()){startLocation();active=true}else{startAfterPermission=true;permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.POST_NOTIFICATIONS))}},{stopService(Intent(this,LocationForegroundService::class.java));active=false},{screen="settings"},{dao.clear()})
   }}}
  }
 }
 private fun hasLocation()=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
 private fun startLocation()=ContextCompat.startForegroundService(this,Intent(this,LocationForegroundService::class.java))
}

@Composable private fun Dashboard(active:Boolean,points:List<LocationPoint>,onStart:()->Unit,onStop:()->Unit,onSettings:()->Unit,onClear:()->Unit){
 val pulse by rememberInfiniteTransition(label="pulse").animateFloat(.94f,1.06f,infiniteRepeatable(tween(1200),RepeatMode.Reverse),label="pulse")
 LazyColumn(Modifier.fillMaxSize().background(Bg),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  item{Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){Column(Modifier.weight(1f)){Text("SPECTER",color=Cyan,fontWeight=FontWeight.Bold);Text("LOCATE",color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.headlineLarge)};IconButton(onClick=onSettings){Icon(Icons.Default.Settings,null,tint=Color.White)}}}
  item{Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Surface)){Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){Box(Modifier.size(82.dp).scale(if(active)pulse else 1f).background(Brush.radialGradient(listOf(Cyan.copy(.25f),Violet.copy(.12f),Color.Transparent)),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.MyLocation,null,tint=if(active)Cyan else Muted,modifier=Modifier.size(34.dp))};Text(if(active)"LIVE TRACKING" else "TRACKING STANDBY",color=if(active)Cyan else Color.White,fontWeight=FontWeight.Bold);Text(if(active)"Your location service is running in the foreground." else "Start sharing when you are ready. You stay in control.",color=Muted);if(!active)Button(onClick=onStart,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("START LOCATION SHARING")}else OutlinedButton(onClick=onStop,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("STOP LOCATION SHARING")}}}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()){StatCard("POINTS",points.size.toString(),Modifier.weight(1f));StatCard("MODE",if(active)"LIVE" else "IDLE",Modifier.weight(1f))}}
  item{Text("LOCATION HISTORY",color=Color.White,fontWeight=FontWeight.Bold)}
  items(points.take(20),key={it.id}){p->HistoryCard(p)}
  if(points.isNotEmpty())item{TextButton(onClick=onClear){Text("Clear local history",color=Muted)}}
 }
}
@Composable private fun StatCard(title:String,value:String,modifier:Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=Surface2),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp)){Text(title,color=Muted,style=MaterialTheme.typography.labelSmall);Spacer(Modifier.height(6.dp));Text(value,color=Color.White,fontWeight=FontWeight.Bold)}}}
@Composable private fun HistoryCard(p:LocationPoint){Card(colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(15.dp)){Text("%.6f, %.6f".format(p.latitude,p.longitude),color=Color.White);Text("Accuracy %.1fm".format(p.accuracy),color=Muted,style=MaterialTheme.typography.bodySmall)}}}
@Composable private fun SettingsScreen(active:Boolean,onBack:()->Unit,onDeveloper:()->Unit,onAdmin:()->Unit){Screen("Settings",onBack){SettingRow(Icons.Default.Person,"Developer Profile","Safwan Bin Mahbub",onDeveloper);SettingRow(Icons.Default.AdminPanelSettings,"Admin Console","Management & diagnostics",onAdmin);SettingRow(Icons.Default.Security,"Privacy","Consent-first location tracking",{});SettingRow(Icons.Default.Info,"Application","SPECTER LOCATE v1.0.0",{});SettingRow(Icons.Default.GpsFixed,"Tracking",if(active)"Currently active" else "Currently stopped",{})}}
@Composable private fun DeveloperProfile(onBack:()->Unit){Screen("Developer",onBack){Card(colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.size(78.dp).background(Brush.linearGradient(listOf(Cyan,Violet)),CircleShape),contentAlignment=Alignment.Center){Text("S",color=Bg,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold)};Text("Safwan Bin Mahbub",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Developer · SPECTER LOCATE",color=Cyan);Text("Building privacy-focused software and security-oriented tools.",color=Muted);Button(onClick={startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/devXsafwan")))},modifier=Modifier.fillMaxWidth()){Text("GitHub · devXsafwan")}}}}}
@Composable private fun AdminPanel(onBack:()->Unit){Screen("Admin Console",onBack){Card(colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("SYSTEM STATUS",color=Cyan,fontWeight=FontWeight.Bold);Text("Collector: Ready",color=Color.White);Text("Storage: Room database",color=Muted);Text("Backend sync: API layer ready",color=Muted)}}}}
@Composable private fun SettingRow(icon:ImageVector,title:String,subtitle:String,action:()->Unit){Card(Modifier.fillMaxWidth().clickable{action()},colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Cyan);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,color=Color.White,fontWeight=FontWeight.SemiBold);Text(subtitle,color=Muted,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}
@Composable private fun Screen(title:String,onBack:()->Unit,content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxSize().background(Bg).padding(20.dp)){Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)};Text(title,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)};Spacer(Modifier.height(18.dp));Column(verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}}
@Composable private fun SpecterTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Cyan,secondary=Violet,background=Bg,surface=Surface,onPrimary=Bg),content=content)}
