package vsga.mobile.aplikasimaps;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

import vsga.mobile.aplikasimaps.databinding.ActivityMapsBinding;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;


    //New Variable for Current Place picker
    private static final String TAG = "MapsActivity";
    ListView lstPlaces;
    private PlacesClient mPlacesClient;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;

    // Used for selecting the Current Place.
    private static final int M_MAX_ENTRIES = 5;
    private String[] mLikelyPlaceNames;
    private String[] mLikelyPlaceAddresses;
    private String[] mLikelyPlaceAttributions;
    private LatLng[] mLikelyPlaceLatLngs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Set Up the action toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //set up the views
        lstPlaces = findViewById(R.id.listPlaces);

        //initialize the place client
        String apiKey = getString(R.string.google_maps_key);
        Places.initialize(getApplicationContext(), apiKey);
        mPlacesClient = Places.createClient(this);
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

    }

    //add menu for app bar menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_geolocate:
                //present the current place picker
                pickCurrentPlace();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //Request location permissions after the map is ready
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        mLocationPermissionGranted = false;
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

        //Enable the zoom control for the map
        mMap.getUiSettings().setZoomControlsEnabled(true);

        //Prompt the user for permission
        getLocationPermission();
    }

    //Handle the result of request permission loc
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                //if request cancelled, the result arrays are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
    }

    //Call the findCurrentPlace API and handle the response
    private void getCurrentPlaceLikelihoods() {
        // Use fields to define the data types to return.
        List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME, Place.Field.ADDRESS,
                Place.Field.LAT_LNG);

        // Get the likely places - that is, the businesses and other points of interest that
        // are the best match for the device's current location.
        @SuppressWarnings("MissingPermission") final FindCurrentPlaceRequest request =
                FindCurrentPlaceRequest.builder(placeFields).build();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Task<FindCurrentPlaceResponse> placeResponse = mPlacesClient.findCurrentPlace(request);
        placeResponse.addOnCompleteListener(this,
                task -> {
                    if (task.isSuccessful()) {
                        FindCurrentPlaceResponse response = task.getResult();
                        // Set the count, handling cases where less than 5 entries are returned.
                        int count;
                        if (response.getPlaceLikelihoods().size() < M_MAX_ENTRIES) {
                            count = response.getPlaceLikelihoods().size();
                        } else {
                            count = M_MAX_ENTRIES;
                        }

                        int i = 0;
                        mLikelyPlaceNames = new String[count];
                        mLikelyPlaceAddresses = new String[count];
                        mLikelyPlaceAttributions = new String[count];
                        mLikelyPlaceLatLngs = new LatLng[count];

                        for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                            Place currPlace = placeLikelihood.getPlace();
                            mLikelyPlaceNames[i] = currPlace.getName();
                            mLikelyPlaceAddresses[i] = currPlace.getAddress();
                            mLikelyPlaceAttributions[i] = (currPlace.getAttributions() == null) ?
                                    null : TextUtils.join(" ", currPlace.getAttributions());
                            mLikelyPlaceLatLngs[i] = currPlace.getLatLng();

                            String currLatLng = (mLikelyPlaceLatLngs[i] == null) ?
                                    "" : mLikelyPlaceLatLngs[i].toString();

                            Log.i(TAG, String.format("Place " + currPlace.getName()
                                    + " has likelihood: " + placeLikelihood.getLikelihood()
                                    + " at " + currLatLng));

                            i++;
                            if (i > (count - 1)) {
                                break;
                            }
                        }


                        // COMMENTED OUT UNTIL WE DEFINE THE METHOD
                        // Populate the ListView
                        fillPlacesList();
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof ApiException) {
                            ApiException apiException = (ApiException) exception;
                            Log.e(TAG, "Place not found: " + apiException.getStatusCode());
                        }
                    }
                });
    }

    //Move the map camera to the device's current location
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Set the map's camera position to the current location of the device.
                        mLastKnownLocation = task.getResult();
                        Log.d(TAG, "Latitude: " + mLastKnownLocation.getLatitude());
                        Log.d(TAG, "Longitude: " + mLastKnownLocation.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(mLastKnownLocation.getLatitude(),
                                        mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.");
                        Log.e(TAG, "Exception: %s", task.getException());
                        mMap.moveCamera(CameraUpdateFactory
                                .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                    }

                    getCurrentPlaceLikelihoods();
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    //Check for location permissions when the user clicks Pick Place
    private void pickCurrentPlace(){
        if (mMap == null) {
            return;
        }

        if (mLocationPermissionGranted){
            getDeviceLocation();
        } else {
            // The user has not granted permission.
            Log.i(TAG, "The user did not grant location permission.");

            // Add a default marker, because the user hasn't selected a place.
            mMap.addMarker(new MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(mDefaultLocation)
                    .snippet(getString(R.string.default_info_snippet)));

            // Prompt the user for permission.
            getLocationPermission();
        }
    }

    private AdapterView.OnItemClickListener listClickedHandler = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View v, int position, long id) {
            // position will give us the index of which place was selected in the array
            LatLng markerLatLng = mLikelyPlaceLatLngs[position];
            String markerSnippet = mLikelyPlaceAddresses[position];
            if (mLikelyPlaceAttributions[position] != null) {
                markerSnippet = markerSnippet + "\n" + mLikelyPlaceAttributions[position];
            }

            // Add a marker for the selected place, with an info window
            // showing information about that place.
            mMap.addMarker(new MarkerOptions()
                    .title(mLikelyPlaceNames[position])
                    .position(markerLatLng)
                    .snippet(markerSnippet));

            // Position the map's camera at the location of the marker.
            mMap.moveCamera(CameraUpdateFactory.newLatLng(markerLatLng));
        }
    };

    private void fillPlacesList() {
        // Set up an ArrayAdapter to convert likely places into TextViews to populate the ListView
        ArrayAdapter<String> placesAdapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mLikelyPlaceNames);
        lstPlaces.setAdapter(placesAdapter);
        lstPlaces.setOnItemClickListener(listClickedHandler);
    }
}