package com.example.visionpeoplecounter

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.Button
import com.example.visionpeoplecounter.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            // BottomSheetDialog 레이아웃 인플레이트
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_layout, null)
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(bottomSheetView)

            // BottomSheetDialog 내의 버튼 참조 및 클릭 리스너 설정
            val option1Button = bottomSheetView.findViewById<Button>(R.id.option1)
            val option2Button = bottomSheetView.findViewById<Button>(R.id.option2)
            val option3Button = bottomSheetView.findViewById<Button>(R.id.option3)

            option1Button.setOnClickListener {
                val intent = Intent(this@MainActivity, LiveCounting::class.java)
                startActivity(intent)
            }

            option2Button.setOnClickListener {
                val intent = Intent(this@MainActivity, UploadVideo::class.java)
                startActivity(intent)
            }

            option3Button.setOnClickListener {
                val intent = Intent(this@MainActivity, GraphActivity::class.java)
                startActivity(intent)
            }

            // Dialog를 마지막에 표시
            dialog.show()
        }


    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}