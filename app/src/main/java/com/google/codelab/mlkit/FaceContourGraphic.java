package com.google.codelab.mlkit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.Pair;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Graphic instance for rendering face contours graphic overlay view.
 */
public class FaceContourGraphic extends GraphicOverlay.Graphic {

    private static final float FACE_POSITION_RADIUS = 10.0f;
    private static final float ID_TEXT_SIZE = 70.0f;
    private static final float ID_Y_OFFSET = 80.0f;
    private static final float ID_X_OFFSET = -70.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private static final int[] COLOR_CHOICES = {
            Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.RED, Color.WHITE, Color.YELLOW
    };
    private static int currentColorIndex = 0;

    private final Paint facePositionPaint;
    private final Paint idPaint;
    private final Paint boxPaint;

    private int foreheadWidth = 0;
    private int foreheadHeight = 0;
    private PointF foreheadPontF = null;


    private ArrayList<PointF> foreheadPoints = new ArrayList<>();

    private volatile Face face;
    private PointDrawListener listener;

    public FaceContourGraphic(GraphicOverlay overlay, PointDrawListener listener) {
        super(overlay);
        this.listener = listener;
        currentColorIndex = (currentColorIndex + 1) % COLOR_CHOICES.length;
        final int selectedColor = COLOR_CHOICES[currentColorIndex];

        facePositionPaint = new Paint();
        facePositionPaint.setColor(selectedColor);

        idPaint = new Paint();
        idPaint.setColor(selectedColor);
        idPaint.setTextSize(ID_TEXT_SIZE);

        boxPaint = new Paint();
        boxPaint.setColor(selectedColor);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
    }

    /**
     * Updates the face instance from the detection of the most recent frame. Invalidates the relevant
     * portions of the overlay to trigger a redraw.
     */
    public void updateFace(Face face) {
        this.face = face;
        postInvalidate();
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Face face = this.face;
        if (face == null) {
            return;
        }

        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getBoundingBox().centerX());
        float y = translateY(face.getBoundingBox().centerY());
        canvas.drawCircle(x, y, FACE_POSITION_RADIUS, facePositionPaint);
//        canvas.drawText("id: " + face.getTrackingId(), x + ID_X_OFFSET, y + ID_Y_OFFSET, idPaint);

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getBoundingBox().width() / 2.0f);
        float yOffset = scaleY(face.getBoundingBox().height() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset;
        canvas.drawRect(left, top, right, bottom, boxPaint);

        foreheadWidth = face.getBoundingBox().width();

        List<FaceContour> contour = face.getAllContours();
//        for (FaceContour faceContour : contour) {
//            for (PointF point : faceContour.getPoints()) {
//                float px = translateX(point.x);
//                float py = translateY(point.y);
//                canvas.drawCircle(px, py, FACE_POSITION_RADIUS, facePositionPaint);
//            }
        for (int i = 0; i < contour.size(); i++) {
            for (int j = 0; j < contour.get(i).getPoints().size(); j++) {
                if (i == 0 && (j <= 6 || j >= 30)) {
//                    if (j == 30) {
//                        foreheadHeight = ((int) contour.get(i).getPoints().get(j).y) - (int) top;
//                    }
                    if (j == 35) {
                        foreheadPontF = contour.get(i).getPoints().get(j);
                    }
                    foreheadPoints.add(contour.get(i).getPoints().get(j));
                }
                if (i == 1 || i == 3) {
                    foreheadPoints.add(contour.get(i).getPoints().get(j));
                }
//                if (i == 0 && j == 30) {
//                    startX = translateX(contour.get(i).getPoints().get(j).x);
//                    startY = translateY(contour.get(i).getPoints().get(j).y);
//                }
//                if (i == 1 && j == 0) {
//                    stopX = translateX(contour.get(i).getPoints().get(j).x);
//                    stopY = translateY(contour.get(i).getPoints().get(j).y);
//                }
//                float px = translateX(contour.get(i).getPoints().get(j).x);
//                float py = translateY(contour.get(i).getPoints().get(j).y);
//                canvas.drawCircle(px, py, FACE_POSITION_RADIUS, facePositionPaint);
            }
        }
//        }

//        canvas.drawLine(startX, startY, stopX, stopY, boxPaint);

        for (PointF points : foreheadPoints) {
            float px = translateX(points.x);
            float py = translateY(points.y);
            canvas.drawCircle(px, py, FACE_POSITION_RADIUS, facePositionPaint);
        }

        if (face.getSmilingProbability() != null) {
            canvas.drawText(
                    "happiness: " + String.format("%.2f", face.getSmilingProbability()),
                    x + ID_X_OFFSET * 3,
                    y - ID_Y_OFFSET,
                    idPaint);
        }

        if (face.getRightEyeOpenProbability() != null) {
            canvas.drawText(
                    "right eye: " + String.format("%.2f", face.getRightEyeOpenProbability()),
                    x - ID_X_OFFSET,
                    y,
                    idPaint);
        }
        if (face.getLeftEyeOpenProbability() != null) {
            canvas.drawText(
                    "left eye: " + String.format("%.2f", face.getLeftEyeOpenProbability()),
                    x + ID_X_OFFSET * 6,
                    y,
                    idPaint);
        }
        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
        if (leftEye != null) {
            canvas.drawCircle(
                    translateX(leftEye.getPosition().x),
                    translateY(leftEye.getPosition().y),
                    FACE_POSITION_RADIUS,
                    facePositionPaint);
        }
        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);
        if (rightEye != null) {
            canvas.drawCircle(
                    translateX(rightEye.getPosition().x),
                    translateY(rightEye.getPosition().y),
                    FACE_POSITION_RADIUS,
                    facePositionPaint);
        }

        FaceLandmark leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK);
        if (leftCheek != null) {
            canvas.drawCircle(
                    translateX(leftCheek.getPosition().x),
                    translateY(leftCheek.getPosition().y),
                    FACE_POSITION_RADIUS,
                    facePositionPaint);
        }
        FaceLandmark rightCheek =
                face.getLandmark(FaceLandmark.RIGHT_CHEEK);
        if (rightCheek != null) {
            canvas.drawCircle(
                    translateX(rightCheek.getPosition().x),
                    translateY(rightCheek.getPosition().y),
                    FACE_POSITION_RADIUS,
                    facePositionPaint);
        }
        listener.onSuccess(foreheadPontF);
    }
}

interface PointDrawListener {
    void onSuccess(PointF foreheadPontF);
}
