package com.specter.locate.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.specter.locate.admin.net.ApiClient
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg=Color(0xFF070A0F)
private val Surface=Color(0xFF0F141C)
private val Surface2=Color(0xFF151C26)
private val Cyan=Color(0xFF67E8F9)
private val Violet=Color(0xFFA78BFA)
private val Muted=Color(0xFF8B96A7)

class MainActivity:ComponentActivity(){
 private val prefs by lazy{getSharedPreferences("specter_admin",MODE_PRIVATE)}
 private val api by lazy{ApiClient{prefs.getString("token",null)}}
 override fun onCreate(state:Bundle?){
  installSplashScreen();super.onCreate(state)
  setContent{
   var route by remember{mutableStateOf(if(prefs.getString("token",null)==null)"login" else "dashboard")}
   var loginPhone by remember{mutableStateOf("")}
   var message by remember{mutableStateOf("")}
   var busy by remember{mutableStateOf(false)}
   AnimatedContent(route,label="route"){r->when(r){
    "login"->LoginScreen(loginPhone,{loginPhone=it},message,busy,
      {phone,code->busy=true;lifecycleScope.launch(Dispatchers.IO){val x=api.bootstrap(phone,code);runOnUiThread{busy=false;message=if(x.ok)"Admin enabled. You can request OTP now." else x.error?:"Bootstrap failed"}}},
      {phone->busy=true;lifecycleScope.launch(Dispatchers.IO){val x=api.requestOtp(phone);runOnUiThread{busy=false;message=if(x.ok){route="otp";"OTP sent."}else x.error?:"Could not send OTP"}}})
    "otp"->OtpScreen(loginPhone,message,busy){code->busy=true;lifecycleScope.launch(Dispatchers.IO){val x=api.verifyOtp(loginPhone,code);runOnUiThread{busy=false;if(x.ok){prefs.edit{putString("token",x.body.optString("token"))};route="dashboard";message=""}else message=x.error?:"Invalid OTP"}}}
    "detail"->DeviceDetailScreen(prefs.getString("selectedDevice","")!!,api,{route="dashboard"},{id->lifecycleScope.launch(Dispatchers.IO){val x=api.revoke(id);runOnUiThread{message=if(x.ok)"Pairing revoked." else x.error?:"Failed";route="dashboard"}}})
    else->DashboardScreen(api,message,{id->prefs.edit{putString("selectedDevice",id)};route="detail"},{busy=true;lifecycleScope.launch(Dispatchers.IO){val x=api.createPairing();runOnUiThread{busy=false;message=if(x.ok)"PAIRING CODE: "+x.body.optString("code") else x.error?:"Could not create code"}}},{prefs.edit{clear()};route="login"})
   }}
  }
 }
}

