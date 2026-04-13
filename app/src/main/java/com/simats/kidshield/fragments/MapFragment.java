package com.simats.kidshield.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.simats.kidshield.R;
import com.simats.kidshield.adapters.TimelineAdapter;
import com.simats.kidshield.models.ChildProfile;
import com.simats.kidshield.models.LocationResponse;
import com.simats.kidshield.models.TimelineEvent;
import com.simats.kidshield.network.BackendManager;
import com.simats.kidshield.utils.SessionManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment {

    private MapView mapView;
    private RecyclerView rvTimeline;
    private TimelineAdapter timelineAdapter;
    private Spinner spinnerChild;
    private List<ChildProfile> children = new ArrayList<>();
    private List<com.simats.kidshield.models.SafeZone> currentZones = new ArrayList<>();
    private int selectedChildId = -1;
    private int parentId;
    private boolean isFirstLoad = true;
    private Marker currentMarker;
    
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (selectedChildId != -1) {
                loadLocation(selectedChildId);
                loadTimeline(selectedChildId);
            }
            handler.postDelayed(this, 30000); // Polling every 30 seconds
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        parentId = SessionManager.getInstance(requireContext()).getParentId();

        mapView = view.findViewById(R.id.map_view);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        rvTimeline = view.findViewById(R.id.rv_timeline);
        timelineAdapter = new TimelineAdapter();
        rvTimeline.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTimeline.setAdapter(timelineAdapter);

        spinnerChild = view.findViewById(R.id.spinner_child_select);

        loadChildren();
    }

    private void loadChildren() {
        if (parentId == -1) return;
        BackendManager.getChildren(parentId, new BackendManager.ApiCallback<List<ChildProfile>>() {
            @Override
            public void onSuccess(List<ChildProfile> result) {
                if (!isAdded()) return;
                children = result;
                List<String> names = new ArrayList<>();
                for (ChildProfile c : result) names.add(c.getName());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerChild.setAdapter(adapter);
                spinnerChild.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                        selectedChildId = children.get(pos).getId();
                        isFirstLoad = true;
                        loadLocation(selectedChildId);
                        loadTimeline(selectedChildId);
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
                if (!result.isEmpty()) {
                    selectedChildId = result.get(0).getId();
                    loadLocation(selectedChildId);
                    loadTimeline(selectedChildId);
                }
            }
            @Override public void onError(String error) {}
        });
    }

    private void loadLocation(int childId) {
        BackendManager.getLatestLocation(childId, new BackendManager.ApiCallback<LocationResponse>() {
            @Override
            public void onSuccess(LocationResponse result) {
                if (!isAdded()) return;
                GeoPoint point = new GeoPoint(result.getLatitude(), result.getLongitude());
                
                if (isFirstLoad) {
                    mapView.getController().setCenter(point);
                    isFirstLoad = false;
                }
                
                if (currentMarker == null) {
                    currentMarker = new Marker(mapView);
                    currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    currentMarker.setTitle("Child Location");
                    mapView.getOverlays().add(currentMarker);
                }
                
                currentMarker.setPosition(point);
                loadSafeZonesOnMap(childId);
                mapView.invalidate();
            }
            @Override public void onError(String error) {}
        });
    }

    private void loadSafeZonesOnMap(int childId) {
        BackendManager.getSafeZones(childId, new BackendManager.ApiCallback<List<com.simats.kidshield.models.SafeZone>>() {
            @Override
            public void onSuccess(List<com.simats.kidshield.models.SafeZone> zones) {
                if (!isAdded()) return;
                currentZones = zones;
                
                // Clear existing zone circles (keeping the child marker)
                List<org.osmdroid.views.overlay.Overlay> toKeep = new ArrayList<>();
                if (currentMarker != null) toKeep.add(currentMarker);
                mapView.getOverlays().clear();
                mapView.getOverlays().addAll(toKeep);

                for (com.simats.kidshield.models.SafeZone zone : zones) {
                    org.osmdroid.views.overlay.Polygon circle = new org.osmdroid.views.overlay.Polygon(mapView);
                    circle.setPoints(org.osmdroid.views.overlay.Polygon.pointsAsCircle(
                            new GeoPoint(zone.getLatitude(), zone.getLongitude()), 
                            zone.getRadius()));
                    circle.setFillColor(android.graphics.Color.argb(40, 16, 185, 129)); // semi-transparent green
                    circle.setStrokeColor(android.graphics.Color.parseColor("#10B981"));
                    circle.setStrokeWidth(2f);
                    circle.setTitle(zone.getName());
                    mapView.getOverlays().add(0, circle); // Add at bottom
                }
                mapView.invalidate();
            }
            @Override public void onError(String error) {}
        });
    }

    private void loadTimeline(int childId) {
        BackendManager.getTimelineEvents(childId, new BackendManager.ApiCallback<List<TimelineEvent>>() {
            @Override
            public void onSuccess(List<TimelineEvent> result) {
                if (!isAdded()) return;
                timelineAdapter.setEvents(result);
            }
            @Override public void onError(String error) {}
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 30000);
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        handler.removeCallbacks(refreshRunnable);
    }
}
