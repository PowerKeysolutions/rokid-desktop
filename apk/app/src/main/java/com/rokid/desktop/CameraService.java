package com.rokid.desktop;

import android.app.*;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import androidx.core.app.NotificationCompat;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class CameraService extends Service {

    static final String NUC_HOST = "192.168.1.146";
    static final int NUC_PORT = 8082;
    private static final String CHANNEL_ID = "cam";

    private CameraDevice camera;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
    private volatile boolean running = true;
    private HandlerThread cameraThread;
    private Handler cameraHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(2, buildNotification());
        cameraThread = new HandlerThread("cam-bg");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        openCamera();
        startPushLoop();
    }

    private void openCamera() {
        CameraManager mgr = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String id = mgr.getCameraIdList()[0];
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                Image img = reader.acquireLatestImage();
                if (img == null) return;
                ByteBuffer buf = img.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buf.remaining()];
                buf.get(bytes);
                latestFrame.set(bytes);
                img.close();
            }, cameraHandler);

            mgr.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) { camera = d; startPreview(); }
                @Override public void onDisconnected(CameraDevice d) { d.close(); }
                @Override public void onError(CameraDevice d, int e) { d.close(); }
            }, cameraHandler);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void startPreview() {
        try {
            CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(imageReader.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            camera.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(CameraCaptureSession s) {
                        captureSession = s;
                        try { s.setRepeatingRequest(b.build(), null, cameraHandler); }
                        catch (Exception e) { e.printStackTrace(); }
                    }
                    @Override public void onConfigureFailed(CameraCaptureSession s) {}
                }, cameraHandler);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void startPushLoop() {
        new Thread(() -> {
            while (running) {
                try (Socket sock = new Socket()) {
                    sock.connect(new InetSocketAddress(NUC_HOST, NUC_PORT), 3000);
                    DataOutputStream out = new DataOutputStream(sock.getOutputStream());
                    while (running && !sock.isClosed()) {
                        byte[] frame = latestFrame.get();
                        if (frame == null) { Thread.sleep(33); continue; }
                        out.writeInt(frame.length);
                        out.write(frame);
                        out.flush();
                        Thread.sleep(33);
                    }
                } catch (Exception e) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private Notification buildNotification() {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Camara", NotificationManager.IMPORTANCE_LOW));
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gestos activos")
            .setContentText("Cámara → NUC")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onDestroy() {
        super.onDestroy();
        running = false;
        if (captureSession != null) captureSession.close();
        if (camera != null) camera.close();
        if (imageReader != null) imageReader.close();
        cameraThread.quitSafely();
    }
}
