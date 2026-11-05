package com.youtubeclone.app.restart

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process

private const val EXTRA_LAUNCH = "launch_intent"
private const val EXTRA_PID = "pid"

/**
 * Redémarre l'application (ADR-027, debug) : relance l'écran d'accueil puis tue l'ancien
 * processus. Cette activité tourne dans son propre processus (« :restart », voir le manifeste
 * debug) : elle survit à la mort du processus principal, ce que ne ferait pas un simple
 * startActivity suivi d'un kill dans le même processus.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val launch = intent.getParcelableExtra<Intent>(EXTRA_LAUNCH)
        if (launch != null) startActivity(launch)
        Process.killProcess(intent.getIntExtra(EXTRA_PID, -1))
        finish()
        Runtime.getRuntime().exit(0)
    }
}

/** Relance l'application depuis son icône, pile de navigation vidée. */
fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) ?: return
    context.startActivity(
        Intent(context, RestartActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_LAUNCH, launch)
            .putExtra(EXTRA_PID, Process.myPid()),
    )
}
