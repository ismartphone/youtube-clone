package com.youtubeclone.app.presentation.onboarding

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.usecase.ObserveOnboardingFeedbackUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sons et vibrations du parcours d'accueil (ADR-028), par les API de la plateforme.
 *
 * Les trois sons (res/raw) sont de courts bips générés une fois avec FFmpeg, hors de l'application :
 *   step    : sinus 740 Hz, 90 ms          choice : sinus 1175 Hz, 70 ms
 *   success : 523 Hz, 659 Hz puis 784 Hz (arpège de 520 ms)
 * chacun à volume 0,35 avec fondu d'entrée et de sortie, en WAV mono 16 bits 44,1 kHz.
 *
 * Règles : le réglage « Sons et vibrations » coupe tout ; aucun son hors du mode sonnerie normale ;
 * aucune vibration en mode silencieux (elles restent permises en mode vibreur).
 */
@Singleton
class OnboardingFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
    observeEnabled: ObserveOnboardingFeedbackUseCase,
) {
    @Volatile private var enabled = true

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            observeEnabled().collect { enabled = it }
        }
    }

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    // Créé au premier son : un appareil qui n'en joue jamais ne charge rien.
    private val sounds: Sounds by lazy { Sounds(context) }

    /** Charge les sons à l'avance : le chargement est asynchrone, le premier bip ne doit pas être perdu. */
    fun warmUp() {
        sounds
    }

    /** Appui sur un bouton : vibration légère. */
    fun tap() = vibrate(LIGHT_MS, LIGHT_AMPLITUDE)

    /** Passage d'une étape à la suivante : son court. */
    fun step() = play(sounds.step)

    /** Choix validé (puce, option) : son de validation et vibration légère. */
    fun choice() {
        play(sounds.choice)
        tap()
    }

    /** Fin du parcours : son de réussite et vibration plus marquée. */
    fun success() {
        play(sounds.success)
        vibrate(STRONG_MS, STRONG_AMPLITUDE)
    }

    private fun play(soundId: Int) {
        if (!enabled || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        sounds.pool.play(soundId, VOLUME, VOLUME, 1, 0, 1f)
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        if (!enabled || audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
    }

    private class Sounds(context: Context) {
        val pool: SoundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        val step = pool.load(context, R.raw.onboarding_step, 1)
        val choice = pool.load(context, R.raw.onboarding_choice, 1)
        val success = pool.load(context, R.raw.onboarding_success, 1)
    }

    private companion object {
        const val VOLUME = 1f
        const val LIGHT_MS = 12L
        const val LIGHT_AMPLITUDE = 80
        const val STRONG_MS = 140L
        const val STRONG_AMPLITUDE = 255
    }
}
