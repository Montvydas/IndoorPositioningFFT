package com.monte.indoorpositioningfft;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

/**
 * Created by monte on 12/04/16.
 */
public class GraphsActivity extends Activity implements SensorEventListener {

    // Define textVies used within the activity
    private TextView freqAccelX;
    private TextView freqAccelY;
    private TextView freqAccelZ;

    private TextView magAccelX;
    private TextView magAccelY;
    private TextView magAccelZ;

    private TextView freqRotationX;
    private TextView freqRotationY;
    private TextView freqRotationZ;

    private TextView magRotationX;
    private TextView magRotationY;
    private TextView magRotationZ;

    //Define graph series used within the project
    private LineGraphSeries<DataPoint> seriesAccelX = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> seriesAccelY = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> seriesAccelZ = new LineGraphSeries<>();

    private LineGraphSeries<DataPoint> seriesRotationX = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> seriesRotationY = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> seriesRotationZ = new LineGraphSeries<>();

    //Sensors defined here
    private SensorManager mSensorManager;   //sensorManager object
    private Sensor accelSensor; //accelerometer sensor
    private Sensor rotationSensor; //rotation vector sensor

    //Threading elements for drawing graphs
    private Runnable runnableAccelX;
    private Runnable runnableAccelY;
    private Runnable runnableAccelZ;

    private Runnable runnableRotationX;
    private Runnable runnableRotationY;
    private Runnable runnableRotationZ;

    private final Handler mHandler = new Handler(); //for handling thread

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphs);
        initialiseText();
        initialiseGraphs();
        initialiseSensors ();
    }

    private void initialiseText (){
        freqAccelX = (TextView) findViewById(R.id.freq_accel_x);
        freqAccelY = (TextView) findViewById(R.id.freq_accel_y);
        freqAccelZ = (TextView) findViewById(R.id.freq_accel_z);

        magAccelX = (TextView) findViewById(R.id.mag_accel_x);
        magAccelY = (TextView) findViewById(R.id.mag_accel_y);
        magAccelZ = (TextView) findViewById(R.id.mag_accel_z);

        freqRotationX = (TextView) findViewById(R.id.freq_rotation_x);
        freqRotationY = (TextView) findViewById(R.id.freq_rotation_y);
        freqRotationZ = (TextView) findViewById(R.id.freq_rotation_z);

        magRotationX = (TextView) findViewById(R.id.mag_rotation_x);
        magRotationY = (TextView) findViewById(R.id.mag_rotation_y);
        magRotationZ = (TextView) findViewById(R.id.mag_rotation_z);
    }
    private void initialiseGraphs (){
        GraphView graphAccelX = (GraphView) findViewById(R.id.graph_accel_x);
        GraphView graphAccelY = (GraphView) findViewById(R.id.graph_accel_y);
        GraphView graphAccelZ = (GraphView) findViewById(R.id.graph_accel_z);

        GraphView graphRotationX = (GraphView) findViewById(R.id.graph_rotation_x);
        GraphView graphRotationY = (GraphView) findViewById(R.id.graph_rotation_y);
        GraphView graphRotationZ = (GraphView) findViewById(R.id.graph_rotation_z);

        graphAccelX.addSeries(seriesAccelX);
        graphAccelY.addSeries(seriesAccelY);
        graphAccelZ.addSeries(seriesAccelZ);

        graphRotationX.addSeries(seriesRotationX);
        graphRotationY.addSeries(seriesRotationY);
        graphRotationZ.addSeries(seriesRotationZ);

//        graphAccelX.getViewport().setXAxisBoundsManual(true);
//        graphAccelX.getViewport().setMinX(MIN_GRAPH);
//        graphAccelX.getViewport().setMaxX(FFT_SIZE);
//        graphAccelX.getViewport().setYAxisBoundsManual(true);
//        graphAccelX.getViewport().setMinY(-2);
//        graphAccelX.getViewport().setMaxY(2);
        graphAccelX.setTitle("AccelerationX");
        graphAccelX.getGridLabelRenderer().setHorizontalAxisTitle("Time");
//        graphAccelX.getGridLabelRenderer().setVerticalAxisTitle(("Magnitude, m/s^2"));

        graphAccelY.setTitle("AccelerationY");
        graphAccelY.getGridLabelRenderer().setHorizontalAxisTitle("Time");
//        graphAccelY.getGridLabelRenderer().setVerticalAxisTitle(("Magnitude, m/s^2"));

        graphAccelZ.setTitle("AccelerationZ");
        graphAccelZ.getGridLabelRenderer().setHorizontalAxisTitle("Time");
//        graphAccelZ.getGridLabelRenderer().setVerticalAxisTitle(("Magnitude, m/s^2"));

        graphRotationX.setTitle("RotationX");
        graphRotationX.getGridLabelRenderer().setHorizontalAxisTitle("Time");
//        graphRotationX.getGridLabelRenderer().setVerticalAxisTitle(("Magnitude, m/s^2"));

        graphRotationY.setTitle("RotationY");
        graphRotationY.getGridLabelRenderer().setHorizontalAxisTitle("Time");
//        graphRotationY.getGridLabelRenderer().setVerticalAxisTitle(("Magnitude, m/s^2"));

        graphRotationZ.setTitle("RotationZ");
        graphRotationZ.getGridLabelRenderer().setHorizontalAxisTitle("Time");
//        graphRotationZ.getGridLabelRenderer().setVerticalAxisTitle(("Magnitude, m/s^2"));
    }
    private void initialiseSensors (){
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME);
    }


    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];
    private float allLinearAcc = 0;
    private float allGravity = 0;
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                applyAccelFilter (event.values.clone());
                accelAnalysis();
//                if (delay++ == 4){
//                    delay = 0;
//                    allGravity = gravitySum/4;
//                    gravitySum = 0;
//                }

//                accelerometerAnalysis();
//                accelerometerAnalysis();
                break;
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                calculateRotationOrientation (event.values.clone());
        }
    }

    private void applyAccelFilter(float[] accelValues){
        float alpha = 0.8f;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * accelValues[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * accelValues[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * accelValues[2];
        allGravity += (float) Math.sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]) - 9.81f;
        linear_acceleration[0] = accelValues[0] - gravity[0];
        linear_acceleration[1] = accelValues[1] - gravity[1];
        linear_acceleration[2] = accelValues[2] - gravity[2];
        allLinearAcc = (float) Math.sqrt(linear_acceleration[0] * linear_acceleration[0] + linear_acceleration[1] * linear_acceleration[1] + linear_acceleration[2] * linear_acceleration[2]);
    }

    private void accelAnalysis (){

    }

    private final static double PI = Math.PI;
    private final static double TWO_PI = PI*2;

    private void calculateRotationOrientation(float[] rotationVectorValues){
        float[] rotationMatrix = new float[16];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorValues);
        SensorManager.getOrientation(rotationMatrix, orientation);

        degrees = ((float) mod(orientation[0] + TWO_PI,TWO_PI) );
        degreesText.setText(String.format("deg= %.2f˚", degrees * 180.0f / PI));
    }
    private double mod(float a, float b){
        return a % b;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
