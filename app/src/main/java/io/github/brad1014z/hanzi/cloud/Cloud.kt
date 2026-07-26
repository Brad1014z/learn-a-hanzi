package io.github.brad1014z.hanzi.cloud

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import io.github.brad1014z.hanzi.data.HanziDatabase
import io.github.brad1014z.hanzi.engine.social.AccountRepository
import io.github.brad1014z.hanzi.engine.social.SocialRepository
import io.github.brad1014z.hanzi.engine.social.SyncRepository

/**
 * The optional cloud layer's composition root (spec 12): picks the Firebase
 * implementations when google-services.json configured a default FirebaseApp, and the
 * offline fakes otherwise — so every social surface is demoable (and testable) with no
 * Firebase project at all. Signed-out behavior is identical either way (constitution).
 */
class Cloud private constructor(
    val account: AccountRepository,
    val social: SocialRepository,
    val sync: SyncRepository,
    /** True when running on the fakes — UI labels the surfaces "preview". */
    val isPreview: Boolean,
) {
    companion object {
        @Volatile private var instance: Cloud? = null

        /** MainActivity supplies the activity CredentialManager needs for its sheet. */
        var activityProvider: () -> Activity? = { null }

        fun get(context: Context): Cloud = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(context: Context): Cloud {
            val firebaseReady = runCatching {
                FirebaseApp.initializeApp(context) != null || FirebaseApp.getApps(context).isNotEmpty()
            }.getOrDefault(false)
            val db = HanziDatabase.get(context)
            return if (firebaseReady) {
                val account = FirebaseAccountRepository(context) { activityProvider() }
                Cloud(
                    account = account,
                    social = FirestoreSocialRepository(),
                    sync = FirestoreSyncRepository(db),
                    isPreview = false,
                )
            } else {
                val fake = FakeCloud(db)
                Cloud(fake.account, fake.social, fake.sync, isPreview = true)
            }
        }
    }
}
