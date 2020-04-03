package com.example.indoortracking

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.math.pow

class MainActivity : AppCompatActivity(), SensorEventListener {
    //値を表示する用のtextView
    private var accView: TextView? = null
    private var magView: TextView? = null
    private var angeleView: TextView? = null
    private var distanceView: TextView? = null
    private var higher: TextView? = null

    private var strAcc = "加速度センサー\n " +
            "X: 0\n" +
            "Y: 0\n" +
            "Z: 0"
    private var strMag = "磁気センサー\n" +
            "X: 0\n" +
            "Y: 0\n" +
            "Z: 0"

    //磁気センサーの値
    private var magneticValues = floatArrayOf(0F,0F,0F)
    //前回の値
    private var magneticOldValues = floatArrayOf(0F,0F,0F)
    //磁気センサーが有効化
    private var validMagnetic = false
    //加速度センサーが有効化
    private var validAcc = false
    //加速度の配列、重力加速度含む
    private var gravitationalOrientationValues = mutableListOf(0F,0F,0F)
    //重力加速度除去後の値
    private var gravitationalAccelerationValues = mutableListOf(0F,0F,0F)

    //差分
    private var dx = 0.0F
    private var dy = 0.0F
    private var dz = 0.0F

    //前回の値
    private var accOldValues = floatArrayOf(0F,0F,0F)

    //ノイズ除去後の値
    private var accValues = floatArrayOf(10.12F,0F,0F)

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
    private var vectorSize_max = 0.0

    //傾斜行列
    private var rotate = FloatArray(16)
    //回転行列
    private var inclination = FloatArray(16)
    //方向
    private var orientation =FloatArray(3)

    companion object {
        //閾値
        const val THRESHOLD = 0.06F
        const val THRESHOLD_MIN = 0.1F
        //ローパスフィルタのα値
        const val alpha = 0.85F
    }

    //lowPassのon,off
    private var lowPassFlag = true
    //thresHoldのon,off
    private var thresHoldFlag = false

    private var oldvalue = 0F
    //speed
    private var speed = 0F
    private var oldSpeed = 0F
    //距離
    private var distance = 0F
    //SensorDelay
    private var sensorDelay = 100000

    private var measure = false
    private var highLowFlag = false
    private var highLowDistance = 0F

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Get an instance of the Switch
        val lowPassSwitch : Switch = findViewById(R.id.lowPass)
        val thresHoldSwitch : Switch = findViewById(R.id.thresHold)
        //switch初期設定
        lowPassSwitch.isChecked = true
        thresHoldSwitch.isChecked = false
        //Flagにon,offを入れる
        lowPassSwitch.setOnCheckedChangeListener { _, isChecked ->
            lowPassFlag = isChecked
        }
        thresHoldSwitch.setOnCheckedChangeListener {_, isChecked ->
            thresHoldFlag = isChecked
        }
        // Get an instance of the TextView
        accView = findViewById(R.id.acc_value)
        magView = findViewById(R.id.mag_value)
        angeleView = findViewById(R.id.angele_value)
        distanceView = findViewById(R.id.deference_value)
        higher = findViewById(R.id.highLow)

