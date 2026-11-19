package com.mapledger.app.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.mapledger.app.MainActivity
import com.mapledger.app.R

/**
 * Optional, opt-in drive detection. It only ever *asks* — tapping the prompt opens MapLedger in the
 * foreground where the user confirms the start. Detected drives are never auto-classified.
 */
class DriveDetectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val running = TrackingState.state.value.running
        result.transitionEvents.forEach { e ->
            if (e.activityType != DetectedActivity.IN_VEHICLE) return@forEach
            when {
                e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER && !running ->
                    prompt(context, "Driving detected", "Tap to start a MapLedger trip (you'll confirm first).", "drive?confirmStart=1")
                e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT && running ->
                    prompt(context, "Drive may have ended", "Tap to review and stop your trip.", "drive")
            }
        }
    }

    private fun prompt(ctx: Context, title: String, text: String, route: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(ctx, 20, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, TrackingService.CHANNEL_ALERTS).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(pi).build()
        ctx.getSystemService(android.app.NotificationManager::class.java).notify(TrackingService.NOTIF_DRIVE, n)
    }

    companion object {
        private fun pending(ctx: Context) = PendingIntent.getBroadcast(ctx, 30, Intent(ctx, DriveDetectionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        fun hasPermission(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

        @SuppressLint("MissingPermission")
        fun enable(ctx: Context): Boolean {
            if (!hasPermission(ctx)) return false
            val transitions = listOf(
                ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
                ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),
            )
            ActivityRecognition.getClient(ctx).requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pending(ctx))
            return true
        }

        @SuppressLint("MissingPermission")
        fun disable(ctx: Context) {
            if (hasPermission(ctx)) ActivityRecognition.getClient(ctx).removeActivityTransitionUpdates(pending(ctx))
        }
    }
}
