package com.specter.locate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.specter.locate.location.LocationForegroundService
import com.specter.locate.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val Bg=Color(0xFF070A0F)
private val Surface=Color(0xFF0F141C)
private val Cyan=Color(0xFF67E8F9)
private val Violet=Color(0xFFA78BFA)
private val Muted=Color(0xFF8B96A7)

class MainActivity:ComponentActivity(){
 private val prefs by lazy{getSharedPreferences("specter_user",MODE_PRIVATE)}
 private val api by lazy{ApiClient{prefs.getString("token",null)}}
 private var pendingStart=false
 private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(pendingStart&&hasLocation())startTracking();pendingStart=false}

 override fun onCreate(state:Bundle?){
  installSplashScreen();super.onCreate(state)
  setContent{
   var route by remember{mutableStateOf(if(prefs.getString("token",null)==null)"auth" else "loading")}
   var phone by remember{mutableStateOf("")}
   var message by remember{mutableStateOf("")}
   var busy by remember{mutableStateOf(false)}
   LaunchedEffect(route){
    if(route=="loading"){
     val result=withContext(Dispatchers.IO){api.devicesMe()}
     route=if(result.ok)"home" else "auth"
    }
   }
   SpecterTheme{
    AnimatedContent(targetState=route,label="route"){current->
     when(current){
      "auth"->AuthScreen(phone,{phone=it},busy,message,{
       busy=true
       lifecycleScope.launch(Dispatchers.IO){
        val x=api.requestOtp(phone.trim(),"user")
        runOnUiThread{
         busy=false
         message=if(x.ok)"Verification code sent." else x.error?:"Could not send code"
         if(x.ok)route="otp"
        }
       }
      })
      "otp"->OtpScreen(phone,busy,message){code->
       busy=true
       lifecycleScope.launch(Dispatchers.IO){
        val x=api.verifyOtp(phone.trim(),code,"user")
        if(x.ok){
         prefs.edit{putString("token",x.body.optString("token"))}
         registerDevice()
         runOnUiThread{busy=false;message="";route="pair"}
        }else runOnUiThread{busy=false;message=x.error?:"Invalid code"}
       }
      }
      "pair"->PairScreen(message,busy,{
       busy=true
       lifecycleScope.launch(Dispatchers.IO){
        val d=api.devicesMe()
        val paired=d.body.optJSONArray("devices")?.optJSONObject(0)?.optBoolean("paired",false)==true
        runOnUiThread{
         busy=false
         message=if(paired)"Device is paired." else d.error?:"Device is not paired yet."
         if(paired)route="home"
        }
       }
      },{code->
       busy=true
       lifecycleScope.launch(Dispatchers.IO){
        val x=api.claimPairing(code.trim())
        runOnUiThread{
         busy=false
         message=if(x.ok)"Device paired successfully." else x.error?:"Pairing failed"
         if(x.ok)route="home"
        }
       }
      },{logout();route="auth"})
      "home"->HomeScreen(message,{
       message=""
       if(hasLocation())startTracking()else{
        pendingStart=true
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.POST_NOTIFICATIONS))
       }
      },{
       stopService(Intent(this,LocationForegroundService::class.java))
       message="Tracking stopped."
      },{
       lifecycleScope.launch(Dispatchers.IO){
        val d=api.devicesMe()
        runOnUiThread{
         message=if(d.ok){
          val paired=d.body.optJSONArray("devices")?.optJSONObject(0)?.optBoolean("paired",false)==true
          if(paired)"Connection verified." else "Device is not paired."
         }else d.error?:"Connection error"
        }
       }
      },{route="pair"},{logout();route="auth"})
      else->LoadingScreen()
     }
    }
   }
  }
 }
 private fun registerDevice(){
  lifecycleScope.launch(Dispatchers.IO){
   if(prefs.getString("deviceId",null)!=null)return@launch
   val name="${Build.MANUFACTURER} ${Build.MODEL}".trim()
   val r=api.registerDevice(name,Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,"2.0.0")
   if(r.ok){prefs.edit{putString("deviceId",r.body.optJSONObject("device")?.optString("id"))}}
  }
 }
 private fun hasLocation()=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
 private fun startTracking()=ContextCompat.startForegroundService(this,Intent(this,LocationForegroundService::class.java))
}

@Composable private fun AuthScreen(phone:String,onPhone:(String)->Unit,busy:Boolean,message:String,onSend:()->Unit){
 Center{Brand();Text("Secure device registration",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Verify the phone number that belongs to this device.",color=Muted);OutlinedTextField(phone,onPhone,label={Text("Phone number")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick=onSend,enabled=!busy&&phone.isNotBlank(),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text(if(busy)"SENDING…" else "SEND VERIFICATION CODE")};Message(message)}
}
@Composable private fun OtpScreen(phone:String,busy:Boolean,message:String,onVerify:(String)->Unit){var code by remember{mutableStateOf("")};Center{Brand();Text("Verify $phone",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);OutlinedTextField(code,{code=it.filter(Char::isDigit).take(10)},label={Text("Verification code")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick={onVerify(code)},enabled=!busy&&code.length>=4,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text(if(busy)"VERIFYING…" else "VERIFY & CONTINUE")};Message(message)}}
@Composable private fun PairScreen(message:String,busy:Boolean,onRefresh:()->Unit,onClaim:(String)->Unit,onLogout:()->Unit){var code by remember{mutableStateOf("")};Center{Brand();Text("PAIR THIS DEVICE",color=Cyan,fontWeight=FontWeight.Bold);Text("Enter the 8-digit pairing code generated in SPECTER LOCATE ADMIN.",color=Muted);OutlinedTextField(code,{code=it.filter(Char::isDigit).take(8)},label={Text("Pairing code")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick={onClaim(code)},enabled=!busy&&code.length==8,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("PAIR DEVICE")};OutlinedButton(onClick=onRefresh,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("CHECK PAIRING STATUS")};TextButton(onClick=onLogout){Text("Sign out",color=Muted)};Message(message)}}
@Composable private fun HomeScreen(message:String,onStart:()->Unit,onStop:()->Unit,onCheck:()->Unit,onPair:()->Unit,onLogout:()->Unit){Center{Brand();Card(colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("DEVICE CONNECTED",color=Cyan,fontWeight=FontWeight.Bold);Text("This device is registered and ready for authorized location sharing.",color=Muted);Button(onClick=onStart,modifier=Modifier.fillMaxWidth()){Text("START LOCATION SHARING")};OutlinedButton(onClick=onStop,modifier=Modifier.fillMaxWidth()){Text("STOP LOCATION SHARING")};OutlinedButton(onClick=onCheck,modifier=Modifier.fillMaxWidth()){Text("CHECK CONNECTION")};TextButton(onClick=onPair){Text("Pair with another admin",color=Muted)};TextButton(onClick=onLogout){Text("Sign out",color=Muted)}}};Message(message)}}
@Composable private fun LoadingScreen(){Center{Brand();CircularProgressIndicator(color=Cyan);Text("Checking device session…",color=Muted)}}
@Composable private fun Brand(){Text("SPECTER",color=Cyan,fontWeight=FontWeight.Bold);Text("LOCATE",color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.displaySmall);Text("Your location. Under your control.",color=Muted)}
@Composable private fun Message(v:String){if(v.isNotBlank())Text(v,color=Muted)}
@Composable private fun Center(content:@Composable ColumnScope.() -> Unit){Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bg,Color(0xFF0D1118)))).padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally,content=content)}
@Composable private fun SpecterTheme(content:@Composable () -> Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Cyan,secondary=Violet,background=Bg,surface=Surface),content=content)}
