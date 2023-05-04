package com.example.indoortracking;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.indoortracking.common.floorselector.FloorSelectorView;
import com.example.indoortracking.databinding.ActivityMapBinding;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import es.situm.sdk.SitumSdk;
import es.situm.sdk.directions.DirectionsRequest;
import es.situm.sdk.error.Error;
import es.situm.sdk.location.LocationListener;
import es.situm.sdk.location.LocationManager;
import es.situm.sdk.location.LocationRequest;
import es.situm.sdk.location.LocationStatus;
import es.situm.sdk.location.util.CoordinateConverter;
import es.situm.sdk.model.cartography.Building;
import es.situm.sdk.model.cartography.BuildingInfo;
import es.situm.sdk.model.cartography.Floor;
import es.situm.sdk.model.cartography.Poi;
import es.situm.sdk.model.cartography.Point;
import es.situm.sdk.model.directions.Route;
import es.situm.sdk.model.directions.RouteSegment;
import es.situm.sdk.model.location.CartesianCoordinate;
import es.situm.sdk.model.location.Coordinate;
import es.situm.sdk.model.location.Location;
import es.situm.wayfinding.LibrarySettings;
import es.situm.wayfinding.OnPoiSelectionListener;
import es.situm.wayfinding.SitumMapView;
import es.situm.wayfinding.SitumMapsLibrary;

