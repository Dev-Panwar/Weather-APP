package dev.panwar.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import dev.panwar.weatherapp.databinding.ActivityMainBinding
import dev.panwar.weatherapp.models.WeatherResponse
import dev.panwar.weatherapp.network.WeatherService
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    //    for getting latitude and longitude location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog:Dialog?=null
    private var binding:ActivityMainBinding?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)
//     Location services we are using is due to implementation that we added in gradle
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()){
//            if location is not enabled than we need not check Location Permission
            Toast.makeText(this,"Your Location Provider is turned off. Please turn it on",Toast.LENGTH_SHORT).show()
// Starting intent which makes user go to Location settings
            val intent=Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        else{
//            Checking location permission using Dexter
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
//                        if all permissions granted
                        if (report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }
//                        if any permission not granted
                        if (report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,"You have denied Location Permission, please enable as it is mandatory for app to provide weather Data",Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>,
                        token: PermissionToken
                    ) {
                        showRationaleDialogueForPermissions()
                    }

                }).onSameThread().check()

        }

    }
    //  for requesting location data
    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
//    requesting location
        val mLocationRequest = com.google.android.gms.location.LocationRequest()
//    setting up the accuracy for our location
        mLocationRequest.priority=com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())//looper.myLooper refreshes to get most accurate Location

    }
    // for getting current location
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult!!.lastLocation
            val Latitude = mLastLocation!!.latitude
            val Longitude = mLastLocation!!.longitude
            Log.i("Current latitute", "$Latitude")
            Log.i("Current longitude", "$Longitude")
            getLocationWeatherDetails(Latitude,Longitude)
        }
    }

    // when permission denied we say why we require permission in this rationale
    private fun showRationaleDialogueForPermissions() {
        AlertDialog.Builder(this).setMessage("It looks Like you have turned off permission requested. It can be Enabled in App Permission settings ")
            .setPositiveButton("Go to settings"){_,_ ->
                try {
                    val intent=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//                    to open Settings of this particular app we are setting path
                    val uri=Uri.fromParts("package",packageName,null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e:ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel"){dialog,_ ->
                dialog.dismiss()
            }.show()
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if (Constants.isNetworkAvailable(this@MainActivity)){
//           Toast.makeText(this,"You have connected to internet. Now you can make API calls",Toast.LENGTH_SHORT).show()
//           Using Retrofit to retrieve data from Open weather api...see retrofit documentation..
//           we made a retrofit object based on base url and converted it into gson format...we got this function from dependency we have added containing retrofit and gson
            val retrofit:Retrofit= Retrofit.Builder().baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()

            val service:WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
// function in Weather Service interface
            val listCall: Call<WeatherResponse> = service.getWeather(latitude,longitude,Constants.METRIC_UNITS,Secrets.APP_ID)
//           we show a progress dialog while we receive data from open weather api website
            showCustomDialog()
//           here we are actually downloading the weather data and enqueueing
            listCall.enqueue(object : Callback<WeatherResponse>{
                //               generated function of Callback interface
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){
                        hideProgressDialog()
                        val weatherList:WeatherResponse=response.body()!!
                        setupUI(weatherList)
                        Log.i("Response Result","$weatherList")
                    }
                    else{
//                       rc=responseCode
                        val rc=response.code()
                        when(rc){
                            400-> Log.e("Error 400","Bad Connection")
                            404-> Log.e("Error 404","Not Found")
                            else-> Log.e("Error","Generic Error")
                        }
                    }
                }
                //               generated function of Callback interface
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrr", t.message.toString())
                    hideProgressDialog()
                }

            })
        }
        else{
            Toast.makeText(this,"No Internet Connection Available",Toast.LENGTH_SHORT).show()
        }
    }

    //    to check if location is enabled or not
    private fun isLocationEnabled(): Boolean{
//    provides access to system location services
        val locationManager:LocationManager=getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    private fun showCustomDialog(){
        mProgressDialog=Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()

    }

    private fun hideProgressDialog(){
        if (mProgressDialog!=null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(weatherList:WeatherResponse){
        for(i in weatherList.weather.indices){
            Log.i("Weather Name",weatherList.weather.toString())

            binding?.tvMain?.text=weatherList.weather[i].main
            binding?.tvMainDescription?.text=weatherList.weather[i].description
//            getUnit gets unit(cel or feh) according to location of user
            binding?.tvTemp?.text=weatherList.main.temp.toString()+getUnit(application.resources.configuration.locales.toString())

            binding?.tvSunriseTime?.text=unixTime(weatherList.sys.sunrise)
            binding?.tvSunsetTime?.text=unixTime(weatherList.sys.sunset)

            binding?.tvHumidity?.text=weatherList.main.humidity.toString()+" per cent"
            binding?.tvMin?.text=weatherList.main.temp_min.toString()+" min"
            binding?.tvMax?.text=weatherList.main.temp_max.toString()+" max"
            binding?.tvSpeed?.text=weatherList.wind.speed.toString()
            binding?.tvName?.text=weatherList.name
            binding?.tvCountry?.text=weatherList.sys.country


            when(weatherList.weather[i].icon){
//                these icons code from from open weather api doc
                "01d"-> binding?.ivMain?.setImageResource(R.drawable.sunny)
                "02d"-> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03d"-> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04d"-> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04n"-> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10d"-> binding?.ivMain?.setImageResource(R.drawable.rain)
                "11d"-> binding?.ivMain?.setImageResource(R.drawable.storm)
                "13d"-> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "01n"-> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "02n"-> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03n"-> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10n"-> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "11n"-> binding?.ivMain?.setImageResource(R.drawable.rain)
                "13n"-> binding?.ivMain?.setImageResource(R.drawable.snowflake)

            }


        }
    }
    private fun getUnit(value:String):String?{
        var value="°C"
        if ("US"==value || "LR"==value || "MM"==value) {
            value = "°F"
        }
        return value
    }

    //    for getting sunrise and sunset time....as api gives a Long code that we need to covert to time
    private fun unixTime(timex:Long):String?{
//    we get the date from code
        val date=Date(timex*1000L)
//    we set format for date
        val sdf=SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone= TimeZone.getDefault()
//    we format and return the data
        return sdf.format(date)
    }
}