package com.kevinscannell.apps.forecast;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity implements LocationListener{
    private final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private final int FORECAST_DAYS = 5;    //In case we ever want to increase the look ahead
    private final Context ctx = this;
    private boolean hasLocationPermission;
    private LocationManager mLocationManager;
    private Location weatherLocation = null;
    private double latitude; // latitude
    private double longitude; // longitude
    private AlertDialog adChangeLocation;

    private class dayWeather{
        private double temperature = 0;
        private double highTemp = 0;
        private double lowTemp = 0;
        private double humidity = 0.0d;
        private double precipChance = 0.0d;
        private String summary = "Sunny";
        private Drawable icon;

        dayWeather(){
            icon = getDrawable(R.drawable.unknown);
        }

        double getTemperature() {
            return temperature;
        }

        void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        double getHumidity() {
            return humidity;
        }

        void setHumidity(double humidity) {
            this.humidity = humidity;
        }

        double getPrecipChance() {
            return precipChance;
        }

        void setPrecipChance(double precipChance) {
            this.precipChance = precipChance;
        }

        String getSummary() {
            return summary;
        }

        void setSummary(String summary) {
            this.summary = summary;
        }

        Drawable getIcon() {
            return icon;
        }

        void setIcon(Drawable icon) {
            this.icon = icon;
        }

        double getHighTemp() {
            return highTemp;
        }

        void setHighTemp(double highTemp) {
            this.highTemp = highTemp;
        }

        double getLowTemp() {
            return lowTemp;
        }

        void setLowTemp(double lowTemp) {
            this.lowTemp = lowTemp;
        }
    }
    private final dayWeather forecast[] = new dayWeather[FORECAST_DAYS];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        for( int f = 0; f < FORECAST_DAYS; f ++ ){
            forecast[f] = new dayWeather();
        }

        ImageButton ibLocationChange = findViewById(R.id.btn_changeLocation);
        ibLocationChange.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if( adChangeLocation == null ) {
                    //Display a menu to change location
                    AlertDialog.Builder newLocationAlertBuilder = new AlertDialog.Builder(ctx);
                    LayoutInflater layoutInflater = ((Activity) ctx).getLayoutInflater();
                    final ConstraintLayout clRoot = (ConstraintLayout) layoutInflater.inflate(R.layout.location_change, null);
                    newLocationAlertBuilder.setTitle(getString(R.string.choose_coords));
                    newLocationAlertBuilder.setView(clRoot);

                    final EditText etCoords = clRoot.findViewById(R.id.et_coords);
                    final Geocoder geocoder = new Geocoder(ctx);
                    try {
                        List<Address> currentAddress = geocoder.getFromLocation(latitude, longitude, 1);
                        String currentZipCode = currentAddress.get(0).getPostalCode();
                        etCoords.setText(currentZipCode);
                        Button btnUseCurrentLocation = clRoot.findViewById(R.id.btn_currentLocation);
                        btnUseCurrentLocation.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ((MainActivity) ctx).getWeatherForLocation(getCurrentLocation());
                                if (adChangeLocation != null) {
                                    adChangeLocation.dismiss();
                                }
                            }
                        });

                        Button btnUseNewLocation = clRoot.findViewById(R.id.btn_newLocation);
                        btnUseNewLocation.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Location coordLocation = new Location("");
                                String newZip = etCoords.getText().toString();
                                try {
                                    List<Address> newAddress = geocoder.getFromLocationName(newZip, 1);
                                    if( newAddress.size() < 1 ){
                                        Toast.makeText(ctx, "Invalid Zip Code", Toast.LENGTH_LONG).show();
                                    } else {
                                        coordLocation.setLatitude(newAddress.get(0).getLatitude());
                                        coordLocation.setLongitude(newAddress.get(0).getLongitude());
                                        if (coordLocation.getLatitude() > 90 || coordLocation.getLatitude() < -90 ||
                                                coordLocation.getLongitude() > 180 || coordLocation.getLongitude() < -180) {
                                            Toast.makeText(ctx, "Invalid Co-ordinates", Toast.LENGTH_LONG).show();
                                        } else {
                                            ((MainActivity) ctx).getWeatherForLocation(coordLocation);
                                            if (adChangeLocation != null) {
                                                adChangeLocation.dismiss();
                                            }
                                        }
                                    }
                                } catch (IOException ioe) {
                                    ioe.printStackTrace();
                                }
                            }
                        });

                        Button btnCancel = clRoot.findViewById(R.id.btn_cancelLocation);
                        btnCancel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (adChangeLocation != null) {
                                    adChangeLocation.dismiss();
                                }
                            }
                        });

                        adChangeLocation = newLocationAlertBuilder.show();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                } else {
                    adChangeLocation.show();
                }
            }
        });
    }

    @Override
    protected void onStart(){
        super.onStart();
        //Make sure we have location permission
        checkLocationPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults){
        switch (requestCode ){
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    hasLocationPermission = true;
                    try{
                        weatherLocation = getCurrentLocation();
                    } catch (SecurityException se){
                        se.printStackTrace();
                    }
                } else {
                    weatherLocation = null;
                }
                break;
        }
        getWeatherForLocation(weatherLocation);
    }

    private Location getCurrentLocation() throws SecurityException{
        if( hasLocationPermission ) {
            return mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } else {
            checkLocationPermission();
            return null;
        }
    }

    private void getWeatherForLocation(Location loc){
        if(loc == null){
            //Get Boston
            loc = new Location("");
            loc.setLatitude(42.3601d);
            loc.setLongitude(-71.0589d);
        }

        latitude = loc.getLatitude();
        longitude = loc.getLongitude();
        String weatherRequestURLString = "https://api.darksky.net/forecast/db9ed0b4c97272da05ab6c0944b24d05/"
                + latitude + "," + longitude + "?units=us&exclude=minutely,hourly,alerts,flags";
        new weatherURLTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, weatherRequestURLString);
    }

    private void checkLocationPermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED){
        //Ask for location permission
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } else {
            hasLocationPermission = true;
            try{
                weatherLocation = getCurrentLocation();
            } catch (SecurityException se){
                se.printStackTrace();
            }
            getWeatherForLocation(weatherLocation);
        }
    }

    private void updateWeatherDisplay(){
        //Setup decimal format for later
        DecimalFormat df = new DecimalFormat(".##");

        //Display location
        TextView tvLocation = findViewById(R.id.tv_Location);
        Geocoder geocoder = new Geocoder(this);
        try {
            List<Address> currentAddress = geocoder.getFromLocation(latitude, longitude, 1);
            tvLocation.setText(currentAddress.get(0).getPostalCode());
        } catch (IOException ioe){
            ioe.printStackTrace();
            tvLocation.setText(getString(R.string.app_name));
        }

                //Display today's info:
        dayWeather day = forecast[0];
        //Current temp
        TextView temp = findViewById(R.id.tv_currentTemp);
        String ctString = Double.toString(day.getTemperature()) + "F";
        temp.setText(ctString);

        //Current summary
        TextView condition = findViewById(R.id.tv_currentCondition);
        condition.setText(day.getSummary());

        //Current Humidity
        TextView humidity = findViewById(R.id.tv_currentHumidity);
        Double humPercent = day.getHumidity() * 100;
        String humidityString = df.format(humPercent) + "% Humidity";
        humidity.setText(humidityString);

        //Today's precip chance
        TextView precip = findViewById(R.id.tv_currentPrecip);
        Double precipPercent = day.getPrecipChance() * 100;
        String precipChanceString = df.format(precipPercent) + "% Chance of Precip.";
        precip.setText(precipChanceString);

        //Draw the appropriate picture
        ImageView icon = findViewById(R.id.iv_currentConditions);
        icon.setBackground(null);
        icon.setBackground(day.getIcon());

        //Now loop over the array and update the forecast:
        for( int f = 0; f < FORECAST_DAYS; f ++ ){
            day = forecast[f];
            String dayString = "day" + f;

            //Display weather icon
            String imageViewName = "iv_" + dayString + "_picture";
            icon = findViewById(getResources().getIdentifier(imageViewName,
                    "id", getPackageName()));
            icon.setBackground(null);
            icon.setBackground(day.getIcon());

            //Display day's high temp
            String highName = "tv_" + dayString + "_high";
            TextView highTemp = findViewById(getResources().getIdentifier(highName,
                    "id", getPackageName()));
            String highTempString = "High: " + Double.toString(day.getHighTemp()) + "F";
            highTemp.setText(highTempString);

            //Display day's low temp
            String lowName = "tv_" + dayString + "_low";
            TextView lowTemp = findViewById(getResources().getIdentifier(lowName,
                    "id", getPackageName()));
            String lowTempString = "Low: " + Double.toString(day.getLowTemp()) + "F";
            lowTemp.setText(lowTempString);

            //Display day's precip chance
            String precipName = "tv_" + dayString + "_precip";
            precip = findViewById(getResources().getIdentifier(precipName,
                    "id", getPackageName()));
            precipPercent = day.getPrecipChance() * 100;
            precipChanceString = df.format(precipPercent) + "% Chance precip.";
            precip.setText(precipChanceString);
        }
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private class weatherURLTask extends AsyncTask<String, Void, String>{
        @Override
        protected String doInBackground(String... params) {
            String urlString = params[0];
            String jsonString = "";
            HttpsURLConnection conn = null;
            try{
                URL weatherUrl = new URL(urlString);
                conn = (HttpsURLConnection)(weatherUrl.openConnection());
                int resCode = conn.getResponseCode();
                if( resCode == HttpsURLConnection.HTTP_OK ) {
                    try {
                        BufferedReader br = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder(1024);
                        String line;
                        while((line = br.readLine()) != null ){
                            sb.append(line).append("\n");
                        }
                        br.close();
                        jsonString = sb.toString();
                    } catch (IOException ioe){
                        ioe.printStackTrace();
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }finally {
                if( conn != null ){
                    conn.disconnect();
                }
            }
            return jsonString;
        }

        @Override
        protected void onPostExecute(String result) {
            //With the result, parse out today's forecast, and the coming days.
            if( result == null || result.equals("") ) return;
            try {
                parseWeather(new JSONObject(result));
            } catch (JSONException je){
                je.printStackTrace();
            }
        }

        void parseWeather(JSONObject jsonObject){
            try {

                //Get forecast
                JSONArray dailyArray = jsonObject.getJSONObject("daily").getJSONArray("data");

                //Get live conditions
                jsonObject = jsonObject.getJSONObject("currently");
                //Get today's information
                dayWeather today = forecast[0];
                today.setSummary(jsonObject.getString("summary"));
                today.setTemperature(jsonObject.getDouble("temperature"));
                today.setHumidity(jsonObject.getDouble("humidity"));
                setIcon(0, jsonObject.getString("icon"));

                //Special case for today, we need to get just the high and low
                JSONObject todayForecast = dailyArray.getJSONObject(0);
                today.setHighTemp(todayForecast.getDouble("temperatureHigh"));
                today.setLowTemp(todayForecast.getDouble("temperatureLow"));
                today.setPrecipChance(todayForecast.getDouble("precipProbability"));

                for( int f = 1; f < FORECAST_DAYS; f ++ ){
                    JSONObject day = dailyArray.getJSONObject(f);
                    dayWeather weather = forecast[f];
                    if( day != null && weather != null ){
                        weather.setSummary(day.getString("summary"));
                        weather.setHighTemp(day.getDouble("temperatureHigh"));
                        weather.setLowTemp(day.getDouble("temperatureLow"));
                        weather.setPrecipChance(day.getDouble("precipProbability"));
                        setIcon(f, day.getString("icon"));
                    }
                }

                //Finally, display our data:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateWeatherDisplay();
                    }
                });
            } catch (JSONException je){
                je.printStackTrace();
            }
        }

        private void setIcon(int dayIndex, String iconName){
            //For various descriptions, choose an appropriate icon
            if( dayIndex >= forecast.length ) return;
            dayWeather day = forecast[dayIndex];

            switch (iconName) {
                case "sunny":
                case "clear-day":
                case "clear-night":
                    day.setIcon(getDrawable(R.drawable.sunny));
                    break;
                case "partly-cloudy-day":
                case "partly-cloudy-night":
                    day.setIcon(getDrawable(R.drawable.partly_cloudy));
                    break;
                case "cloudy":
                    day.setIcon(getDrawable(R.drawable.cloudy));
                    break;
                case "rain":
                    day.setIcon(getDrawable(R.drawable.rainy));
                    break;
                default:
                    day.setIcon(getDrawable(R.drawable.unknown));
            }
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }
}