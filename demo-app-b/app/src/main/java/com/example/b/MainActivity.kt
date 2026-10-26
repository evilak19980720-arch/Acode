package com.example.b

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Minimal demo app B — proves the host A installed and launched it. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        val title = TextView(this).apply {
            text = "应用 B 已成功运行 🎉"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "由宿主 A 安装并拉起\npackage: com.example.b"
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = 24
            }
        }
        root.addView(title)
        root.addView(sub)
        setContentView(root)
    }
}
