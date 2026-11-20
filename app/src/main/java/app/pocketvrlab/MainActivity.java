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
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
    private SceneView scene;
    private WorldRenderer renderer;
    private TextView hud;
    private final float[] matrix=new float[16], orientation=new float[3];
    private float zeroYaw,zeroPitch,zeroRoll;
    private boolean calibrated;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512|4096);
        sensors=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
        rotationSensor=sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if(rotationSensor==null) rotationSensor=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        FrameLayout root=new FrameLayout(this);
        scene=new SceneView(this);
        renderer=new WorldRenderer();
        scene.setRenderer(renderer);
        scene.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(scene,new FrameLayout.LayoutParams(-1,-1));

        hud=new TextView(this);
        hud.setTextColor(Color.WHITE); hud.setTextSize(12); hud.setGravity(Gravity.CENTER);
        hud.setPadding(12,10,12,10); hud.setBackgroundColor(0x99071320);
        hud.setText("POCKET VR  |  ПОВОРАЧИВАЙ ТЕЛЕФОН");
        root.addView(hud,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));

        TextView center=new TextView(this);
        center.setText("＋"); center.setTextColor(0xCCFFFFFF); center.setTextSize(26);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-1);
        root.addView(center,cp);
        center.setOnClickListener(v -> renderer.interact());

        TextView recenter=new TextView(this);
        recenter.setText("◎  ЦЕНТР"); recenter.setTextColor(Color.WHITE);
        recenter.setTextSize(13); recenter.setGravity(Gravity.CENTER);
        recenter.setPadding(20,12,20,12); recenter.setBackgroundColor(0xCC123448);
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        rp.bottomMargin=18; root.addView(recenter,rp);
        recenter.setOnClickListener(v -> recenter());

        scene.setOnTouchListener(new View.OnTouchListener() {
            float lastX,lastY; boolean moved;
            public boolean onTouch(View v,MotionEvent e) {
                if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();moved=false;return true;}
                if(e.getAction()==MotionEvent.ACTION_MOVE){
                    float dx=e.getX()-lastX,dy=e.getY()-lastY;
                    if(Math.abs(dx)+Math.abs(dy)>8)moved=true;
                    renderer.drag(-dx*0.004f,dy*0.004f);
                    lastX=e.getX();lastY=e.getY();return true;
                }
                if(e.getAction()==MotionEvent.ACTION_UP){if(!moved)renderer.interact();return true;}
                return true;
            }
        });
        setContentView(root);
        updateHud();
    }

    private void recenter(){
        zeroYaw=orientation[0];zeroPitch=orientation[1];zeroRoll=orientation[2];calibrated=true;
        renderer.resetLook();
    }
    private void updateHud(){
        if(hud==null||renderer==null)return;
        hud.setText(String.format(Locale.US,"POCKET VR  |  YAW %+.0f°  PITCH %+.0f°  |  ОБЪЕКТ: %d",
            Math.toDegrees(renderer.yaw),Math.toDegrees(renderer.pitch),renderer.active+1));
        hud.postDelayed(this::updateHud,150);
    }
    @Override protected void onResume(){
        super.onResume();if(scene!=null)scene.onResume();
        if(rotationSensor!=null)sensors.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);
    }
    @Override protected void onPause(){
        if(sensors!=null)sensors.unregisterListener(this);if(scene!=null)scene.onPause();super.onPause();
    }
    @Override public void onSensorChanged(SensorEvent e){
        if(e.sensor.getType()!=Sensor.TYPE_GAME_ROTATION_VECTOR&&e.sensor.getType()!=Sensor.TYPE_ROTATION_VECTOR)return;
        SensorManager.getRotationMatrixFromVector(matrix,e.values);
        SensorManager.getOrientation(matrix,orientation);
        if(!calibrated){zeroYaw=orientation[0];zeroPitch=orientation[1];zeroRoll=orientation[2];calibrated=true;}
        renderer.sensorLook(wrap(orientation[0]-zeroYaw),wrap(orientation[1]-zeroPitch),wrap(orientation[2]-zeroRoll));
    }
    private float wrap(float a){while(a>Math.PI)a-=2*Math.PI;while(a< -Math.PI)a+=2*Math.PI;return a;}
    @Override public void onAccuracyChanged(Sensor s,int a){}

    private static class SceneView extends GLSurfaceView{
        SceneView(Context c){super(c);setEGLContextClientVersion(2);setPreserveEGLContextOnPause(true);}
    }

    private static class WorldRenderer implements GLSurfaceView.Renderer{
        private static final String VS="attribute vec3 p; uniform mat4 m; void main(){gl_Position=m*vec4(p,1.0);}";
        private static final String FS="precision mediump float; uniform vec4 c; void main(){gl_FragColor=c;}";
        int program,ph,mh,ch,w,h;
        FloatBuffer cube,grid;
        final float[] proj=new float[16],view=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];
        volatile float yaw,pitch,roll;
        float touchYaw,touchPitch;
        volatile int active=0;
        void sensorLook(float y,float p,float r){yaw=y+touchYaw;pitch=clamp(p+touchPitch,-1.3f,1.3f);roll=r;}
        void drag(float y,float p){touchYaw=clamp(touchYaw+y,-3.1f,3.1f);touchPitch=clamp(touchPitch+p,-1.3f,1.3f);yaw+=y;pitch=clamp(pitch+p,-1.3f,1.3f);}
        void resetLook(){touchYaw=touchPitch=0;yaw=pitch=roll=0;}
        void interact(){active=(active+1)%3;}
        float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
            GLES20.glClearColor(.012f,.022f,.045f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            int vs=shader(GLES20.GL_VERTEX_SHADER,VS),fs=shader(GLES20.GL_FRAGMENT_SHADER,FS);
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);
            ph=GLES20.glGetAttribLocation(program,"p");mh=GLES20.glGetUniformLocation(program,"m");ch=GLES20.glGetUniformLocation(program,"c");
            cube=buf(new float[]{
            -.5f,-.5f,-.5f,.5f,-.5f,-.5f,.5f,.5f,-.5f,-.5f,-.5f,-.5f,.5f,.5f,-.5f,-.5f,.5f,-.5f,
            -.5f,-.5f,.5f,.5f,.5f,.5f,.5f,-.5f,.5f,-.5f,-.5f,.5f,-.5f,.5f,.5f,.5f,.5f,.5f,
            -.5f,-.5f,-.5f,-.5f,.5f,.5f,-.5f,-.5f,.5f,-.5f,-.5f,-.5f,-.5f,.5f,-.5f,-.5f,.5f,.5f,
            .5f,-.5f,-.5f,.5f,-.5f,.5f,.5f,.5f,.5f,.5f,-.5f,-.5f,.5f,.5f,.5f,.5f,.5f,-.5f,
            -.5f,.5f,-.5f,.5f,.5f,-.5f,.5f,.5f,.5f,-.5f,.5f,-.5f,.5f,.5f,.5f,-.5f,.5f,.5f,
            -.5f,-.5f,-.5f,.5f,-.5f,.5f,.5f,-.5f,-.5f,-.5f,-.5f,-.5f,-.5f,-.5f,.5f,.5f,-.5f,.5f});
            float[] a=new float[252];int k=0;
            for(int i=-10;i<=10;i++){a[k++]=i*.25f;a[k++]=-.6f;a[k++]=0;a[k++]=i*.25f;a[k++]=-.6f;a[k++]=-5;
                a[k++]=-2.5f;a[k++]=-.6f;a[k++]=i*.25f-2.5f;a[k++]=2.5f;a[k++]=-.6f;a[k++]=i*.25f-2.5f;}
            grid=buf(a);
        }
        int shader(int t,String s){int x=GLES20.glCreateShader(t);GLES20.glShaderSource(x,s);GLES20.glCompileShader(x);return x;}
        FloatBuffer buf(float[] a){FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).position(0);return b;}
        @Override public void onSurfaceChanged(GL10 gl,int x,int y){w=x;h=y;GLES20.glViewport(0,0,x,y);}
        @Override public void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            Matrix.perspectiveM(proj,0,70,(float)w/Math.max(1,h),.05f,60);
            float cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch),cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw);
            Matrix.setLookAtM(view,0,0,0,0,sy*cp,-sp,-cy*cp,0,1,0);
            Matrix.rotateM(view,0,(float)Math.toDegrees(roll),0,0,1);
            Matrix.multiplyMM(vp,0,proj,0,view,0);
            GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(ph);
            lines();
            box(0,-.05f,-3,.85f,.85f,.85f,active==0?.15f:.08f,active==0?.95f:.48f,.8f);
            box(-1.3f,-.05f,-2.3f,.5f,.85f,.5f,active==1?1:.85f,.45f,.16f);
            box(1.3f,-.05f,-2.5f,.55f,.55f,.55f,.25f,.55f,active==2?1:.9f);
            box(0,-.65f,-2.8f,5,.08f,5,.09f,.13f,.2f);
            GLES20.glDisableVertexAttribArray(ph);
        }
        void lines(){
            GLES20.glUniformMatrix4fv(mh,1,false,vp,0);GLES20.glUniform4f(ch,.08f,.2f,.28f,1);
            grid.position(0);GLES20.glVertexAttribPointer(ph,3,GLES20.GL_FLOAT,false,0,grid);GLES20.glDrawArrays(GLES20.GL_LINES,0,84);
        }
        void box(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b){
            Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.scaleM(model,0,sx,sy,sz);Matrix.multiplyMM(mvp,0,vp,0,model,0);
            GLES20.glUniformMatrix4fv(mh,1,false,mvp,0);GLES20.glUniform4f(ch,r,g,b,1);
            cube.position(0);GLES20.glVertexAttribPointer(ph,3,GLES20.GL_FLOAT,false,0,cube);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}