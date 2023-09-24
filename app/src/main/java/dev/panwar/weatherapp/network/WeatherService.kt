package dev.panwar.weatherapp.network

import dev.panwar.weatherapp.models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
// we are just completing the link after base url...see retrofit doc
//    this is our required url //    https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid={API key}
//    return type is call....it gets data from website and format according to our weather response data class
    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat:Double,
        @Query("lon") lon:Double,
        @Query("units") units:String?,
        @Query("app_id") appid:String?
    ) : Call<WeatherResponse>
}