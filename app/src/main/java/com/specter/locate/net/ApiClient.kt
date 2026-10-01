package com.specter.locate.net

import com.specter.locate.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ApiResult(val ok:Boolean,val body:JSONObject=JSONObject(),val error:String?=null)

class ApiClient(private val tokenProvider:()->String?){
    private val base=BuildConfig.API_BASE_URL.trimEnd('/')
    private fun request(path:String,method:String="GET",body:JSONObject?=null):ApiResult{
        if(base.contains("YOUR_API_HOST")) return ApiResult(false,error="Backend URL is not configured")
        return try{
            val c=(URL(base+path).openConnection() as HttpURLConnection)
            c.requestMethod=method
            c.connectTimeout=10000;c.readTimeout=15000
            c.setRequestProperty("Accept","application/json")
            c.setRequestProperty("Content-Type","application/json")
            tokenProvider()?.let{c.setRequestProperty("Authorization","Bearer $it")}
            if(body!=null){c.doOutput=true;c.outputStream.use{it.write(body.toString().toByteArray())}}
            val code=c.responseCode
            val stream=if(code in 200..299)c.inputStream else c.errorStream
            val text=stream?.bufferedReader()?.use{it.readText()}?:"{}"
            c.disconnect()
            val json=try{JSONObject(text)}catch(_:Exception){JSONObject()}
            ApiResult(code in 200..299,json,json.optString("message",json.optString("error","")))
        }catch(e:Exception){ApiResult(false,error=e.message?:"Network error")}
    }
    fun requestOtp(phone:String,role:String)=request("/api/v1/auth/request-otp","POST",JSONObject().put("phone",phone).put("role",role))
    fun verifyOtp(phone:String,code:String,role:String)=request("/api/v1/auth/verify-otp","POST",JSONObject().put("phone",phone).put("code",code).put("role",role))
    fun registerDevice(name:String,manufacturer:String,model:String,android:String,app:String)=request("/api/v1/devices/register","POST",JSONObject().put("deviceName",name).put("manufacturer",manufacturer).put("model",model).put("androidVersion",android).put("appVersion",app))
    fun devicesMe()=request("/api/v1/devices/me")
    fun claimPairing(code:String)=request("/api/v1/pairings/claim","POST",JSONObject().put("code",code))
    fun heartbeat(deviceId:String,battery:Int,charging:Boolean)=request("/api/v1/devices/heartbeat","POST",JSONObject().put("deviceId",deviceId).put("batteryPercent",battery).put("charging",charging))
    fun uploadLocation(deviceId:String,lat:Double,lon:Double,accuracy:Float,time:Long)=request("/api/v1/locations/batch","POST",JSONObject().put("deviceId",deviceId).put("points",JSONArray().put(JSONObject().put("latitude",lat).put("longitude",lon).put("accuracy",accuracy).put("capturedAt",time))))
}