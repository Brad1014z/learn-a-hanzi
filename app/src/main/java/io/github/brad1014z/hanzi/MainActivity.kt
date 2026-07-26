package io.github.brad1014z.hanzi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.brad1014z.hanzi.cloud.Cloud
import io.github.brad1014z.hanzi.cloud.SyncWorker
import io.github.brad1014z.hanzi.ui.HanziApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cloud layer glue (spec 12): Credential Manager needs an Activity for its
        // sheet; the periodic sync is a silent no-op offline or signed out.
        Cloud.activityProvider = { this }
        SyncWorker.schedulePeriodic(applicationContext)
        SyncWorker.kickNow(applicationContext)
        setContent {
            HanziApp()
        }
    }

    override fun onDestroy() {
        Cloud.activityProvider = { null }
        super.onDestroy()
    }
}
