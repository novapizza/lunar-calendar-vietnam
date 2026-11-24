package io.github.novapizza.licham.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Redraws the widgets when the phone's font size changes. Widget text is given in sp divided by the
 * font scale at drawing time ([fixedSp]), but the launcher applies a new scale to what was drawn
 * before: at 1.3 the month grid's lunar days ran into the solar ones, at 2.0 the days overlapped,
 * until the next redraw near midnight (emulator, 2026-11-24). A job triggered by the font_scale
 * setting runs even when the app is not, then waits for the next change. Content-triggered jobs
 * cannot be persisted, so [WidgetClock.schedule] sets it again from the widgets' updates (also
 * sent after a reboot and an app update) and the midnight tick.
 */
class FontScaleJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        WidgetClock.refresh(applicationContext) {
            jobFinished(params, false)
            schedule(applicationContext, again = true)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val ID = 7301

        /** Waits for the next font size change; [again] replaces a job already set (after a run). */
        fun schedule(context: Context, again: Boolean = false) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            // Setting a job again with the same id stops it if it is running: leave a waiting or running one.
            if (!again && jobs.getPendingJob(ID) != null) return
            val uri = Settings.System.getUriFor(Settings.System.FONT_SCALE)
            val job = JobInfo.Builder(ID, ComponentName(context, FontScaleJob::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
                .setTriggerContentUpdateDelay(500)
                .setTriggerContentMaxDelay(5_000)
                .build()
            runCatching { jobs.schedule(job) }
        }
    }
}
