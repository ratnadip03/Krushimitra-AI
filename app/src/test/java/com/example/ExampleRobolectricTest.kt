package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private fun setOnline(context: Context) {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val shadowConnectivityManager = shadowOf(connectivityManager)
    
    val network = ShadowNetwork.newInstance(1)
    val networkInfo = org.robolectric.shadows.ShadowNetworkInfo.newInstance(
        android.net.NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_WIFI,
        0,
        true,
        true
    )
    
    shadowConnectivityManager.addNetwork(network, networkInfo)
    shadowConnectivityManager.setActiveNetworkInfo(networkInfo)
    shadowConnectivityManager.setDefaultNetworkActive(true)
    
    val nc = ShadowNetworkCapabilities.newInstance()
    val shadowNc = shadowOf(nc)
    shadowNc.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    shadowNc.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    
    shadowConnectivityManager.setNetworkCapabilities(network, nc)
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("KrishiMitra AI", appName)
  }

  @Test
  fun testRealWeatherNashik() = kotlinx.coroutines.runBlocking {
    ShadowLog.stream = System.out
    val context = ApplicationProvider.getApplicationContext<Context>()
    setOnline(context)
    val weatherService = com.example.data.WeatherService(context)
    val data = weatherService.fetchWeather("Nashik", "Maharashtra")
    println("REAL_WEATHER_TEST_OUTPUT: temp=${data.tempC} condition=${data.conditionText} rain3d=${data.rainChanceNext3Days} source=${data.source}")
    
    // Direct network call to fetch raw HTTP code and body for verification
    try {
      val apiKey = com.example.BuildConfig.WEATHER_API_KEY
      val query = "Nashik,Maharashtra,India"
      val url = "https://api.weatherapi.com/v1/current.json?key=$apiKey&q=${android.net.Uri.encode(query)}"
      val client = okhttp3.OkHttpClient()
      val request = okhttp3.Request.Builder().url(url).build()
      client.newCall(request).execute().use { response ->
        println("REAL_WEATHER_TEST_RAW_HTTP_CODE: ${response.code}")
        println("REAL_WEATHER_TEST_RAW_HTTP_BODY: ${response.body?.string()}")
      }
    } catch(e: Exception) {
      println("REAL_WEATHER_TEST_HTTP_ERROR: ${e.message}")
    }
  }

  @Test
  fun testRealMandiTomatoPune() = kotlinx.coroutines.runBlocking {
    ShadowLog.stream = System.out
    val context = ApplicationProvider.getApplicationContext<Context>()
    setOnline(context)
    val mandiService = com.example.data.MandiPriceService(context)
    val data = mandiService.fetchMandiPrice("Tomato", "Maharashtra", "Pune")
    println("REAL_MANDI_TOMATO_PUNE_OUTPUT: crop=${data.commodity} price=${data.latestModalPrice} trend=${data.trend} market=${data.nearestMarket} records=${data.records.size} source=${data.source}")
    
    // Direct network call to fetch raw HTTP code and body for verification
    try {
      val apiKey = com.example.BuildConfig.MANDI_API_KEY
      val url = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070" +
              "?api-key=$apiKey" +
              "&format=json" +
              "&filters[state.keyword]=Maharashtra" +
              "&filters[commodity]=Tomato" +
              "&filters[district]=Pune" +
              "&limit=10"
      val client = okhttp3.OkHttpClient()
      val request = okhttp3.Request.Builder().url(url).build()
      client.newCall(request).execute().use { response ->
        println("REAL_MANDI_TOMATO_PUNE_RAW_HTTP_CODE: ${response.code}")
        println("REAL_MANDI_TOMATO_PUNE_RAW_HTTP_BODY: ${response.body?.string()}")
      }
    } catch(e: Exception) {
      println("REAL_MANDI_TOMATO_PUNE_HTTP_ERROR: ${e.message}")
    }
  }

  @Test
  fun testRealMandiOnionNashik() = kotlinx.coroutines.runBlocking {
    ShadowLog.stream = System.out
    val context = ApplicationProvider.getApplicationContext<Context>()
    setOnline(context)
    val mandiService = com.example.data.MandiPriceService(context)
    val data = mandiService.fetchMandiPrice("Onion", "Maharashtra", "Nashik")
    println("REAL_MANDI_ONION_NASHIK_OUTPUT: crop=${data.commodity} price=${data.latestModalPrice} trend=${data.trend} market=${data.nearestMarket} records=${data.records.size} source=${data.source}")
    
    // Direct network call to fetch raw HTTP code and body for verification
    try {
      val apiKey = com.example.BuildConfig.MANDI_API_KEY
      val url = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070" +
              "?api-key=$apiKey" +
              "&format=json" +
              "&filters[state.keyword]=Maharashtra" +
              "&filters[commodity]=Onion" +
              "&filters[district]=Nashik" +
              "&limit=10"
      val client = okhttp3.OkHttpClient()
      val request = okhttp3.Request.Builder().url(url).build()
      client.newCall(request).execute().use { response ->
        println("REAL_MANDI_ONION_NASHIK_RAW_HTTP_CODE: ${response.code}")
        println("REAL_MANDI_ONION_NASHIK_RAW_HTTP_BODY: ${response.body?.string()}")
      }
    } catch(e: Exception) {
      println("REAL_MANDI_ONION_NASHIK_HTTP_ERROR: ${e.message}")
    }
  }

  @Test
  fun testRealMandiTomatoNoDistrict() = kotlinx.coroutines.runBlocking {
    ShadowLog.stream = System.out
    val context = ApplicationProvider.getApplicationContext<Context>()
    setOnline(context)
    val mandiService = com.example.data.MandiPriceService(context)
    val data = mandiService.fetchMandiPrice("tamatar", "Maharashtra", "")
    println("REAL_MANDI_TOMATO_NO_DISTRICT_OUTPUT: crop=${data.commodity} price=${data.latestModalPrice} trend=${data.trend} market=${data.nearestMarket} records=${data.records.size} source=${data.source}")
    
    // Direct network call to fetch raw HTTP code and body for verification
    try {
      val apiKey = com.example.BuildConfig.MANDI_API_KEY
      val url = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070" +
              "?api-key=$apiKey" +
              "&format=json" +
              "&filters[state.keyword]=Maharashtra" +
              "&filters[commodity]=Tomato" +
              "&limit=10"
      val client = okhttp3.OkHttpClient()
      val request = okhttp3.Request.Builder().url(url).build()
      client.newCall(request).execute().use { response ->
        val body = response.body?.string() ?: ""
        println("REAL_MANDI_TOMATO_NO_DISTRICT_RAW_HTTP_CODE: ${response.code}")
        println("REAL_MANDI_TOMATO_NO_DISTRICT_RAW_HTTP_BODY: $body")
      }
    } catch(e: Exception) {
      println("REAL_MANDI_TOMATO_NO_DISTRICT_HTTP_ERROR: ${e.message}")
    }
  }

  @Test
  fun testRealMandiOnionNoDistrict() = kotlinx.coroutines.runBlocking {
    ShadowLog.stream = System.out
    val context = ApplicationProvider.getApplicationContext<Context>()
    setOnline(context)
    val mandiService = com.example.data.MandiPriceService(context)
    val data = mandiService.fetchMandiPrice("प्याज", "Maharashtra", "")
    println("REAL_MANDI_ONION_NO_DISTRICT_OUTPUT: crop=${data.commodity} price=${data.latestModalPrice} trend=${data.trend} market=${data.nearestMarket} records=${data.records.size} source=${data.source}")
    
    // Direct network call to fetch raw HTTP code and body for verification
    try {
      val apiKey = com.example.BuildConfig.MANDI_API_KEY
      val url = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070" +
              "?api-key=$apiKey" +
              "&format=json" +
              "&filters[state.keyword]=Maharashtra" +
              "&filters[commodity]=Onion" +
              "&limit=10"
      val client = okhttp3.OkHttpClient()
      val request = okhttp3.Request.Builder().url(url).build()
      client.newCall(request).execute().use { response ->
        val body = response.body?.string() ?: ""
        println("REAL_MANDI_ONION_NO_DISTRICT_RAW_HTTP_CODE: ${response.code}")
        println("REAL_MANDI_ONION_NO_DISTRICT_RAW_HTTP_BODY: $body")
      }
    } catch(e: Exception) {
      println("REAL_MANDI_ONION_NO_DISTRICT_HTTP_ERROR: ${e.message}")
    }
  }
}
