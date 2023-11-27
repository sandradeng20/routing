package uk.ac.cam.cares.jps.routing;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.mapbox.bindgen.Expected;
import com.mapbox.bindgen.None;
import com.mapbox.bindgen.Value;
import com.mapbox.geojson.Point;
import com.mapbox.maps.LayerPosition;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;
import com.mapbox.maps.plugin.Plugin;
import com.mapbox.maps.plugin.annotation.Annotation;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.AnnotationType;
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationDragListener;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import dagger.hilt.android.AndroidEntryPoint;
import uk.ac.cam.cares.jps.routing.databinding.FragmentMapBinding;


@AndroidEntryPoint
public class RoutingFragment extends Fragment {
    private Logger LOGGER = Logger.getLogger(RoutingFragment.class);
    private FragmentMapBinding binding;
    private MapView mapView;
    private RoutingViewModel viewModel;
    private Point startPoint = Point.fromLngLat(0.44503308060457725, 52.75554119773284);
    private Point endPoint = Point.fromLngLat(0.43026936435893426, 52.76964201123162);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater);
//        var nameInput = findViewById(R.id.nameInput
        mapView  = binding.getRoot().findViewById(R.id.mapView);
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS);
        viewModel = new ViewModelProvider(requireActivity()).get(RoutingViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        addMarker(startPoint, getResources().getColor(R.color.start_marker_color));
        addMarker(endPoint, getResources().getColor(R.color.end_marker_color));
        viewModel.getRouteData(startPoint.longitude(), startPoint.latitude(), endPoint.longitude(), endPoint.latitude());
        viewModel.getRouteGeoJsonData().observe(getViewLifecycleOwner(), data -> {
            mapView.getMapboxMap().getStyle(style -> {
                Expected<String, None> removeLayerSuccess =  style.removeStyleLayer("route_layer");
                LOGGER.debug("route: source created " + (removeLayerSuccess.isError() ? removeLayerSuccess.getError() : "success"));
                Expected<String, None> removeSourceSuccess =  style.removeStyleSource("route");
                LOGGER.debug("route: source created " + (removeSourceSuccess.isError() ? removeSourceSuccess.getError() : "success"));

                JSONObject sourceJson = new JSONObject();
                try {
                    sourceJson.put("type", "geojson");
                    sourceJson.put("data", data);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                Expected<String, None> success = style.addStyleSource("route", Objects.requireNonNull(Value.fromJson(sourceJson.toString()).getValue()));
                LOGGER.debug("route: source created " + (success.isError() ? success.getError() : "success"));

                JSONObject layerJson = new JSONObject();
                try {
                    layerJson.put("id", "route_layer");
                    layerJson.put("type", "line");
                    layerJson.put("source", "route");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                Expected<String, None> layerSuccess = style.addStyleLayer(Objects.requireNonNull(Value.fromJson(layerJson.toString()).getValue()), new LayerPosition(null, null, null));
                LOGGER.debug("route: layer created " + (layerSuccess.isError() ? layerSuccess.getError() : "success"));
            });
        });
    }

    private void addMarker(Point point, int color) {
        AnnotationPlugin annotationPlugin = mapView.getPlugin(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID);
        PointAnnotationManager pointAnnotationManager = (PointAnnotationManager) annotationPlugin.createAnnotationManager(AnnotationType.PointAnnotation, null);
        pointAnnotationManager.addDragListener(new OnPointAnnotationDragListener() {
            @Override
            public void onAnnotationDragStarted(@NonNull Annotation<?> annotation) { }

            @Override
            public void onAnnotationDrag(@NonNull Annotation<?> annotation) {
                pointAnnotationManager.update((PointAnnotation) annotation);
            }

            @Override
            public void onAnnotationDragFinished(@NonNull Annotation<?> annotation) {
                viewModel.getRouteData(((PointAnnotation)annotation).getGeometry().longitude(), ((PointAnnotation)annotation).getGeometry().latitude(), point == startPoint);
            }
        });

        PointAnnotationOptions pointAnnotationOptions = new PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(drawableToBitmap(R.drawable.source_marker, color))
                .withIconSize(2.0)
                .withDraggable(true);
        pointAnnotationManager.create(pointAnnotationOptions);
    }

    public Bitmap drawableToBitmap (int drawableId, int color) {
        Drawable drawable = ContextCompat.getDrawable(requireContext(), drawableId);
        drawable.setTint(color);
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }




    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mapView.onDestroy();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
