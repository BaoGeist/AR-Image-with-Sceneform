package com.google.ar.sceneform.samples.gltf;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collection;

public class MainActivity extends AppCompatActivity implements
        FragmentOnAttachListener,
//        BaseArFragment.OnTapArPlaneListener,
        BaseArFragment.OnSessionConfigurationListener,
        ArFragment.OnViewCreatedListener {

    // Abstracted version of AR capabilities so you don't need to manage an AR sssion and camera configuration manually
    private ArFragment arFragment;
    // 3D Models  that you can render and display
    private Renderable model;
    // 2D Models/xml's that you can render and display
    private ViewRenderable viewRenderable;

    Session mSession;

    public static final Config.PlaneFindingMode off = Config.PlaneFindingMode.DISABLED;

    private boolean modelAdded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Where all the AR will be displayed
        setContentView(R.layout.activity_main);

        // First part returns a fragment manager that interacts with the activitiy's fragments, second part adds a listener
        getSupportFragmentManager().addFragmentOnAttachListener(this);

        if (savedInstanceState == null) {
            // Checks if Sceneform can run and is supported on the device
            if (Sceneform.isSupported(this)) {
                // Similar to sessions
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.arFragment, ArFragment.class, null)
                        .commit();
            }
        }



//         Loads the models
//        loadModels();
    }

    // Overidden when a fragment is attached to the activity's FragmentManager
    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        // Checks that the two id's match
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (ArFragment) fragment;
            // Sets listeners to this class to use its override methods
            arFragment.setOnSessionConfigurationListener(this);
            arFragment.setOnViewCreatedListener(this);

//            arFragment.setOnTapArPlaneListener(this);
        }
    }


    // Part of SessionConfiurationListener, called by an AR fragment when the AR session is being configured
    @Override
    public void onSessionConfiguration(Session session, Config config) {
        // Sets depth mode of the session's configuration to AUTOMATIC
        config.setPlaneFindingMode(off);
        mSession = session;
        if (!setupAugmentedImageDb(config)) {
            Toast.makeText(this, "Unable to setup augmented", Toast.LENGTH_SHORT).show();
        }
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        }
    }

    // Part of OnViewCreatedListener, called when the ARFragment's view (ArSceneView) has been created
    @Override
    public void onViewCreated(ArSceneView arSceneView) {
        // Removes the listener once its purpose has been served. Common practice(?)
        arFragment.setOnViewCreatedListener(null);

        // Fine adjust the maximum frame rate
        arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL);

        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
    }

    public void loadModels() {
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        ModelRenderable.builder()
                .setSource(this, Uri.parse("https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb"))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept(model -> {
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.model = model;
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(
                            this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });
        // Building a 3D renderable from an Android View Layout resource, and handles it as so
        // The builder
        ViewRenderable.builder()
                // Specifies the view to be used
                .setView(this, R.layout.view_model_title)
                .build()
                // Callback method that is executed once the ViewRenderable is built
                .thenAccept(viewRenderable -> {
                    // Retrieves the MainActivity instance from the weak reference 'weakActivity'
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.viewRenderable = viewRenderable;
                    }
                })
                // Error handling
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    private boolean setupAugmentedImageDb(Config config) {
        AugmentedImageDatabase augmentedImageDatabase;
        Bitmap augmentedImageBitmap = loadAugmentedImage();
        if (augmentedImageBitmap == null) {
            return false;
        }
        augmentedImageDatabase = new AugmentedImageDatabase(mSession);
        augmentedImageDatabase.addImage("default", augmentedImageBitmap);
        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }

    private Bitmap loadAugmentedImage(){
        try (InputStream is = getAssets().open("defaul.jpg")){
            return BitmapFactory.decodeStream(is);
        }
        catch (IOException e){
            Log.e("ImageLoad", "IO Exception while loading", e);
        }
        return null;
    }

    private void onUpdateFrame(FrameTime frameTime){
        Frame frame = arFragment.getArSceneView().getArFrame();

        Collection<AugmentedImage> augmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage augmentedImage : augmentedImages){
            if (augmentedImage.getTrackingState() == TrackingState.TRACKING){

                if (augmentedImage.getName().contains("default") && !modelAdded){
                    renderObject(arFragment, augmentedImage.createAnchor(augmentedImage.getCenterPose()));
                    modelAdded = true;
                }
            }
        }

    }

    private void renderObject(ArFragment fragment, Anchor anchor){
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        ViewRenderable.builder()
                // Specifies the view to be used
                .setView(this, R.layout.view_model_title)
                .build()
                // Callback method that is executed once the ViewRenderable is built
                .thenAccept(viewRenderable -> {
                    // Retrieves the MainActivity instance from the weak reference 'weakActivity'
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.viewRenderable = viewRenderable;
                        addNodeToScene(fragment, anchor, viewRenderable);
                    }
                })
                // Error handling
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    private void addNodeToScene(ArFragment fragment, Anchor anchor, ViewRenderable viewRenderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        float verticalShift = -10;
        anchorNode.setLocalPosition(new Vector3(0f, verticalShift, 0f));

        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        node.setEnabled(false);
        node.setRenderable(viewRenderable);
        node.setParent(anchorNode);
        node.setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0f, 0f), -90f));


        float scale = 0.2f;
        node.setLocalScale(new Vector3(scale, scale, scale));

        node.select();
        node.setEnabled(true);
    }

}
