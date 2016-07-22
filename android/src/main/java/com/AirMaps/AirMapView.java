package com.AirMaps;


import android.graphics.Point;
import android.os.Handler;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.MotionEventCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.util.Log;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;

import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.android.gms.maps.model.TileOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;


public class AirMapView
        extends MapView
        implements
        GoogleMap.InfoWindowAdapter,
        GoogleMap.OnMarkerDragListener,
        OnMapReadyCallback
{
    public GoogleMap map;

    private LatLngBounds boundsToMove;
    private boolean showUserLocation = false;
    private boolean isMonitoringRegion = false;
    private boolean isTouchDown = false;
    private boolean isShowingHeatmap = true;

    private ArrayList<AirMapFeature> features = new ArrayList<>();
    private List<LatLng> focalPoints = new ArrayList<>();
    private HashMap<Marker, AirMapMarker> markerMap = new HashMap<>();
    private HashMap<Polyline, AirMapPolyline> polylineMap = new HashMap<>();
    private HashMap<Polygon, AirMapPolygon> polygonMap = new HashMap<>();
    private HashMap<Circle, AirMapCircle> circleMap = new HashMap<>();

    private ScaleGestureDetector scaleDetector;
    private GestureDetectorCompat gestureDetector;
    private AirMapManager manager;

    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay = null;

    final EventDispatcher eventDispatcher;


    public AirMapView(ThemedReactContext context, AirMapManager manager) {
        super(context);
        this.manager = manager;

        super.onCreate(null);
        super.onResume();
        super.getMapAsync(this);

        final AirMapView view = this;
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
//            @Override
//            public boolean onScale(ScaleGestureDetector detector) {
//                Log.d("AirMapView", "onScale");
//                return false;
//            }

            @Override
            public boolean onScaleBegin (ScaleGestureDetector detector) {
                view.startMonitoringRegion();
                return true; // stop recording this gesture. let mapview handle it.
            }
        });

        gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                view.startMonitoringRegion();
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                view.startMonitoringRegion();
                return false;
            }
        });

        eventDispatcher = context.getNativeModule(UIManagerModule.class).getEventDispatcher();
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        Log.d("AirMapView", "onMapReady");

        this.map = map;
        this.map.setInfoWindowAdapter(this);
        this.map.setOnMarkerDragListener(this);

        manager.pushEvent(this, "onMapReady", new WritableNativeMap());

        final AirMapView view = this;

        map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                WritableMap event;

                event = makeClickEventData(marker.getPosition());
                event.putString("action", "marker-press");
                manager.pushEvent(view, "onMarkerPress", event);

                event = makeClickEventData(marker.getPosition());
                event.putString("action", "marker-press");
                manager.pushEvent(markerMap.get(marker), "onPress", event);

                return false; // returning false opens the callout window, if possible
            }
        });

        map.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition position) {
                Log.d("AirMapView", "on camera change ");

                WritableMap event;

                LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
                event = makeCameraPositionEventData(bounds);
                lastBoundsEmitted = bounds;
                eventDispatcher.dispatchEvent(new RegionChangeEvent(getId(), bounds, isTouchDown));
                view.stopMonitoringRegion();

                manager.pushEvent(view, "onCameraChange", event);
            }
        });

        // TODO: this here needs to change too  17.04.16
        map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                WritableMap event;

                event = makeClickEventData(marker.getPosition());
                event.putString("action", "callout-press");
                manager.pushEvent(view, "onCalloutPress", event);

                event = makeClickEventData(marker.getPosition());
                event.putString("action", "callout-press");
                AirMapMarker markerView = markerMap.get(marker);
                manager.pushEvent(markerView, "onCalloutPress", event);

                event = makeClickEventData(marker.getPosition());
                event.putString("action", "callout-press");
                AirMapCallout infoWindow = markerView.getCalloutView();
                if (infoWindow != null) manager.pushEvent(infoWindow, "onPress", event);
            }
        });

        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng point) {
                WritableMap event = makeClickEventData(point);
                event.putString("action", "press");
                manager.pushEvent(view, "onPress", event);
            }
        });

        map.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng point) {
                WritableMap event = makeClickEventData(point);
                event.putString("action", "long-press");
                manager.pushEvent(view, "onLongPress", makeClickEventData(point));
            }
        });

        // We need to be sure to disable location-tracking when app enters background, in-case some other module
        // has acquired a wake-lock and is controlling location-updates, otherwise, location-manager will be left
        // updating location constantly, killing the battery, even though some other location-mgmt module may
        // desire to shut-down location-services.
        LifecycleEventListener listener = new LifecycleEventListener() {
            @Override
            public void onHostResume() {
                map.setMyLocationEnabled(showUserLocation);
            }

            @Override
            public void onHostPause() {
                map.setMyLocationEnabled(false);
            }

            @Override
            public void onHostDestroy() {

            }
        };

        ((ThemedReactContext) getContext()).addLifecycleEventListener(listener);
    }

    public void setRegion(ReadableMap region) {
        if (region == null) return;

        Double lng = region.getDouble("longitude");
        Double lat = region.getDouble("latitude");
        Double lngDelta = region.getDouble("longitudeDelta");
        Double latDelta = region.getDouble("latitudeDelta");
        LatLngBounds bounds = new LatLngBounds(
                new LatLng(lat - latDelta / 2, lng - lngDelta / 2), // southwest
                new LatLng(lat + latDelta / 2, lng + lngDelta / 2)  // northeast
        );
        if (super.getHeight() <= 0 || super.getWidth() <= 0) {
            // in this case, our map has not been laid out yet, so we save the bounds in a local
            // variable, and make a guess of zoomLevel 10. Not to worry, though: as soon as layout
            // occurs, we will move the camera to the saved bounds. Note that if we tried to move
            // to the bounds now, it would trigger an exception.
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 10));
            boundsToMove = bounds;
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
            boundsToMove = null;
        }
    }

    public void setShowsUserLocation(boolean showUserLocation) {
        this.showUserLocation = showUserLocation; // hold onto this for lifecycle handling
        map.setMyLocationEnabled(showUserLocation);
    }

    public void addFeature(View child, int index) {
        // Our desired API is to pass up annotations/overlays as children to the mapview component.
        // This is where we intercept them and do the appropriate underlying mapview action.
        Log.d("AirMapView", "XXX: adding feature ");

        // Log.d("AirMapView", Log.getStackTraceString(new Exception()));

        if (child instanceof AirMapMarker) {
            AirMapMarker annotation = (AirMapMarker) child;

            if (!this.isShowingHeatmap) {
                // do not add marker to map if we are showing heatmap
                annotation.addToMap(map);
            }

            focalPoints.add(annotation.getPosition());
            features.add(index, annotation);
            Marker marker = (Marker)annotation.getFeature();
            markerMap.put(marker, annotation);
        } else if (child instanceof AirMapPolyline) {
            AirMapPolyline polylineView = (AirMapPolyline) child;
            polylineView.addToMap(map);
            features.add(index, polylineView);
            Polyline polyline = (Polyline)polylineView.getFeature();
            polylineMap.put(polyline, polylineView);
        } else if (child instanceof AirMapPolygon) {
            AirMapPolygon polygonView = (AirMapPolygon) child;
            polygonView.addToMap(map);
            features.add(index, polygonView);
            Polygon polygon = (Polygon)polygonView.getFeature();
            polygonMap.put(polygon, polygonView);
        } else if (child instanceof AirMapCircle) {
            AirMapCircle circleView = (AirMapCircle) child;
            circleView.addToMap(map);
            features.add(index, circleView);
            Circle circle = (Circle)circleView.getFeature();
            circleMap.put(circle, circleView);
        } else {
            // TODO(lmr): throw? User shouldn't be adding non-feature children.
        }
    }

    public int getFeatureCount() {
        return features.size();
    }

    public int getFocalPointCount() {
        return focalPoints.size();
    }

    public View getFeatureAt(int index) {
        return features.get(index);
    }

    public void removeFeatureAt(int index) {
        AirMapFeature feature = features.remove(index);
        feature.removeFromMap(map);

        if (feature instanceof AirMapMarker) {
            markerMap.remove(feature.getFeature());
        } else if (feature instanceof AirMapPolyline) {
            polylineMap.remove(feature.getFeature());
        } else if (feature instanceof AirMapPolygon) {
            polygonMap.remove(feature.getFeature());
        } else if (feature instanceof AirMapCircle) {
            circleMap.remove(feature.getFeature());
        }
    }

    public WritableMap makeCameraPositionEventData(LatLngBounds bounds) {
        // takes bound and returns 3 positions
        WritableMap event = new WritableNativeMap();

        //
        WritableMap center = new WritableNativeMap();
        WritableMap northeast = new WritableNativeMap();
        WritableMap southwest = new WritableNativeMap();

        //
        LatLng boundCenter = bounds.getCenter();
        LatLng boundNortheast = bounds.northeast;
        LatLng boundSouthwest = bounds.southwest;
        // put center
        center.putDouble("latitude", boundCenter.latitude);
        center.putDouble("longitude", boundCenter.longitude);
        event.putMap("center", center);
        // northeast
        northeast.putDouble("latitude", boundNortheast.latitude);
        northeast.putDouble("longitude", boundNortheast.longitude);
        event.putMap("northeast", northeast);
        // southwest
        southwest.putDouble("latitude", boundSouthwest.latitude);
        southwest.putDouble("longitude", boundSouthwest.longitude);
        event.putMap("southwest", southwest);

        return event;
    }

    public WritableMap makeClickEventData(LatLng point) {
        WritableMap event = new WritableNativeMap();

        WritableMap coordinate = new WritableNativeMap();
        coordinate.putDouble("latitude", point.latitude);
        coordinate.putDouble("longitude", point.longitude);
        event.putMap("coordinate", coordinate);

        Projection projection = map.getProjection();
        Point screenPoint = projection.toScreenLocation(point);

        WritableMap position = new WritableNativeMap();
        position.putDouble("x", screenPoint.x);
        position.putDouble("y", screenPoint.y);
        event.putMap("position", position);

        return event;
    }

    public void updateExtraData(Object extraData) {
        // if boundsToMove is not null, we now have the MapView's width/height, so we can apply
        // a proper camera move
        if (boundsToMove != null) {
            HashMap<String, Float> data = (HashMap<String, Float>)extraData;
            float width = data.get("width");
            float height = data.get("height");
            map.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                            boundsToMove,
                            (int)width,
                            (int)height,
                            0
                    )
            );
            boundsToMove = null;
        }
    }

    public void animateToRegion(LatLngBounds bounds, int duration) {
        startMonitoringRegion();
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0), duration, null);
    }

    public void animateToCoordinate(LatLng coordinate, int duration) {
        startMonitoringRegion();
        map.animateCamera(CameraUpdateFactory.newLatLng(coordinate), duration, null);
    }

    public void fitToElements(boolean animated) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (AirMapFeature feature : features) {
            if (feature instanceof AirMapMarker) {
                Marker marker = (Marker)feature.getFeature();
                builder.include(marker.getPosition());
            }
            // TODO(lmr): may want to include shapes / etc.
        }
        LatLngBounds bounds = builder.build();
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, 50);
        if (animated) {
            startMonitoringRegion();
            map.animateCamera(cu);
        } else {
            map.moveCamera(cu);
        }
    }

    // InfoWindowAdapter interface

    @Override
    public View getInfoWindow(Marker marker) {
        AirMapMarker markerView = markerMap.get(marker);
        return markerView.getCallout();
    }

    @Override
    public View getInfoContents(Marker marker) {
        AirMapMarker markerView = markerMap.get(marker);
        return markerView.getInfoContents();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);
        gestureDetector.onTouchEvent(ev);

        int action = MotionEventCompat.getActionMasked(ev);

        switch(action) {
            case (MotionEvent.ACTION_DOWN):
                isTouchDown = true;
                break;
            case (MotionEvent.ACTION_MOVE):
                startMonitoringRegion();
                break;
            case (MotionEvent.ACTION_UP):
                isTouchDown = false;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    // Timer Implementation

    public void startMonitoringRegion() {
        if (isMonitoringRegion) return;
        timerHandler.postDelayed(timerRunnable, 100);
        isMonitoringRegion = true;
    }

    public void stopMonitoringRegion() {
        if (!isMonitoringRegion) return;
        timerHandler.removeCallbacks(timerRunnable);
        isMonitoringRegion = false;
    }

    private LatLngBounds lastBoundsEmitted;

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {

            LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
            if (lastBoundsEmitted == null || LatLngBoundsUtils.BoundsAreDifferent(bounds, lastBoundsEmitted)) {
                lastBoundsEmitted = bounds;
                eventDispatcher.dispatchEvent(new RegionChangeEvent(getId(), bounds, true));
            }

            timerHandler.postDelayed(this, 100);
        }
    };

    @Override
    public void onMarkerDragStart(Marker marker) {
        WritableMap event = makeClickEventData(marker.getPosition());
        manager.pushEvent(this, "onMarkerDragStart", event);

        AirMapMarker markerView = markerMap.get(marker);
        event = makeClickEventData(marker.getPosition());
        manager.pushEvent(markerView, "onDragStart", event);
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        WritableMap event = makeClickEventData(marker.getPosition());
        manager.pushEvent(this, "onMarkerDrag", event);

        AirMapMarker markerView = markerMap.get(marker);
        event = makeClickEventData(marker.getPosition());
        manager.pushEvent(markerView, "onDrag", event);
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        WritableMap event = makeClickEventData(marker.getPosition());
        manager.pushEvent(this, "onMarkerDragEnd", event);

        AirMapMarker markerView = markerMap.get(marker);
        event = makeClickEventData(marker.getPosition());
        manager.pushEvent(markerView, "onDragEnd", event);
    }

    public void reloadHeatmap() {
        Log.d("AirMapView", "reload collection event is invoked");
        Log.d("AirMapView", "feature/focalpoint count " +
              this.getFeatureCount() + " " + this.getFocalPointCount());

        // make sure there are some focal points to build heatmap with
        if (this.getFocalPointCount() > 0) {
            if (this.mOverlay != null)
                this.mOverlay.remove();
            Log.d("AirMapView", "focal point count" + this.getFocalPointCount())

            this.mProvider = new HeatmapTileProvider.Builder()
                                    .data(this.focalPoints)
                                    .build();
            this.mOverlay = this.map.addTileOverlay(
                new TileOverlayOptions().tileProvider(this.mProvider));
        } else {
            Log.d("AirMapView", "no focal points, skip building heatmap");
        }
    }

    public void showMarkers() {
        Log.d("AirMapView", "show markers are  instigated");

        for (AirMapFeature feature : this.features) {
            if (feature instanceof AirMapMarker) {
                feature.addToMap(this.map);
            }
        }
    }

    public void hideMarkers() {
        Log.d("AirMapView", "hiding all markers");

        for (AirMapFeature feature : this.features) {
            if (feature instanceof AirMapMarker) {
                feature.removeFromMap(this.map);
            }
        }
    }

    public void toggleHeatmap() {
        if (this.isShowingHeatmap) {
            this.showMarkers();
            this.isShowingHeatmap = false;
        } else {
            this.hideMarkers();
            this.reloadHeatmap();
            this.isShowingHeatmap = true;
        }
    }

}