@Composable private fun LoginScreen(phone:String,onPhone:(String)->Unit,message:String,busy:Boolean,onBootstrap:(String,String)->Unit,onOtp:(String)->Unit){
 var bootstrap by remember{mutableStateOf("")}
 Center{Brand();Text("ADMIN SETUP",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Register the admin phone once with the server bootstrap secret.",color=Muted);OutlinedTextField(phone,onPhone,label={Text("Admin phone")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(bootstrap,{bootstrap=it},label={Text("Bootstrap secret")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick={onBootstrap(phone.trim(),bootstrap.trim())},enabled=!busy&&phone.isNotBlank()&&bootstrap.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("ENABLE ADMIN")};Spacer(Modifier.height(6.dp));Button(onClick={onOtp(phone.trim())},enabled=!busy&&phone.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("SEND ADMIN OTP")};Message(message)}
}
@Composable private fun OtpScreen(phone:String,message:String,busy:Boolean,onVerify:(String)->Unit){
 var code by remember{mutableStateOf("")}
 Center{Brand();Text("ADMIN VERIFICATION",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(phone,color=Muted);OutlinedTextField(code,{code=it.filter(Char::isDigit)},label={Text("OTP")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick={onVerify(code)},enabled=!busy&&code.length>=4,modifier=Modifier.fillMaxWidth()){Text(if(busy)"VERIFYING…" else "SIGN IN")};Message(message)}
}
@Composable private fun DashboardScreen(api:ApiClient,message:String,onDevice:(String)->Unit,onPair:()->Unit,onLogout:()->Unit){
 var devices by remember{mutableStateOf(emptyList<JSONObject>())}
 var localMessage by remember{mutableStateOf(message)}
 LaunchedEffect(Unit){while(true){val r=withContext(Dispatchers.IO){api.devices()};if(r.ok){val a=r.body.optJSONArray("devices");devices=(0 until (a?.length()?:0)).map{a!!.getJSONObject(it)}}else localMessage=r.error?:"Connection error";delay(5000)}}
 LazyColumn(Modifier.fillMaxSize().background(Bg),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("SPECTER",color=Cyan,fontWeight=FontWeight.Bold);Text("LOCATE ADMIN",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)};IconButton(onClick=onLogout){Icon(Icons.Default.Logout,null,tint=Color.White)}}}
  item{Button(onClick=onPair,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("GENERATE PAIRING CODE")}}
  if(localMessage.isNotBlank())item{Text(localMessage,color=Cyan)}
  item{Text("DEVICES",color=Color.White,fontWeight=FontWeight.Bold)}
  items(devices,key={it.optString("id")}){d->DeviceCard(d){onDevice(d.optString("id"))}}
 }
}
@Composable private fun DeviceCard(d:JSONObject,onClick:()->Unit){
 val online=d.optBoolean("online")
 Card(Modifier.fillMaxWidth().clickable{onClick()},colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(20.dp)){
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
   Row{Text(d.optString("device_name","Android device"),color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(if(online)"ONLINE" else "OFFLINE",color=if(online)Cyan else Muted,fontWeight=FontWeight.Bold)}
   Text(d.optString("manufacturer")+" "+d.optString("model"),color=Muted)
   val b=d.optInt("battery_percent",-1)
   Text("Battery "+if(b>=0)b.toString()+"%" else "—"+"  •  Android "+d.optString("android_version","—"),color=Muted)
   Text("Last seen: "+d.optString("last_seen_at","—"),color=Muted,style=MaterialTheme.typography.bodySmall)
  }
 }
}
@Composable private fun DeviceDetailScreen(id:String,api:ApiClient,onBack:()->Unit,onRevoke:(String)->Unit){
 var device by remember{mutableStateOf<JSONObject?>(null)}
 var history by remember{mutableStateOf(emptyList<JSONObject>())}
 var message by remember{mutableStateOf("")}
 LaunchedEffect(id){while(true){val r=withContext(Dispatchers.IO){api.device(id)};if(r.ok){device=r.body.optJSONObject("device");val a=r.body.optJSONArray("history");history=(0 until (a?.length()?:0)).map{a!!.getJSONObject(it)}}else{message=r.error?:"Device unavailable";break};delay(5000)}}
 Column(Modifier.fillMaxSize().background(Bg).padding(20.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=Color.White)};Text("DEVICE",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
  if(device!=null)LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=12.dp)){
   item{DeviceDetailCard(device!!,id,onRevoke)}
   item{Text("LOCATION HISTORY",color=Color.White,fontWeight=FontWeight.Bold)}
   items(history){p->Card(colors=CardDefaults.cardColors(containerColor=Surface2),shape=RoundedCornerShape(14.dp)){Text(p.optDouble("latitude").toString()+", "+p.optDouble("longitude").toString()+"  •  "+p.optString("captured_at"),color=Color.White,modifier=Modifier.padding(14.dp))}}
  }else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(message.ifBlank{"Loading device…"},color=Muted)}
 }
}
@Composable private fun DeviceDetailCard(d:JSONObject,id:String,onRevoke:(String)->Unit){
 val lat=d.optDouble("last_latitude",Double.NaN);val lon=d.optDouble("last_longitude",Double.NaN)
 Card(colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(d.optString("device_name"),color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
  Text("Status: "+if(d.optBoolean("online"))"ONLINE" else "OFFLINE",color=Cyan)
  val b=d.optInt("battery_percent",-1);Text("Battery: "+if(b>=0)b.toString()+"%" else "—",color=Muted)
  Text("Model: "+d.optString("manufacturer")+" "+d.optString("model"),color=Muted)
  Text("Android: "+d.optString("android_version"),color=Muted)
  Text("Last seen: "+d.optString("last_seen_at"),color=Muted)
  if(!lat.isNaN()&&!lon.isNaN())Button(onClick={val uri=Uri.parse("geo:"+lat+","+lon+"?q="+lat+","+lon);/* handled by parent activity through intent chooser not needed here */},modifier=Modifier.fillMaxWidth()){Text("LOCATION: %.6f, %.6f".format(lat,lon))}
  OutlinedButton(onClick={onRevoke(id)},modifier=Modifier.fillMaxWidth()){Text("REVOKE PAIRING")}
 }}
}
@Composable private fun Brand(){Text("SPECTER",color=Cyan,fontWeight=FontWeight.Bold);Text("LOCATE ADMIN",color=Color.White,style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.ExtraBold);Text("Authorized device management",color=Muted)}
@Composable private fun Message(v:String){if(v.isNotBlank())Text(v,color=Muted)}
@Composable private fun Center(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bg,Color(0xFF0D1118)))).padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally,content=content)}
