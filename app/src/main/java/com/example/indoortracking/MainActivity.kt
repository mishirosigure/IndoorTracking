package com.example.indoortracking

import android.content.Context
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.support.v4.app.INotificationSideChannel
import android.widget.TextView
import java.util.*
import kotlin.math.max
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {
    private var textView : TextView? = null
    private var textInfo : TextView? = null

    private var strAcc = "加速度センサー\n " +
            "X: 0\n " +
            "Y: 0\n " +
            "Z: 0"

    //加速度の配列、重力加速度含む
    private var gravitationalOrientationValues = Array(3) {0.0F; 0.0F; 0.0F}
    //パスを通したあとの値
    private var gravitationalAccelerationValues = Array(3) {0.0F; 0.0F; 0.0F}

    //差分
    private var dx = 0.0F
    private var dy = 0.0F
    private var dz = 0.0F
    //前回の値
    private var x_old = 0.0F
    private var y_old = 0.0F
    private var z_old = 0.0F
    //ノイズ除去後の値
    private var xValue = 0.0F
    private var yValue = 0.0F
    private var zValue = 0.0F
    //ベクトル量
    private var vectorSize = 0.0//デフォルトでDouble型
    //カウンタ
    var counter = 0L
    //カウントフラグ(一回の揺れで２回以上のカウント防止)
    private var counted = false

    //軸ごとの加速方向
    private var vecX = true
    private var vecY = true
    private var vecZ = true

    //ノイズ除去
    private var noiseflg = true
    //ベクトル量_Max
    private var vectorSize_max = 0.0;

    companion object {
        //閾値
        const val THRESHOLD = 0.2F
        const val THRESHOLD_MIN = 0.1F

        //ローパスフィルタのα値
        const val alpha = 0.85F
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Get an instance of the TextView
        textView = findViewById(R.id.acc_value)
    }

    override fun onResume() {
        super.onResume()
        //Create instance of SensorManager
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        //Get accelerometer sensor instance
        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        //make callback method
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        //Listener解除
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {

        event?.let {
            when(event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    //重力加速度を抽出
                    for(i in 0..2) {
                        gravitationalOrientationValues[i] =
                                event.values[i] * (1-alpha) + gravitationalOrientationValues[i] * alpha

                        //重力加速度を除く
                        gravitationalAccelerationValues[i] =
                                event.values[i] - gravitationalOrientationValues[i]
                    }
                    //差分を計算
                    dx = gravitationalAccelerationValues[0] - x_old
                    dy = gravitationalAccelerationValues[1] - y_old
                    dz = gravitationalAccelerationValues[2] - z_old

                    //RCフィルタをかける
                    xValue = ((1-alpha)*gravitationalAccelerationValues[0] + alpha*x_old).toFloat()
                    yValue = ((1-alpha)*gravitationalAccelerationValues[1] + alpha*y_old).toFloat()
                    zValue = ((1-alpha)*gravitationalAccelerationValues[2] + alpha*z_old).toFloat()
                    //切り捨てる
                    xValue = String.format("%.4f", xValue).toFloat()
                    yValue = String.format("%.4f", yValue).toFloat()
                    zValue = String.format("%.4f", zValue).toFloat()

                    //ベクトルの大きさを計算
                    //vectorSize = sqrt((dx*dx + dy*dy + dz*dz).toDouble())
                    vectorSize = sqrt((xValue*xValue + yValue*yValue + zValue*zValue).toDouble())

                    //ノイズになりやすい1回目は除く
                    if (noiseflg){
                        noiseflg = false
                    }
                    else{
                        if(vectorSize > THRESHOLD /*&& dz < 0.0F*/){
//                            if (true/*counted*/) {
////                                counted = false
//                                //最大値なら格納
//                                //vectorSize_max = max(vectorSize, vectorSize_max)
//                            }
//                            else if(!counted){
//                                counted = true
//                            }
                        }
                        else{
                            xValue = 0.0000F
                            yValue = 0.0000F
                            zValue = 0.0000F
                        }
                    }

                    strAcc = "加速度センサー\n " +
                            "X: $xValue\n " +
                            "Y: $yValue\n " +
                            "Z: $zValue"
                    //strAcc = "$vectorSize"
                    //状態保存
                    x_old = gravitationalAccelerationValues[0]
                    y_old = gravitationalAccelerationValues[1]
                    z_old = gravitationalAccelerationValues[2]

                    textView?.text = strAcc
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }
}
