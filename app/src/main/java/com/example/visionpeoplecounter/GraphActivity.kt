package com.example.visionpeoplecounter

import DatabaseHelper
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButton

class GraphActivity : AppCompatActivity() {
    private lateinit var lineChart: LineChart
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var resetButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        lineChart = findViewById(R.id.lineChart)
        resetButton = findViewById(R.id.btn_reset_db)
        dbHelper = DatabaseHelper(this)

        displayGraph()

        resetButton.setOnClickListener {
            dbHelper.clearDatabase()
            displayGraph()
            Toast.makeText(this, "Database reset", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayGraph() {
        val counts = dbHelper.getAllCounts()
        val entries = counts.mapIndexed { index, (count,timestamp) -> Entry(index.toFloat(), count.toFloat()) }

        val dataSet = LineDataSet(entries, "Person Count").apply{
            color = Color.WHITE // 라인 색상
            valueTextColor = Color.WHITE // 값 텍스트 색상
            setDrawValues(true) // 데이터 포인트 값 표시 여부
        }
        val lineData = LineData(dataSet)
        lineChart.apply {
            data = lineData
            setBackgroundColor(Color.BLACK) // 차트 배경 색상
            axisLeft.textColor = Color.WHITE // 왼쪽 Y축 텍스트 색상
            axisRight.textColor = Color.WHITE // 오른쪽 Y축 텍스트 색상
            xAxis.textColor = Color.WHITE // X축 텍스트 색상
            legend.textColor = Color.WHITE // 범례 텍스트 색상
            description.textColor = Color.WHITE // 설명 텍스트 색상
            description.isEnabled = false // 설명 텍스트 비활성화
            setDrawGridBackground(false) // 그리드 배경 비활성화
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.setLabelCount(counts.size, true)
            xAxis.valueFormatter = IndexAxisValueFormatter(counts.map { it.second })

            setExtraOffsets(10f, 10f, 10f, 10f)
            invalidate() // 차트 새로고침
        }
    }
}