package site.brutt.betterred.share

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import site.brutt.betterred.widget.Widgets
import java.util.concurrent.TimeUnit

/**
 * Background sync (WorkManager). Runs every [Share.SYNC_MINUTES] minutes while paired, only with a
 * network, so missed updates catch up after she (or the partner) was offline.
 */
class ShareWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val error = ShareSync.sync(applicationContext)
        Widgets.updateAll(applicationContext) // the partner's widgets show her cycle
        // Being offline is retried by WorkManager (with back-off); other errors wait for the next run.
        return if (error == SyncError.OFFLINE && tags.contains(TAG_SOON)) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "share-sync"
        private const val SOON = "share-soon"
        private const val TAG_SOON = "soon"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Keeps the regular sync scheduled (call while paired). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ShareWorker>(Share.SYNC_MINUTES, TimeUnit.MINUTES)
                .setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One extra sync shortly (after she changed something), waiting for a network if needed. */
        fun soon(context: Context) {
            val request = OneTimeWorkRequestBuilder<ShareWorker>().setInitialDelay(5, TimeUnit.SECONDS)
                .setConstraints(online).addTag(TAG_SOON).build()
            WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply { cancelUniqueWork(PERIODIC); cancelUniqueWork(SOON) }
        }
    }
}
