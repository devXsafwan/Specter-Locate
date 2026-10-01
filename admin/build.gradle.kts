plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace="com.specter.locate.admin"
 compileSdk=35
 buildFeatures { buildConfig=true; compose=true }
 defaultConfig {
  applicationId="com.specter.locate.admin"
  minSdk=26
  targetSdk=35
  versionCode=1
  versionName="1.0.0"
  val apiUrl=((project.findProperty("SPECTER_API_URL") as String?)?.takeIf { it.isNotBlank() }) ?: "https://YOUR_API_HOST"
  buildConfigField("String","API_BASE_URL","\"$apiUrl\"")
 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.core:core-splashscreen:1.2.0")
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.compose.ui:ui:1.7.8")
 implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
 implementation("androidx.compose.material3:material3:1.3.1")
 implementation("androidx.compose.material:material-icons-extended:1.7.8")
}