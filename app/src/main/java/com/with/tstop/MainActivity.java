package com.with.tstop;

import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private MapView mapView;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;
    private NaverMap naverMap;
    private EditText destinationEditText;
    private Marker destinationMarker;
    private PathOverlay pathOverlay;
    private ConstraintLayout calling;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        String UN = getIntent().getStringExtra("userNumber");
        // 네이버 지도
        mapView = findViewById(R.id.map_view);
        calling = findViewById(R.id.calling);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference("call");

        destinationEditText = findViewById(R.id.et_destination);
        Button searchButton = findViewById(R.id.btn_search);
        Button routeButton = findViewById(R.id.route_button);
        TextView textView = findViewById(R.id.CN);
        textView.setText(UN);
        routeButton.setVisibility(View.INVISIBLE);
        calling.setVisibility(View.INVISIBLE);

        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    calling.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // 데이터 읽기가 취소될 때 호출되는 메서드
            }
        });

        searchButton.setOnClickListener(v -> {
                routeButton.setVisibility(View.VISIBLE);
                findRoute();
        });
        routeButton.setOnClickListener(v -> {
            if (routeButton.getText().equals("운행 종료")) {
                destinationEditText.setVisibility(View.VISIBLE);
                searchButton.setVisibility(View.VISIBLE);
                routeButton.setVisibility(View.INVISIBLE);
                routeButton.setText("내비게이션 실행");
                destinationMarker.setMap(null);
                destinationMarker = null;
                pathOverlay.setMap(null);
                pathOverlay = null;
                Location lastLocation = locationSource.getLastLocation();
                double latitude = lastLocation.getLatitude();
                double longitude = lastLocation.getLongitude();
                LatLng currentPosition = new LatLng(latitude, longitude);
                CameraPosition cameraPosition = new CameraPosition(currentPosition, 17, 0, 0);
                CameraUpdate cameraUpdate = CameraUpdate.toCameraPosition(cameraPosition);
                naverMap.moveCamera(cameraUpdate);

            }

            if (destinationMarker == null) {
                Toast.makeText(this, "도착지를 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            else{
                routeButton.setText("운행 종료");
                destinationEditText.setText("");
                Location lastLocation = locationSource.getLastLocation();
                double latitude = lastLocation.getLatitude();
                double longitude = lastLocation.getLongitude();

                LatLng currentPosition = new LatLng(latitude, longitude);
                CameraPosition cameraPosition = new CameraPosition(currentPosition, 17, 55, 0);
                CameraUpdate cameraUpdate = CameraUpdate.toCameraPosition(cameraPosition);
                naverMap.moveCamera(cameraUpdate);
                destinationEditText.setVisibility(View.INVISIBLE);
                searchButton.setVisibility(View.INVISIBLE);


            }

            LatLng startPoint = new LatLng(locationSource.getLastLocation());
            LatLng endPoint = destinationMarker.getPosition();

            DirectionsTask directionsTask = new DirectionsTask();
            directionsTask.execute(startPoint, endPoint);

        });
    }

    private void findRoute() {
        if (locationSource.getLastLocation() == null) {
            Toast.makeText(this, "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        LatLng startPoint = new LatLng(locationSource.getLastLocation());
        String destinationAddress = destinationEditText.getText().toString();

        GeocodeTask geocodeTask = new GeocodeTask();
        geocodeTask.execute(destinationAddress, startPoint);
    }


    private class GeocodeTask extends AsyncTask<Object, Void, LatLng> {
        @Override
        protected LatLng doInBackground(Object... params) {
            String address = (String) params[0];

            // 도착지 주소를 좌표로 변환
            return geocode(address);
        }

        protected void onPostExecute(LatLng endPoint) {
            super.onPostExecute(endPoint);
            if (endPoint != null) {
                if (destinationMarker != null) {
                    destinationMarker.setMap(null);
                }
                destinationMarker = new Marker();
                destinationMarker.setPosition(endPoint);
                destinationMarker.setMap(naverMap);
                double destinationLatitude = endPoint.latitude;
                double destinationLongitude = endPoint.longitude;
                CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(destinationLatitude, destinationLongitude))
                        .animate(CameraAnimation.Easing);
                naverMap.moveCamera(cameraUpdate);
            } else {
                Toast.makeText(MainActivity.this, "도착지 좌표를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        }

    }


    private LatLng geocode(String address) {
        try {
            address = URLEncoder.encode(address, "UTF-8");
            String apiUrl = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + address;
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-NCP-APIGW-API-KEY-ID","haxn8rbegk");
            conn.setRequestProperty("X-NCP-APIGW-API-KEY","tWej6VthnYhWkIsLv43WFX2n9ywhE1YoIE9BN02v");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = conn.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = bufferedReader.readLine()) != null) {
                    response.append(line);
                }
                bufferedReader.close();

                return parseCoordinatesFromResponse(response.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("MainActivity", "Error: " + e.getMessage());
        }
        return null;
    }

    private LatLng parseCoordinatesFromResponse(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray addressesArray = jsonObject.getJSONArray("addresses");
            if (addressesArray.length() > 0) {
                JSONObject addressObject = addressesArray.getJSONObject(0);
                double latitude = addressObject.getDouble("y");
                double longitude = addressObject.getDouble("x");
                return new LatLng(latitude, longitude);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class DirectionsTask extends AsyncTask<LatLng, Void, String> {

        protected String doInBackground(LatLng... params) {
            LatLng start = params[0];
            LatLng end = params[1];

            return getDirections(start, end);
        }

        protected void onPostExecute(String response) {
            super.onPostExecute(response);
            drawPath(response);
        }
    }

    private String getDirections(LatLng start, LatLng end) {
        try {
            String apiUrl = "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving?start=" +
                    start.longitude + "," + start.latitude + "&goal=" + end.longitude + "," + end.latitude + "&option=trafast";
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-NCP-APIGW-API-KEY-ID","haxn8rbegk");
            conn.setRequestProperty("X-NCP-APIGW-API-KEY","tWej6VthnYhWkIsLv43WFX2n9ywhE1YoIE9BN02v");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = conn.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = bufferedReader.readLine()) != null) {
                    response.append(line);
                }
                bufferedReader.close();

                return response.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("MainActivity", "Error: " + e.getMessage());
        }
        return null;
    }

    private void drawPath(String response) {
        if (pathOverlay != null) {
            pathOverlay.setMap(null);
            pathOverlay = null;
        }
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray steps = jsonObject.getJSONObject("route")
                    .getJSONArray("trafast")
                    .getJSONObject(0)
                    .getJSONArray("path");

            List<LatLng> path = new ArrayList<>();
            for (int i = 0; i < steps.length(); i++) {
                JSONArray coordinates = steps.getJSONArray(i);
                double latitude = coordinates.getDouble(1);
                double longitude = coordinates.getDouble(0);
                path.add(new LatLng(latitude, longitude));
            }

            addPath(path);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }
    private void addPath(List<LatLng> path) {
        pathOverlay = new PathOverlay();
        pathOverlay.setCoords(path);
        pathOverlay.setColor(Color.RED);
        pathOverlay.setWidth(40);
        pathOverlay.setPatternImage(OverlayImage.fromResource(R.drawable.path_patter));
        pathOverlay.setPatternInterval(100);
        pathOverlay.setMap(naverMap);

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        LocationOverlay locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
        locationOverlay.setIconWidth(60);
        locationOverlay.setIconHeight(60);

        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        naverMap.getUiSettings().setRotateGesturesEnabled(false);

        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(false);
        uiSettings.setTiltGesturesEnabled(false);
        uiSettings.setLocationButtonEnabled(true);
    }



    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}


