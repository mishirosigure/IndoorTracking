package com.example.indoortracking

import android.content.Context
import android.graphics.Color
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.net.sip.SipSession
import android.support.v4.app.INotificationSideChannel
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.lang.AssertionError
import java.security.KeyStore
import java.util.*
import kotlin.math.max
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {
    //値を表示する用のtextView
    private lateinit var textView: TextView

    private var strAcc = "加速度センサー\n " +
            "X: 0\n" +
            "Y: 0\n" +
            "Z: 0"

    //加速度の配列、重力加速度含む
    private var gravitationalOrientationValues = arrayListOf(0F, 0F, 0F) //Array(3) { 0.0F; 0.0F; 0.0F }

    //重力加速度除去後の値
    private var gravitationalAccelerationValues = arrayListOf(0F, 0F, 0F)//Array(3) { 0.0F; 0.0F; 0.0F }

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

    //グラフ全体
    private var mChart: LineChart? = null

    //各要素
    private val names = arrayListOf("x-value", "y_value", "z_value")//Array(3) { "x-value"; "y-value"; "z-value" }
    private val colors = arrayListOf(Color.RED, Color.GREEN, Color.BLUE)//Array(3) { Color.RED; Color.GREEN; Color.BLUE }
    private var accValues = arrayListOf(0F, 0F, 0F)//Array(3){0.0F; 0.0F; 0.0f}

    //lowPassのon,off
    private var lowPassFlag = true
    //thresHoldのon,off
    private var thresHoldFlag = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Get an instance of the Switch
        val lowPassSwitch : Switch = findViewById(R.id.lowPass)
        val thresHoldSwitch : Switch = findViewById(R.id.thresHold)
        //switch初期設定
        lowPassSwitch.isChecked = false
        thresHoldSwitch.isChecked = true
        //Flagにon,offを入れる
        lowPassSwitch.setOnCheckedChangeListener { _, isChecked ->
            lowPassFlag = isChecked
        }
        thresHoldSwitch.setOnCheckedChangeListener {_, isChecked ->
            thresHoldFlag = isChecked
        }
        // Get an instance of the TextView
        textView = findViewById(R.id.acc_value)
        //LineChartビューにLineData型のインスタンス
        mChart = findViewById(R.id.lineChart)
        //表のタイトルを消す
        mChart?.setDescription("")
        //Line型インスタンスの追加
        mChart?.data = LineData()
    }

    fun onCheckedChangeListener(){

    }

    override fun onResume() {
        super.onResume()
        //Create instance of SensorManager
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        //Get accelerometer sensor instance
        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        //make callback method
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        //Listener解除
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {

        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    //重力加速度を抽出
                    for (i in 0..2) {
                        gravitationalOrientationValues[i] =
                            event.values[i] * (1 - alpha) + gravitationalOrientationValues[i] * alpha

                        //重力加速度を除く
                        gravitationalAccelerationValues[i] =
                            event.values[i] - gravitationalOrientationValues[i]
                    }
                    //差分を計算
                    dx = gravitationalAccelerationValues[0] - x_old
                    dy = gravitationalAccelerationValues[1] - y_old
                    dz = gravitationalAccelerationValues[2] - z_old

                    //RCフィルタをかける
                    if (lowPassFlag) {
                        xValue =
                            ((1 - alpha) * gravitationalAccelerationValues[0] + alpha * xValue)
                        yValue =
                            ((1 - alpha) * gravitationalAccelerationValues[1] + alpha * yValue)
                        zValue =
                            ((1 - alpha) * gravitationalAccelerationValues[2] + alpha * zValue)
                    }

                    xValue = gravitationalAccelerationValues[0]
                    yValue = gravitationalAccelerationValues[1]
                    zValue = gravitationalAccelerationValues[2]


                    //切り捨てる
                    xValue = String.format("%.4f", xValue).toFloat()
                    yValue = String.format("%.4f", yValue).toFloat()
                    zValue = String.format("%.4f", zValue).toFloat()

                    //ベクトルの大きさを計算
                    //vectorSize = sqrt((dx*dx + dy*dy + dz*dz).toDouble())
                    vectorSize =
                        sqrt((xValue * xValue + yValue * yValue + zValue * zValue).toDouble())

                    if (vectorSize > THRESHOLD /*&& dz < 0.0F*/) {
//                            if (true/*counted*/) {
////                                counted = false
//                                //最大値なら格納
//                                //vectorSize_max = max(vectorSize, vectorSize_max)
//                            }
//                            else if(!counted){
//                                counted = true
//                            }
                    } else {
                        if(thresHoldFlag) {
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

                    //グラフへのデータ追加
                    accValues = arrayListOf(xValue, yValue, zValue)//{xValue; yValue; zValue}
                    val data:LineData? = mChart?.lineData
                    data?.let {
                        for (i in 0..2){
                            var set:ILineDataSet? = data.getDataSetByIndex(i)
                            if(set == null){
                                set = createSet(names[i], colors[i])
                                data.addDataSet(set)
                            }
                            //データ追加
                            data.addEntry(Entry(set.entryCount.toFloat(), accValues[i]), i)
                            data.notifyDataChanged()
                        }
                        mChart?.run {
                            //表示更新のために変更を通知
                            notifyDataSetChanged()
                            //表示の幅を決定
                            setVisibleXRangeMaximum(50F)
                            //表示を最新のデータまで移動
                            moveViewToX(data.entryCount.toFloat())
                        }
                    }
                }
                else -> {throw AssertionError()}
            }
        }
    }

    //グラフに関する設定
    private fun createSet(label: String, col: Int): LineDataSet {
        return LineDataSet(null, label).apply {
            lineWidth = 2.5f
            color = col
            setDrawCircles(false)
            setDrawValues(false)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }
}
