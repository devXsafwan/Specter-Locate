package com.specter.locate.admin.net
import com.specter.locate.admin.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
data class ApiResult(val ok:Boolean,val body:JSONObject=JSONObject(),val error:String?=null)
class ApiClient(private val tokenProvider:()->String?){
 private val base=BuildConfig.API_BASE_URL.trimEnd('/')
 private fun request(path:String,method:String="GET",body:JSONObject?=null):ApiResult{
  if(base.contains("YOUR_API_HOST"))return ApiResult(false,error="Backend URL is not configured")
  return try{
   val c=(URL(base+path).openConnection() as HttpURLConnection)
   c.requestMethod=method;c.connectTimeout=10000;c.readTimeout=15000
   c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json")
   tokenProvider()?.let{c.setRequestProperty("Authorization","Bearer $it")}
   if(body!=null){c.doOutput=true;c.outputStream.use{it.write(body.toString().toByteArray())}}
   val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream
   val text=stream?.bufferedReader()?.use{it.readText()}?:"{}";c.disconnect()
   val json=try{JSONObject(text)}catch(_:Exception){JSONObject()}
   ApiResult(code in 200..299,json,json.optString("message",json.optString("error",null)))
  }catch(e:Exception){ApiResult(false,error=e.message?:"Network error")}
 }
 fun requestOtp(phone:String)=request("/api/v1/auth/request-otp","POST",JSONObject().put("phone",phone).put("role","admin"))
 fun verifyOtp(phone:String,code:String)=request("/api/v1/auth/verify-otp","POST",JSONObject().put("phone",phone).put("code",code).put("role","admin"))
 fun bootstrap(phone:String,code:String)=request("/api/v1/auth/bootstrap-admin","POST",JSONObject().put("phone",phone).put("bootstrapCode",code))
 fun devices()=request("/api/v1/admin/devices")
 fun device(id:String)=request("/api/v1/admin/devices/"+id)
 fun createPairing()=request("/api/v1/pairings/code","POST",JSONObject())
 fun revoke(id:String)=request("/api/v1/admin/devices/"+id+"/revoke","POST",JSONObject())
}