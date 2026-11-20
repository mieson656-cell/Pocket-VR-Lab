package app.pocketvrlab;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Typeface;
import android.graphics.Color;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private PocketView pocketView;
    private float yawZero = 0f;
    private float pitchZero = 0f;
    private boolean hasCalibration = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        pocketView = new PocketView(this);
        setContentView(pocketView);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotationSensor == null) rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        pocketView.setSensorAvailable(rotationSensor != null);
    }

    @Override protected void onResume() {
        super.onResume();
        if (rotationSensor != null) sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_GAME_ROTATION_VECTOR &&
            event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        float[] matrix = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, orientation);
        float yaw = orientation[0];
        float pitch = orientation[1];
        if (!hasCalibration) {
            yawZero = yaw;
            pitchZero = pitch;
            hasCalibration = true;
        }
        pocketView.setHeadPose(wrapAngle(yaw - yawZero), wrapAngle(pitch - pitchZero));
    }

    private float wrapAngle(float value) {
        while (value > Math.PI) value -= (float)(2 * Math.PI);
        while (value < -Math.PI) value += (float)(2 * Math.PI);
        return value;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private class PocketView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float yaw = 0f, pitch = 0f, smoothYaw = 0f, smoothPitch = 0f;
        private float sensitivity = 1.0f;
        private boolean sensorAvailable = false;
        private String activeControl = "";
        private long jumpFlashUntil = 0, interactFlashUntil = 0;

        PocketView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setSensorAvailable(boolean value) { sensorAvailable = value; invalidate(); }
        void setHeadPose(float y, float x) { yaw = y; pitch = x; invalidate(); }

        private void paint(int color, float width, Paint.Style style) {
            p.setColor(color); p.setStrokeWidth(width); p.setStyle(style);
        }
        private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
            paint(color, 1, Paint.Style.FILL);
            p.setTextSize(size); p.setTypeface(bold ? Typeface.create("sans-serif", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
            c.drawText(s, x, y, p);
        }
        private void rounded(Canvas c, float l, float t, float r, float b, int color, float radius) {
            paint(color, 1, Paint.Style.FILL); c.drawRoundRect(l,t,r,b,radius,radius,p);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), half = w / 2f;
            smoothYaw += (yaw - smoothYaw) * 0.18f;
            smoothPitch += (pitch - smoothPitch) * 0.18f;
            drawEye(canvas, 0, half, h, -1);
            drawEye(canvas, half, w, h, 1);
            paint(0xFF111B2A, 2, Paint.Style.FILL);
            canvas.drawRect(half - 1, 0, half + 1, h, p);
            drawControls(canvas, w, h);
            if (System.currentTimeMillis() < jumpFlashUntil) text(canvas, "JUMP", w/2f-25, h*0.25f, 20, 0xFF55D6BE, true);
            if (System.currentTimeMillis() < interactFlashUntil) text(canvas, "INTERACT", w/2f-48, h*0.25f, 20, 0xFFFFD166, true);
            postInvalidateDelayed(16);
        }

        private void drawEye(Canvas c, float left, float right, float h, int eye) {
            float width = right-left;
            c.save();
            c.clipRect(left,0,right,h);
            paint(0xFF0A1422, 1, Paint.Style.FILL); c.drawRect(left,0,right,h,p);
            paint(0xFF10283A, 1, Paint.Style.FILL); c.drawRect(left,h*0.58f,right,h,p);
            float shiftX = (float)Math.sin(smoothYaw * sensitivity) * width * 0.22f + eye * 5;
            float shiftY = (float)Math.sin(smoothPitch * sensitivity) * h * 0.17f;
            paint(0xFF18394A, 2, Paint.Style.STROKE);
            for (int i=0;i<9;i++) {
                float x = left + (i+1)*width/10f + shiftX;
                c.drawLine(x,h*0.58f,x + (x-(left+width/2))*0.35f,h,p);
            }
            for (int i=0;i<5;i++) {
                float y = h*0.63f + i*i*7 + shiftY;
                c.drawLine(left,y,right,y,p);
            }
            paint(0xFF55D6BE, 1, Paint.Style.FILL);
            c.drawCircle(left+width*0.78f+shiftX*0.2f,h*0.25f+shiftY*0.2f,18,p);
            paint(0xFFB8F3E8, 2, Paint.Style.STROKE);
            float cx = (left+right)/2f + shiftX, cy = h*0.48f + shiftY;
            c.drawCircle(cx,cy,13,p); c.drawLine(cx-22,cy,cx-7,cy,p); c.drawLine(cx+7,cy,cx+22,cy,p);
            c.drawLine(cx,cy-22,cx,cy-7,p); c.drawLine(cx,cy+7,cx,cy+22,p);
            text(c, eye < 0 ? "LEFT EYE" : "RIGHT EYE", left+18, 28, 11, 0xFF8AA6B8, true);
            text(c, String.format(java.util.Locale.US, "YAW %+.0f  PITCH %+.0f", Math.toDegrees(smoothYaw), Math.toDegrees(smoothPitch)),
                left+18, 47, 10, 0xFF8AA6B8, false);
            c.restore();
        }

        private void button(Canvas c, float l, float t, float r, float b, String label, boolean active) {
            rounded(c,l,t,r,b,active ? 0xFF245C59 : 0xCC142538,16);
            paint(active ? 0xFF75E6D2 : 0xFF426074,2,Paint.Style.STROKE);
            c.drawRoundRect(l,t,r,b,16,16,p);
            p.setTextSize(12); p.setTypeface(Typeface.DEFAULT_BOLD);
            float tw=p.measureText(label);
            text(c,label,(l+r-tw)/2f,(t+b)/2f+4,12,0xFFEAF7FA,true);
        }

        private void drawControls(Canvas c, float w, float h) {
            float bw=Math.min(82,w*0.105f), bh=48;
            button(c,14,h*0.42f,14+bw,h*0.42f+bh,"◀ MOVE",activeControl.equals("LEFT"));
            button(c,14,h*0.42f+bh+10,14+bw,h*0.42f+bh*2+10,"RIGHT ▶",activeControl.equals("RIGHT"));
            button(c,w-bw-14,h*0.42f,w-14,h*0.42f+bh,"TURN ◀",activeControl.equals("TURNL"));
            button(c,w-bw-14,h*0.42f+bh+10,w-14,h*0.42f+bh*2+10,"TURN ▶",activeControl.equals("TURNR"));
            button(c,w/2f-44,h-62,w/2f-6,h-14,"JUMP",activeControl.equals("JUMP"));
            button(c,w/2f+6,h-62,w/2f+50,h-14,"USE",activeControl.equals("USE"));
            button(c,w/2f-62,8,w/2f+62,38,"RECENTER",false);
            button(c,w-104,8,w-14,38,"SENS +",false);
            text(c,sensorAvailable ? "GYRO READY" : "NO ROTATION SENSOR",18,h-12,10,
                sensorAvailable ? 0xFF55D6BE : 0xFFFF7777,true);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY(), w=getWidth(), h=getHeight();
            if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                if ("JUMP".equals(activeControl)) jumpFlashUntil=System.currentTimeMillis()+350;
                if ("USE".equals(activeControl)) interactFlashUntil=System.currentTimeMillis()+350;
                activeControl=""; invalidate(); return true;
            }
            if (e.getAction()==MotionEvent.ACTION_DOWN || e.getAction()==MotionEvent.ACTION_MOVE) {
                if (y<48 && x>w/2f-70 && x<w/2f+70) {
                    yawZero += yaw; pitchZero += pitch; smoothYaw=0; smoothPitch=0; yaw=0; pitch=0;
                    activeControl=""; invalidate(); return true;
                }
                if (y<48 && x>w-110) { sensitivity=Math.min(2.5f,sensitivity+0.1f); activeControl=""; invalidate(); return true; }
                if (y>h-70 && x>w/2f-50 && x<w/2f) activeControl="JUMP";
                else if (y>h-70 && x>=w/2f && x<w/2f+60) activeControl="USE";
                else if (x<115 && y>h*0.35f && y<h*0.55f) activeControl="LEFT";
                else if (x<115 && y>=h*0.55f) activeControl="RIGHT";
                else if (x>w-115 && y>h*0.35f && y<h*0.55f) activeControl="TURNL";
                else if (x>w-115 && y>=h*0.55f) activeControl="TURNR";
                invalidate(); return true;
            }
            return true;
        }
    }
}