public class mapActivity extends AppCompatActivity implements OnMapReadyCallback {
    private SitumMapsLibrary mapsLibrary = null;
    private LibrarySettings librarySettings;
    private SitumMapView mapsView=null;
    private String buildingId = "13347";
    private String targetFloorId = "42059";
    private Boolean routesFound=false;
    private LocationManager locationManager;
    private Coordinate targetCoordinate =
            new Coordinate(30.919247, 75.831841);
    private List<Polyline> polylines = new ArrayList<>();
    private CoordinateConverter coordinateConverter;
    private commons commons;
    private boolean isNavigating = false;
    private boolean arrived=false;
    private GoogleMap googleMap;
    private LatLng current;
    private LocationListener locationListener;
    private ActivityMapBinding binding;
    private Circle circle;
    // To store the building information after we retrieve it
    private BuildingInfo buildingInfo_;
    private GetPoisUseCase getPoisUseCase;
    private GetPoiCategoryIconUseCase getPoiCategoryIconUseCase = new GetPoiCategoryIconUseCase();
    private ProgressBar progressBar;
    private Point pointOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        commons = new commons();
        enableLoc();
        SitumSdk.init(mapActivity.this);
      //  loadMap();
        load_map2();
        manageLogic();
    }

    private void manageLogic() {
        //googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 25));
    }

    private void load_map2() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        locationManager=SitumSdk.locationManager();
    }

    private void loadMap() {
        locationManager=SitumSdk.locationManager();
        librarySettings = new LibrarySettings();
        librarySettings.setMinZoom(25);
        librarySettings.setHasSearchView(false);
        librarySettings.setApiKey(commons.getAPI_EMAIL(), commons.getAPI_KEY());
        librarySettings.setUserPositionIcon("situm_position_icon.png");
        librarySettings.setUserPositionArrowIcon("itum_position_icon_arrow.png");
        mapsLibrary = new SitumMapsLibrary(R.id.maps_library_target, this, librarySettings);
        mapsLibrary.load();



        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mapsLibrary.enableOneBuildingMode(buildingId);
                mapsLibrary.startPositioning(buildingId);
                startLocation();
                librarySettings.setShowNavigationIndications(true);
            }
        },500);

    }

    private void showPOIRoutes() {
        mapsLibrary.setOnPoiSelectionListener(new OnPoiSelectionListener() {
            @Override
            public void onPoiSelected(Poi poi, Floor floor, Building building) {
                Toast.makeText(mapActivity.this, "POI selected: "+poi.getName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPoiDeselected(Building building) {
                routesFound=false;
            }
        });
    }


    private void startLocation() {

        if (locationManager.isRunning()) {
            return;
        }
        locationListener = new LocationListener(){

            @Override
            public void onLocationChanged(@NonNull Location location) {
                //place marker on map when location loaded!
                LatLng latLng = new LatLng(location.getCoordinate().getLatitude(),
                        location.getCoordinate().getLongitude());
                current=latLng;
                if (circle == null) {
                    circle = googleMap.addCircle(new CircleOptions()
                            .center(latLng)
                            .radius(.9f)
                            .strokeWidth(6f)
                                    .zIndex(9)
                                    .strokeColor(getResources().getColor(R.color.white))
                            .fillColor(getResources().getColor(R.color.redPrimary)));

                    //create route to all the pois
                }else{
                    circle.setCenter(latLng);
                }
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 25));
                hideProgress();
                SitumSdk.communicationManager().fetchIndoorPOIsFromBuilding(buildingId, new es.situm.sdk.utils.Handler<Collection<Poi>>() {
                    @Override
                    public void onSuccess(Collection<Poi> pois) {
                        for(Poi poi: pois) {
                            if(latLng!=null) {
                                calculateRoute(latLng, poi.getCoordinate(), poi.getCartesianCoordinate());
                            }
                            }

                    }

                    @Override
                    public void onFailure(Error error) {

                    }
                });

            }


            @Override
            public void onStatusChanged(@NonNull LocationStatus locationStatus) {
            }

            @Override
            public void onError(@NonNull Error error) {
            }
        };
        LocationRequest locationRequest = new LocationRequest.Builder()
                .useWifi(true)
                .useBle(true)
                .useForegroundService(true)
                .build();
        locationManager.requestLocationUpdates(locationRequest, locationListener);

    }
    private void stopLocation() {
        if (!locationManager.isRunning()) {
            return;
        }
        locationManager.removeUpdates(locationListener);
    }


    private void getPois(final GoogleMap googleMap){
        getPoisUseCase.get(new GetPoisUseCase.Callback() {
            @Override
            public void onSuccess(Building building, Collection<Poi> pois) {
                if (pois.isEmpty()){
                    Toast.makeText(mapActivity.this, "There isnt any poi in the building: " + building.getName() + ". Go to the situm dashboard and create at least one poi before execute again this example", Toast.LENGTH_LONG).show();
                }else {
                    for (final Poi poi : pois) {
                        getPoiCategoryIconUseCase.getUnselectedIcon(poi, new GetPoiCategoryIconUseCase.Callback() {
                            @Override
                            public void onSuccess(Bitmap bitmap) {
                                drawPoi(poi, bitmap);
                            }

                            @Override
                            public void onError(String error) {
                                Log.d("Error fetching poi icon", error);
                                drawPoi(poi);
                            }
                        });
                    }

                }
            }

            private void drawPoi(Poi poi, Bitmap bitmap) {
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                LatLng latLng = new LatLng(poi.getCoordinate().getLatitude(),
                        poi.getCoordinate().getLongitude());
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(latLng)
                        .title(poi.getName());
                if (bitmap != null) {
                    markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
                }
                googleMap.addMarker(markerOptions);
                builder.include(latLng);
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
            }

            private void drawPoi(Poi poi) {
                drawPoi(poi, null);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(mapActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void hideProgress() {
                binding.progressBar2.setVisibility(View.GONE);
                binding.mapsLibraryTarget.setVisibility(View.VISIBLE);
                binding.emrLayout.setVisibility(View.VISIBLE);
    }

    private void showProgress() {
        binding.progressBar2.setVisibility(View.VISIBLE);
    }

    private void enableLoc() {

        com.google.android.gms.location.LocationRequest locationRequest = com.google.android.gms.location.LocationRequest.create();
        locationRequest.setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(30 * 1000);
        locationRequest.setFastestInterval(5 * 1000);


        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        builder.setAlwaysShow(true);

        Task<LocationSettingsResponse> result =
                LocationServices.getSettingsClient(this).checkLocationSettings(builder.build());

        result.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {


            @Override
            public void onComplete(Task<LocationSettingsResponse> task) {
                try {
                    LocationSettingsResponse response = task.getResult(ApiException.class);
                    // All location settings are satisfied. The client can initialize location
                    // requests here.

                } catch (ApiException exception) {
                    switch (exception.getStatusCode()) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied. But could be fixed by showing the
                            // user a dialog.
                            try {
                                // Cast to a resolvable exception.
                                ResolvableApiException resolvable = (ResolvableApiException) exception;
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                resolvable.startResolutionForResult(
                                        mapActivity.this,
                                        101);
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        startActivity(new Intent(mapActivity.this, ARActivity.class));
                                        finish();
                                        overridePendingTransition(R.anim.slide_in_left,R.anim.slide_out_left);
                                    }
                                },1200);
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                            } catch (ClassCastException e) {
                                // Ignore, should be an impossible error.
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            break;
                    }
                }
            }
        });

    }

    @Override
    public void onBackPressed() {
        if (mapsLibrary != null) {
            if (mapsLibrary.onBackPressed()) {
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        this.googleMap = googleMap;
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.setBuildingsEnabled(false);
        googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                        this, R.raw.google_map_style));


        SitumSdk.communicationManager().fetchBuildingInfo(buildingId, new es.situm.sdk.utils.Handler<BuildingInfo>() {
            @Override
            public void onSuccess(BuildingInfo buildingInfo) {
                coordinateConverter = new CoordinateConverter(buildingInfo.getBuilding().getDimensions(),
                        buildingInfo.getBuilding().getCenter(),buildingInfo.getBuilding().getRotation());
                buildingInfo_=buildingInfo;
               FloorSelectorView floorSelectorView = findViewById(R.id.situm_floor_selector);
                floorSelectorView.setFloorSelector(buildingInfo.getBuilding(), googleMap, targetFloorId);
                getPoisUseCase= new GetPoisUseCase(buildingInfo.getBuilding());
                getPois(googleMap);
                startLocation();

            }

            @Override
            public void onFailure(Error error) {

            }
        });

    }

    private void calculateRoute(LatLng current, Coordinate destination, CartesianCoordinate _cartesianDestination) {
        pointOrigin = createPoint(current);
        Point pointDestination =  new Point(buildingId,targetFloorId,destination,_cartesianDestination);
        DirectionsRequest directionsRequest = new DirectionsRequest.Builder()
                .from(pointOrigin, null)
                .to(pointDestination)
                .build();
        SitumSdk.directionsManager().requestDirections(directionsRequest, new es.situm.sdk.utils.Handler<Route>() {
            @Override
            public void onSuccess(Route route) {
                drawRoute(route);
                centerCamera(route);
                hideProgress();
                pointOrigin = null;
            }

            @Override
            public void onFailure(Error error) {
                hideProgress();
                clearMap();
                pointOrigin = null;
                Toast.makeText(mapActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void clearMap(){
        removePolylines();
    }

    private Point createPoint(LatLng latLng) {
        Coordinate coordinate = new Coordinate(latLng.latitude, latLng.longitude);
        CartesianCoordinate cartesianCoordinate= coordinateConverter.toCartesianCoordinate(coordinate);
        Point point = new Point(buildingId, targetFloorId,coordinate,cartesianCoordinate );
        return point;
    }

    private void removePolylines() {
        for (Polyline polyline : polylines) {
            polyline.remove();
        }
        polylines.clear();
    }

    private void drawRoute(Route route) {
        for (RouteSegment segment : route.getSegments()) {
            //For each segment you must draw a polyline
            //Add an if to filter and draw only the current selected floor
            List<LatLng> latLngs = new ArrayList<>();
            for (Point point : segment.getPoints()) {
                latLngs.add(new LatLng(point.getCoordinate().getLatitude(), point.getCoordinate().getLongitude()));
            }

            PolylineOptions polyLineOptions = new PolylineOptions()
                    .color(getResources().getColor(R.color.black))
                    .width(12f)
                    .addAll(latLngs);
            polylines.add(googleMap.addPolyline(polyLineOptions));
        }
    }

    private void centerCamera(Route route) {
        Coordinate from = route.getFrom().getCoordinate();
        Coordinate to = route.getTo().getCoordinate();

        LatLngBounds.Builder builder = new LatLngBounds.Builder()
                .include(new LatLng(from.getLatitude(), from.getLongitude()))
                .include(new LatLng(to.getLatitude(), to.getLongitude()));
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
    }
    @Override
    public void finish() {
        getPoisUseCase.cancel();
        stopLocation();
        super.finish();
    }
}
