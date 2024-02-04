
package com.google.codelab.mlkit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ImageView mImageView;
    private Button mFaceButton;
    private Button Camera;
    private Bitmap mSelectedImage;
    private Bitmap mForeheadImage;
    private GraphicOverlay mGraphicOverlay;
    // Max width (portrait mode)
    private Integer mImageMaxWidth;
    // Max height (portrait mode)
    private Integer mImageMaxHeight;
    private ImageView foreheadImageView;
    private Uri imageUri = null;
    private String skinType = "Mixed";


    /**
     * Number of results to show in the UI.
     */
    private static final int RESULTS_TO_SHOW = 3;
    /**
     * Dimensions of inputs.
     */
    int pixelValue = 0;

    private final PriorityQueue<Map.Entry<String, Float>> sortedLabels = new PriorityQueue<>(RESULTS_TO_SHOW, new Comparator<Map.Entry<String, Float>>() {
        @Override
        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
            return (o1.getValue()).compareTo(o2.getValue());
        }
    });


    @SuppressLint("MissingInflatedId")
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = findViewById(R.id.image_view);
        foreheadImageView = findViewById(R.id.forheadIv);

        mFaceButton = findViewById(R.id.button_face);

        mGraphicOverlay = findViewById(R.id.graphic_overlay);
        Camera = findViewById(R.id.button_camera);
        mFaceButton.setOnClickListener(view -> {
            if (mFaceButton.getText().toString().equals(getString(R.string.check_moisture))) {
                showDialog();
            } else {
                if (mSelectedImage != null) runFaceContourDetection();
            }

        });
        Camera.setOnClickListener(v -> {
            mFaceButton.setText(getString(R.string.find_face_contour_button));
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
            } else {
                openCameraInterface();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
                openCameraInterface();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == Activity.RESULT_OK) {
            try {
                mSelectedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                mSelectedImage = ImageModifier.rotateImageIfRequired(this, mSelectedImage, imageUri);
                resizeImageToTarget();
            } catch (IOException e) {
                showToast(e.getMessage());
                e.printStackTrace();
            }
        }
    }


    private void runFaceContourDetection() {
        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        FaceDetectorOptions options = new FaceDetectorOptions.Builder().setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).build();

        mFaceButton.setEnabled(false);
        FaceDetector detector = FaceDetection.getClient(options);
        detector.process(image).addOnSuccessListener(faces -> {
            mFaceButton.setEnabled(true);
            processFaceContourDetectionResult(faces);
        }).addOnFailureListener(e -> {
            // Task failed with an exception
            mFaceButton.setEnabled(true);
            e.printStackTrace();
        });

    }

    private void processFaceContourDetectionResult(List<Face> faces) {
        // Task completed successfully
        if (faces.isEmpty()) {
            showToast("No face found");
            return;
        }
        mGraphicOverlay.clear();
        for (int i = 0; i < faces.size(); ++i) {
            Face face = faces.get(i);
            final FaceContourGraphic faceGraphic = new FaceContourGraphic(mGraphicOverlay, foreheadPontF -> {
                if (foreheadPontF != null)
                    mForeheadImage = Bitmap.createBitmap(mSelectedImage, (int) foreheadPontF.x, (int) foreheadPontF.y, 80, 50);
                if (mForeheadImage != null) {
                    getPixelValue();
                    saveMediaToStorage();
                }
            });
            mGraphicOverlay.add(faceGraphic);
            faceGraphic.updateFace(face);
        }
    }

    private void saveMediaToStorage() {
        OutputStream fos = null;
        String filename = System.currentTimeMillis() + ".jpg";

        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {

                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename + ".jpg");

                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg");
                Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri);
                }
                mForeheadImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);

                if (fos != null) {
                    //showToast(imageUri.toString());
                    // showToast("Image saved successfully");
                }
            } else {
                //These for devices running on android < Q
                File imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File image = new File(imagesDir, filename);
                fos = new FileOutputStream(image);

                //Finally writing the bitmap to the output stream that we opened
                mForeheadImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                showToast(Uri.fromFile(image).toString());
            }
        } catch (Exception e) {
            showToast(e.getMessage());
            Log.d("error", e.toString());
        }
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Functions for loading images from app assets.

    // Returns max image width, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private Integer getImageMaxWidth() {
        if (mImageMaxWidth == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxWidth = mImageView.getWidth();
        }

        return mImageMaxWidth;
    }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private Integer getImageMaxHeight() {
        if (mImageMaxHeight == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxHeight = mImageView.getHeight();
        }

        return mImageMaxHeight;
    }

    // Gets the targeted width / height.
    private Pair<Integer, Integer> getTargetedWidthHeight() {
        int targetWidth;
        int targetHeight;
        int maxWidthForPortraitMode = getImageMaxWidth();
        int maxHeightForPortraitMode = getImageMaxHeight();
        targetWidth = maxWidthForPortraitMode;
        targetHeight = maxHeightForPortraitMode;
        return new Pair<>(targetWidth, targetHeight);
    }


    private void resizeImageToTarget() {
        mGraphicOverlay.clear();
        if (mSelectedImage != null) {
            // Get the dimensions of the View
            Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

            int targetWidth = targetedSize.first;
            int maxHeight = targetedSize.second;

            // Determine how much to scale down the image
            float scaleFactor = Math.max((float) mSelectedImage.getWidth() / (float) targetWidth, (float) mSelectedImage.getHeight() / (float) maxHeight);

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(mSelectedImage, (int) (mSelectedImage.getWidth() / scaleFactor), (int) (mSelectedImage.getHeight() / scaleFactor), true);

            mImageView.setImageBitmap(resizedBitmap);
            mSelectedImage = resizedBitmap;
        }
    }

    private void openCameraInterface() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "take picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "take picture from camera");
        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        // Create camera intent
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);

        // Launch intent
        startActivityForResult(intent, 101);
    }

    // to get the pixel value and show oily and dry

    private void getPixelValue() {
        int graySum = 0;
        double a = 850;
        double b = 1099.04;
        int height = mForeheadImage.getHeight();
        int width = mForeheadImage.getWidth();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixels = mForeheadImage.getPixel(x, y);
                graySum = graySum + ((Color.red(pixels) + Color.blue(pixels) + Color.green(pixels)) / 3);
            }
        }
        int avgGray = graySum / (height * width);
        float limit = avgGray + ((float) ((255 - avgGray) * 5 / 100));
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixels = mForeheadImage.getPixel(x, y);
                if ((float) ((Color.red(pixels) + Color.blue(pixels) + Color.green(pixels)) / 3) >= limit) {
                    pixelValue = pixelValue + 1;
                }
            }
        }

        if (this.pixelValue < a) {
            skinType = "Dry";
        } else if (this.pixelValue > b) {
            skinType = "Oily";
        } else {
            skinType = "Mixed";
        }
        mFaceButton.setText(getString(R.string.check_moisture));
    }

    public void showDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.moisture_view);
        TextView text = dialog.findViewById(R.id.skinTypeTv);
        TextView detectingTv = dialog.findViewById(R.id.detectingTv);
        text.setText(String.format(getString(R.string.your_skin_is), skinType));
        Group group = dialog.findViewById(R.id.group);
        Button okayButton = dialog.findViewById(R.id.okayBtn);
        okayButton.setOnClickListener(v ->
                dialog.dismiss()
        );
        dialog.show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (group != null && detectingTv != null) {
                group.setVisibility(View.VISIBLE);
                detectingTv.setVisibility(View.GONE);
            }

        }, 5000);
    }
}
