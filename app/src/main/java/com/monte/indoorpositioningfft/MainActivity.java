package com.monte.indoorpositioningfft;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
//Implement differential of the Magnitude to detect a stop and start
//At the beginning user can press calibrate, to calibrate sensor with true north values
//Could use previously developed compass to check if true north is actually true
public class MainActivity extends AppCompatActivity implements SensorEventListener, CompoundButton.OnCheckedChangeListener {

    private final Handler mHandler = new Handler();
    private Runnable mTimer;
    private Runnable FFTtimer;
    private LineGraphSeries<DataPoint> mSeries;
    private BarGraphSeries<DataPoint> FFTseries;
    private DataPoint[] FFTdata;
    private double[] ReFFT;
    private double[] ImFFT;
    private double[] FFTwindow;

    private List<Float> FFTdataList;
    private final int SAMPLING_FREQ = 50; //sampling frequency
    private final int FFT_SIZE = 128;
    private FFT sensorFFT;
    private final int MIN_GRAPH = 0;
//    private final int MAX_GRAPH = 128;
    private double graph2LastXValue = 5d;

    private SensorManager mSensorManager;   //sensorManager object
    private Sensor mSensor; //magnetometer sensor
    private Sensor sSensor; //step counter sensor
    private Sensor gSensor; //gyroscope sensor
    private float[] gravity;
    private float[] linear_acceleration;
    private float allLinearAcc = 0;
    private float allGravity = 0;   //combined gravity

    private TextView maxFreqText;
    private TextView maxMagnitudeText;
    private TextView distanceText;
    private TextView dynamicRangeText;
    private TextView statusText;
    private TextView stepCounterText;
    private TextView mapXText;
    private TextView mapYText;
    private TextView degreesText;

    private float walkedDistance = 0.0f;

    private long prevTime = System.currentTimeMillis();
    private float stepCount = 0;

    private float mapX = 0.0f;
    private float mapY = 0.0f;
    private float degrees = 0.0f;

    private float[] rawRotationValues = new float[4];
    private Sensor rSensor;
    private final static double PI = Math.PI;
    private final static double TWO_PI = PI*2;