        //Buttonのリスナー
        DistanceButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                measure = true
                speed = 0F
                oldSpeed = 0F
                distance = 0F
            }
            else{
                measure = false
            }
        }
        highLowButton.setOnCheckedChangeListener{ _, isChecked ->
            if (isChecked) {
                highLowFlag = true
                highLowDistance = 0F
            }
            else{
                highLowFlag = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //Create instance of SensorManager
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        //Get accelerometer sensor instance
        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        //Get magnetic sensor instance
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        //make callback method
        sensorManager.registerListener(this, accSensor, sensorDelay)//0.1秒で取得
        sensorManager.registerListener(this, magSensor, sensorDelay)
    }

    override fun onPause() {
        super.onPause()
        //Listener解除
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(this)
    }

    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent?) {

        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    //重力加速度を抽出
                    for (i in event.values.indices) {
                        gravitationalOrientationValues[i] =
                        event.values[i] * (1 - alpha) + gravitationalOrientationValues[i] * alpha

//                        重力加速度を除く
                        gravitationalAccelerationValues[i] =
                        event.values[i] - gravitationalOrientationValues[i]
                    }


                    //値を代入
                    accValues = event.values.clone()
                    //accValues = event.values.clone()

                    //差分を計算
//                    dx = gravitationalAccelerationValues[0] - accOldValues[0]
//                    dy = gravitationalAccelerationValues[1] - accOldValues[1]
//                    dz = gravitationalAccelerationValues[2] - accOldValues[2]

                    //RCフィルタをかける
                    accValues = lowpassFilter(accOldValues, accValues)

                    //切り捨てる
                    accValues = truncation(accValues)

                    for (i in gravitationalAccelerationValues.indices){
                        if (gravitationalAccelerationValues[i].pow(2) < THRESHOLD && thresHoldFlag) {
                            gravitationalAccelerationValues[i] = 0F
                        }
                    }


                    //ベクトルの大きさを計算
                    //vectorSize = sqrt((dx*dx + dy*dy + dz*dz).toDouble())
//                    vectorSize =
//                        sqrt((gravitationalAccelerationValues[0].pow(2) + gravitationalAccelerationValues[1].pow(2) + gravitationalAccelerationValues[2].pow(2)).toDouble())
//
//                    if (vectorSize > THRESHOLD || !thresHoldFlag /*dz < 0.0F*/) {
//                            if (true/*counted*/) {
//                                counted = false
//                                //最大値なら格納
//                                //vectorSize_max = max(vectorSize, vectorSize_max)
//                            }
//                            else if(!counted){
//                                counted = true
//                            }
//                    }
//                    else{
//                        gravitationalAccelerationValues = floatArrayOf(0.0000F, 0.0000F, 0.0000F)
//                    }

                    strAcc = "加速度センサー\n " +
                            "X: ${gravitationalAccelerationValues[0]}\n " +
                            "Y: ${gravitationalAccelerationValues[1]}\n " +
                            "Z: ${gravitationalAccelerationValues[2]}"
                    //strAcc = "$vectorSize"
                    //状態保存
                    accOldValues = accValues

                    accView?.text = strAcc
                    validAcc = true

                    if (measure) {
                        //速度を台形積分
                        speed += ((gravitationalAccelerationValues[1] + oldvalue) * 0.1F) / 2
                        oldvalue = gravitationalAccelerationValues[1]
                        //距離を台形積分
                        distance += ((speed + oldSpeed) * 0.1F) / 2
                        oldSpeed = speed
                        distanceView?.text = "速度 : $speed\n" +
                                "距離 : $distance"
                    }
                    if (highLowFlag){
                        //逆方向の加速度を測定しないようにする
                        if(counted) {
                            highLowDistance += gravitationalAccelerationValues[2]
                        }
                        else{
                            counted = true
                        }
                        if(0 < highLowDistance){
                            higher?.text = "LOW"
                        }
                        else if(0 > highLowDistance){
                           higher?.text = "HIGH"
                        }
                        else{
                            higher?.text = "0"
                        }
                    }
                }

                //磁気センサー
                Sensor.TYPE_MAGNETIC_FIELD ->{
                    magneticValues = event.values.clone()
                    magneticValues = lowpassFilter(magneticOldValues, magneticValues)
                    magneticValues = truncation(magneticValues)
                    strMag = "磁気センサー\n " +
                            "X: ${magneticValues[0]}\n " +
                            "Y: ${magneticValues[1]}\n " +
                            "Z: ${magneticValues[2]}"
                    magView?.text = strMag
                    validMagnetic = true
                    magneticOldValues = magneticValues
                }
            }
            if(validAcc && validMagnetic){
                SensorManager.getRotationMatrix(
                    rotate, inclination, accValues, magneticValues
                )
                SensorManager.getOrientation(rotate, orientation)

                //デグリー角に変換
                val degreeDir = Math.toDegrees(orientation[0].toDouble())
                findViewById<ImageView>(R.id.naviImage).rotation = degreeDir.toFloat()
                angeleView?.text = "角度 = $degreeDir"
                //フラグを無効化
                validAcc = false
                validMagnetic = false
            }
        }
    }

    //切り捨てる関数
    private fun truncation(values:FloatArray) : FloatArray{
        for (i in values.indices){
            values[i] = String.format("%.4f", values[i]).toFloat()
        }
        return values
    }
    //RCフィルタ用関数
    private fun lowpassFilter(oldValues:FloatArray , values:FloatArray) : FloatArray{
        if (lowPassFlag) {
            for (i in values.indices) {
                values[i] = (1 - alpha) * values[i] + alpha * oldValues[i]
            }
        }
        return values
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }
}
