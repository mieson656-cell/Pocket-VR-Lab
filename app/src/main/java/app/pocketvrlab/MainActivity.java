package app.pocketvrlab;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager sensors;
    private Sensor rotationSensor;
    private VrSurface vrSurface;
    private VrRenderer renderer;
    private TextView telemetry;
    private float[] rotation = new float[16];
    private float[] orientation = new float[3];
    private float yaw0, pitch0, roll0;
    private boolean calibrated;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512 | 4096);
        sensors = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotationSensor == null) rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(5, 11, 20));
        vrSurface = new VrSurface(this);
        renderer = new VrRenderer();
        vrSurface.setRenderer(renderer);
        vrSurface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(vrSurface, new LinearLayout.LayoutParams(-1, 0, 1));

        telemetry = new TextView(this);
        telemetry.setTextColor(Color.rgb(113, 235, 215));
        telemetry.setTextSize(12);
        telemetry.setPadding(12, 5, 12, 5);
        telemetry.setGravity(Gravity.CENTER);
        telemetry.setBackgroundColor(Color.rgb(10, 24, 36));
        root.addView(telemetry, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(5, 3, 5, 5);
        controls.setBackgroundColor(Color.rgb(8, 17, 29));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        LinearLayout row2 = new LinearLayout(this);
        LinearLayout row3 = new LinearLayout(this);
        controls.addView(row1); controls.addView(row2); controls.addView(row3);
        addButton(row1, "RECENTER", v -> recenter(), 1);
        addButton(row1, "LEFT HAND", v -> renderer.selectedHand = 0, 1);
        addButton(row1, "RIGHT HAND", v -> renderer.selectedHand = 1, 1);
        addButton(row2, "X −", v -> renderer.moveHand(-0.08f,0,0), 1);
        addButton(row2, "X +", v -> renderer.moveHand(0.08f,0,0), 1);
        addButton(row2, "Y −", v -> renderer.moveHand(0,-0.08f,0), 1);
        addButton(row2, "Y +", v -> renderer.moveHand(0,0.08f,0), 1);
        addButton(row3, "ROT −", v -> renderer.rotateHand(-8), 1);
        addButton(row3, "ROT +", v -> renderer.rotateHand(8), 1);
        addButton(row3, "RESET HANDS", v -> renderer.resetHands(), 1);
        addButton(row3, "IPD", v -> renderer.ipd = renderer.ipd > 0.075f ? 0.055f : renderer.ipd + 0.005f, 1);
        setContentView(root);
        updateTelemetry();
    }

    private void addButton(LinearLayout row, String label, View.OnClickListener click, float weight) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(9); b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(24, 55, 72)));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 42, weight);
        lp.setMargins(2, 1, 2, 1);
        row.addView(b, lp);
    }

    private void recenter() {
        yaw0 = orientation[0]; pitch0 = orientation[1]; roll0 = orientation[2];
        calibrated = true;
        if (renderer != null) renderer.setHead(0,0,0);
    }

    private void updateTelemetry() {
        if (telemetry == null || renderer == null) return;
        telemetry.setText(String.format(Locale.US,
            "HEAD  YAW %+.1f°  PITCH %+.1f°  ROLL %+.1f°     HAND: %s   IPD %.0f mm",
            Math.toDegrees(renderer.headYaw), Math.toDegrees(renderer.headPitch),
            Math.toDegrees(renderer.headRoll), renderer.selectedHand == 0 ? "LEFT" : "RIGHT", renderer.ipd*1000));
        telemetry.postDelayed(this::updateTelemetry, 120);
    }

    @Override protected void onResume() {
        super.onResume();
        if (vrSurface != null) vrSurface.onResume();
        if (rotationSensor != null) sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }
    @Override protected void onPause() {
        if (sensors != null) sensors.unregisterListener(this);
        if (vrSurface != null) vrSurface.onPause();
        super.onPause();
    }
    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_GAME_ROTATION_VECTOR &&
            e.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rotation, e.values);
        SensorManager.getOrientation(rotation, orientation);
        if (!calibrated) { yaw0=orientation[0]; pitch0=orientation[1]; roll0=orientation[2]; calibrated=true; }
        if (renderer != null) renderer.setHead(wrap(orientation[0]-yaw0), wrap(orientation[1]-pitch0), wrap(orientation[2]-roll0));
    }
    private float wrap(float a) {
        while (a > Math.PI) a -= (float)(2*Math.PI);
        while (a < -Math.PI) a += (float)(2*Math.PI);
        return a;
    }
    @Override public void onAccuracyChanged(Sensor s, int a) { }

    private static class VrSurface extends GLSurfaceView {
        VrSurface(Context c) { super(c); setEGLContextClientVersion(2); setPreserveEGLContextOnPause(true); }
    }

    private static class VrRenderer implements GLSurfaceView.Renderer {
        private static final String VERT =
            "attribute vec3 aPosition; uniform mat4 uMvp; void main(){ gl_Position=uMvp*vec4(aPosition,1.0); }";
        private static final String FRAG =
            "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }";
        private int program, posHandle, mvpHandle, colorHandle;
        private FloatBuffer cube;
        private FloatBuffer grid;
        volatile float headYaw, headPitch, headRoll;
        volatile int selectedHand = 0;
        volatile float ipd = 0.064f;
        private final float[][] handPos = {{-0.32f,-0.32f,-1.05f},{0.32f,-0.32f,-1.05f}};
        private final float[] handYaw = {0,0};
        private final float[] handPitch = {0,0};

        void setHead(float y,float p,float r) { headYaw=y; headPitch=p; headRoll=r; }
        void moveHand(float x,float y,float z) {
            int i=selectedHand; handPos[i][0]=clamp(handPos[i][0]+x,-1.1f,1.1f);
            handPos[i][1]=clamp(handPos[i][1]+y,-1.0f,0.6f);
            handPos[i][2]=clamp(handPos[i][2]+z,-2.2f,-0.4f);
        }
        void rotateHand(float a) { handYaw[selectedHand] += a; }
        void resetHands() {
            handPos[0]=new float[]{-0.32f,-0.32f,-1.05f};
            handPos[1]=new float[]{0.32f,-0.32f,-1.05f};
            handYaw[0]=handYaw[1]=handPitch[0]=handPitch[1]=0;
        }
        private float clamp(float v,float lo,float hi) { return Math.max(lo,Math.min(hi,v)); }

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
            GLES20.glClearColor(0.015f,0.035f,0.065f,1);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            program = GLES20.glCreateProgram();
            int vs=shader(GLES20.GL_VERTEX_SHADER,VERT), fs=shader(GLES20.GL_FRAGMENT_SHADER,FRAG);
            GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs); GLES20.glLinkProgram(program);
            posHandle=GLES20.glGetAttribLocation(program,"aPosition");
            mvpHandle=GLES20.glGetUniformLocation(program,"uMvp");
            colorHandle=GLES20.glGetUniformLocation(program,"uColor");
            cube=buffer(new float[]{
                -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, -0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
                -0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f,
                -0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,-0.5f,0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, -0.5f,0.5f,0.5f,
                0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f,
                -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f,
                -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f
            });
            float[] lines=new float[21*2*3]; int k=0;
            for(int i=-10;i<=10;i++) {
                lines[k++]=i*0.25f; lines[k++]=-0.55f; lines[k++]=0;
                lines[k++]=i*0.25f; lines[k++]=-0.55f; lines[k++]=-5;
                lines[k++]=-2.5f; lines[k++]=-0.55f; lines[k++]=i*0.25f-2.5f;
                lines[k++]=2.5f; lines[k++]=-0.55f; lines[k++]=i*0.25f-2.5f;
            }
            grid=buffer(lines);
        }
        private FloatBuffer buffer(float[] a) {
            FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            b.put(a).position(0); return b;
        }
        private int shader(int type,String src) {
            int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,src); GLES20.glCompileShader(s); return s;
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h) { GLES20.glViewport(0,0,w,h); }

        @Override public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            int w=GLES20ViewportWidth(), h=GLES20ViewportHeight();
            int eyeW=w/2;
            drawEye(0,eyeW,h,-1);
            drawEye(eyeW,w-eyeW,h,1);
        }
        private int GLES20ViewportWidth() { int[] v=new int[4]; GLES20.glGetIntegerv(GLES20.GL_VIEWPORT,v,0); return v[2]; }
        private int GLES20ViewportHeight() { int[] v=new int[4]; GLES20.glGetIntegerv(GLES20.GL_VIEWPORT,v,0); return v[3]; }

        private void drawEye(int x,int width,int height,int eye) {
            GLES20.glViewport(x,0,width,height);
            float[] proj=new float[16], view=new float[16], vp=new float[16], model=new float[16], mvp=new float[16];
            float aspect=(float)width/Math.max(1,height);
            Matrix.perspectiveM(proj,0,78,aspect,0.05f,40f);
            float eyeOffset=eye*ipd*0.5f;
            float cy=(float)Math.cos(headYaw), sy=(float)Math.sin(headYaw);
            float cp=(float)Math.cos(headPitch), sp=(float)Math.sin(headPitch);
            float camX=eyeOffset, camY=0, camZ=0;
            float lookX=sy*cp, lookY=-sp, lookZ=-cy*cp;
            Matrix.setLookAtM(view,0,camX,camY,camZ,camX+lookX,camY+lookY,camZ+lookZ,0,1,0);
            Matrix.multiplyMM(vp,0,proj,0,view,0);
            GLES20.glUseProgram(program);
            GLES20.glEnableVertexAttribArray(posHandle);
            drawLines(vp,grid,0xFF234253,84);
            drawCube(vp,0,0.05f,-2.7f,0.7f,0.7f,0.7f,0.12f,0.78f,0.72f);
            drawCube(vp,-1.15f,-0.12f,-2.0f,0.35f,0.55f,0.35f,0.95f,0.55f,0.22f);
            drawCube(vp,1.1f,-0.12f,-2.3f,0.4f,0.4f,0.4f,0.35f,0.55f,0.95f);
            drawController(vp,0);
            drawController(vp,1);
            GLES20.glDisableVertexAttribArray(posHandle);
        }
        private void drawLines(float[] vp,FloatBuffer vertices,int color,int count) {
            Matrix.setIdentityM(new float[16],0);
            GLES20.glUniformMatrix4fv(mvpHandle,1,false,vp,0);
            GLES20.glUniform4f(colorHandle,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,1);
            vertices.position(0); GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,0,vertices);
            GLES20.glLineWidth(1.2f); GLES20.glDrawArrays(GLES20.GL_LINES,0,count);
        }
        private void drawCube(float[] vp,float x,float y,float z,float sx,float sy,float sz,float r,float g,float b) {
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(mvp,0,vp,0,model,0);
            GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0);
            GLES20.glUniform4f(colorHandle,r,g,b,1);
            cube.position(0); GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,0,cube);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
        private final float[] model=new float[16], mvp=new float[16];
        private void drawController(float[] vp,int i) {
            float[] pos=handPos[i];
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,pos[0],pos[1],pos[2]);
            Matrix.rotateM(model,0,handYaw[i],0,1,0);
            Matrix.rotateM(model,0,handPitch[i],1,0,0);
            Matrix.scaleM(model,0,0.11f,0.18f,0.28f);
            Matrix.multiplyMM(mvp,0,vp,0,model,0);
            GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0);
            GLES20.glUniform4f(colorHandle,i==0?0.2f:0.95f,i==0?0.82f:0.42f,i==0?0.72f:0.25f,1);
            cube.position(0); GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,0,cube);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}