    private boolean positionIndoor = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        initialiseSensors();
        initialiseGraphs();
        initialiseFFTanother();
        initialiseAdditionalInfo();
    }

    private void initialiseAdditionalInfo (){
        maxFreqText = (TextView) findViewById(R.id.freqText);
        maxMagnitudeText = (TextView) findViewById(R.id.magnitudeText);
        distanceText = (TextView) findViewById(R.id.distanceText);
        dynamicRangeText = (TextView) findViewById(R.id.dynamicRangeText);
        statusText = (TextView) findViewById(R.id.statusText);
        stepCounterText = (TextView) findViewById(R.id.stepCounterText);
        mapXText = (TextView) findViewById(R.id.map_x_text);
        mapYText = (TextView) findViewById(R.id.map_y_text);
        degreesText = (TextView) findViewById(R.id.degreesText);

        Switch indoorOutdoorSwitch = (Switch) findViewById(R.id.indoorOutdoorSwitch);
        indoorOutdoorSwitch.setOnCheckedChangeListener(this);
    }
    private void initialiseSensors (){
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        rSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
//        gSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        gravity = new float[3];
        linear_acceleration = new float[3];
    }

    private void initialiseGraphs (){
        GraphView accelGraph = (GraphView) findViewById(R.id.graph);

        mSeries = new LineGraphSeries<DataPoint>();
        accelGraph.addSeries(mSeries);
        accelGraph.getViewport().setXAxisBoundsManual(true);
        accelGraph.getViewport().setMinX(MIN_GRAPH);
        accelGraph.getViewport().setMaxX(FFT_SIZE);
        accelGraph.getViewport().setYAxisBoundsManual(true);
        accelGraph.getViewport().setMinY(-2);
        accelGraph.getViewport().setMaxY(2);
        accelGraph.setTitle("Normalised Acceleration");
        accelGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        accelGraph.getGridLabelRenderer().setVerticalAxisTitle(("Magnitude, m/s^2"));

        GraphView FFTgraph = (GraphView) findViewById(R.id.FFTgraph);
        FFTseries = new BarGraphSeries<DataPoint>();
        FFTgraph.addSeries(FFTseries);
        FFTgraph.getViewport().setXAxisBoundsManual(true);
        FFTgraph.getViewport().setMinX(-FFT_SIZE / 2);
        FFTgraph.getViewport().setMaxX(FFT_SIZE / 2);

        FFTgraph.getViewport().setYAxisBoundsManual(true);
        FFTgraph.getViewport().setMaxY(60);
        FFTgraph.setTitle("FFT of the Acceleration");
        FFTgraph.getGridLabelRenderer().setHorizontalAxisTitle("Bin number");
        FFTgraph.getGridLabelRenderer().setVerticalAxisTitle(("Magnitude"));
    }

    private void initialiseFFTanother (){
        FFTdataList = new ArrayList<Float>();
        ReFFT = new double[FFT_SIZE];
        ImFFT = new double[FFT_SIZE];
        FFTdata = new DataPoint[FFT_SIZE];

        for (int i = 0; i < FFT_SIZE; i++){
            ReFFT[i] = 0.0;
            ImFFT[i] = 0.0;
            FFTdata[i] = new DataPoint((double)i, 0.0);
            FFTdataList.add(0.0f);
        }
        sensorFFT = new FFT(FFT_SIZE);
        FFTwindow = sensorFFT.getWindow();
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, rSensor, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, sSensor, SensorManager.SENSOR_DELAY_GAME);
        mTimer = new Runnable() {
            @Override
            public void run() {
                graph2LastXValue += 1d;
                if (graph2LastXValue > 1000){    //to prevent from data Leak
                    DataPoint[] tmp = new DataPoint[FFT_SIZE];
                    for (int i = 0 ; i  < FFT_SIZE; i++)
                        tmp[i]  = new DataPoint(i, ReFFT[i]);
                    mSeries.resetData(tmp);
                    graph2LastXValue = FFT_SIZE;
                }
                mSeries.appendData(new DataPoint(graph2LastXValue, allGravity), true, FFT_SIZE);
                mHandler.postDelayed(this, 30);
            }
        };
        mHandler.postDelayed(mTimer, 1000);

        FFTtimer = new Runnable() {
            @Override
            public void run() {
                FFTseries.resetData(FFTdata);
                mHandler.postDelayed(this, 30);
            }
        };
        mHandler.postDelayed(FFTtimer, 1000);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        mHandler.removeCallbacks(mTimer);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float alpha = 0.8f;
        switch (event.sensor.getType()){
            case Sensor.TYPE_ACCELEROMETER:
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
                allGravity = (float) Math.sqrt(gravity[0]*gravity[0] + gravity[1]*gravity[1] + gravity[2]*gravity[2]) - 9.75f;
                linear_acceleration[0] = event.values[0] - gravity[0];
                linear_acceleration[1] = event.values[1] - gravity[1];
                linear_acceleration[2] = event.values[2] - gravity[2];

                allLinearAcc = (float) Math.sqrt(linear_acceleration[0]*linear_acceleration[0] + linear_acceleration[1]*linear_acceleration[1] + linear_acceleration[2]*linear_acceleration[2]) / 9.7f - 1.0f;
                for (int i = 0; i < FFT_SIZE - 1; i++){
                    ReFFT[i] = ReFFT[i + 1];
                    ImFFT[i] = 0.0f;
                }
                ReFFT[FFT_SIZE - 1] = (double) allGravity;
                ImFFT[FFT_SIZE - 1] = 0.0f;

                double [] ReTmp = ReFFT.clone();
                double [] ImTmp = ImFFT.clone();

                for (int i = 0; i < FFT_SIZE; i++)
                    ReTmp[i] = FFTwindow[i] * ReTmp[i];

                sensorFFT.fft(ReTmp, ImTmp);
                double[] mag = FFT.getMagnitude(ReTmp, ImTmp);


                for (int i = -FFT_SIZE/2; i < 0; i++){
                    FFTdata[i+FFT_SIZE/2] = new DataPoint((double) i, mag[FFT_SIZE + i]);
                }

                for (int i = 0; i < FFT_SIZE/2 - 1; i++){
                    FFTdata[i+FFT_SIZE/2] = new DataPoint((double) i, mag[i]);
                }

                double maxMag = 0;
                double maxFreq = 0;
                int maxIndex = 0;
                double allMagnitudes = 0.0;
                for (int i = 1; i < FFT_SIZE/2 - 1; i++){
                    allMagnitudes += mag[i];
                    if (mag[i] > maxMag){
                        maxMag = mag[i];
                        maxFreq = FFT.Index2Freq(i, (double)SAMPLING_FREQ, FFT_SIZE);
                        maxIndex = i;
                    }
                }
                maxFreqText.setText(String.format("%.2f Hz", maxFreq));
                maxMagnitudeText.setText(String.format("%.2f Mag", maxMag));
                double dynamicRange = maxMag/(allMagnitudes-maxMag);
                dynamicRangeText.setText(String.format("DR= %.2f", dynamicRange));

                //make sure maxMagnitude is large enough to detect a movement

                //make sure that the movement is not smth fake - check for
                //good dynamic range to see noise

                //Dynamic range is not enough, if frequency is making a transition, thus check for that

                //check that it doesn't have hidden biasing - compare max with DC value
                //Otherwise means that it's moving in two different direction - simply random movement
                boolean makeTransition = (mag[maxIndex-1]/mag[maxIndex]>0.8 || mag[maxIndex+1]/mag[maxIndex]>0.8);
                if (maxMag > 5.0 && (dynamicRange > 0.18 || (makeTransition && dynamicRange > 0.14)) && mag[0]/maxMag < 0.5 ){
                    //checks for random single motions
                    if (System.currentTimeMillis() - prevTime > 400) {
                        double changeInDistance = maxFreq / SAMPLING_FREQ / 2.0 + maxMag / (100.0 * SAMPLING_FREQ);
                        walkedDistance += changeInDistance;

                        mapX += Math.cos(degrees) * changeInDistance;
                        mapY += Math.sin(degrees) * changeInDistance;

                        if (maxFreq < 1.6)
                            statusText.setText("WALKING");
                        else
                            statusText.setText("RUNNING");
                    }
                } else {
                    prevTime = System.currentTimeMillis();
                    if (mag[0]/maxMag > 0.5 &&  mag[0] > 5.0)
                        statusText.setText("ROTATING");
                    else
                        statusText.setText("STANDING");
                }

                distanceText.setText(String.format("Walked= %.2f m", walkedDistance));
                mapXText.setText(String.format("X= %.2f", mapX));
                mapYText.setText(String.format("Y= %.2f", mapY));

//                System.out.println("MaxFreq=" + (float)maxFreq + " maxMag=" + (float)maxMag + " Index=" + maxIndex);
//                for (int i = 0; i < FFT_SIZE - 1; i++){
//                    FFTdata[i] = new DataPoint((double) i, mag[i]);
//                    System.out.print(ReFFT[i] + " ");
//                }
//                System.out.println();

                break;
            case Sensor.TYPE_STEP_COUNTER:
                stepCount = event.values[0];
                stepCounterText.setText("Steps=" + stepCount);
                break;
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                rawRotationValues = event.values.clone();
                break;
            case Sensor.TYPE_ROTATION_VECTOR:

                break;
        }
        if (positionIndoor)
            calculateGyroOrientation();
        else
            calculateRotationOrientation();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void calculateRotationOrientation (){
        float[] rotationMatrix = new float[16];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rawRotationValues);
        SensorManager.getOrientation(rotationMatrix, orientation);

        degrees = ((float) mod(orientation[0] + TWO_PI,TWO_PI) );
        degreesText.setText(String.format("deg= %.2f˚", degrees * 180.0f/PI));
    }

    private void calculateGyroOrientation (){

    }

    private double mod(double a, double b){
        return a % b;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        positionIndoor = isChecked;
        walkedDistance = 0.0f;
        mapX = 0.0f;
        mapY = 0.0f;
    }
}
