package com.dermassist.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // Prevent concern photos and symptoms appearing in screenshots or the recents thumbnail.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent { DermTheme { DermApp() } }
    }
}

fun shareReport(context: android.content.Context, assessment: Assessment) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "DermAssist prototype record")
        putExtra(Intent.EXTRA_TEXT, assessment.report())
    }
    context.startActivity(Intent.createChooser(intent, "Share your record"))
}
