package com.youtubeclone.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.youtubeclone.app.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Fichier distinct de la session (TokenStore) : ces préférences ne sont pas secrètes
// et n'ont pas besoin d'être chiffrées.
private val Context.preferencesDataStore by preferencesDataStore(name = "preferences")

class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : PreferencesRepository {

    private val dataStore = context.preferencesDataStore

    // Fichier illisible : on repart des valeurs par défaut plutôt que de bloquer le démarrage.
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val onboardingCompleted: Flow<Boolean> =
        preferences.map { it[ONBOARDING_COMPLETED] ?: false }.distinctUntilChanged()

    override val interests: Flow<Set<String>> =
        preferences.map { it[INTERESTS] ?: emptySet() }.distinctUntilChanged()

    override suspend fun completeOnboarding(interests: Set<String>) {
        dataStore.edit {
            it[INTERESTS] = interests
            it[ONBOARDING_COMPLETED] = true
        }
    }

    override suspend fun setInterests(interests: Set<String>) {
        dataStore.edit { it[INTERESTS] = interests }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val INTERESTS = stringSetPreferencesKey("interest_category_ids")
    }
}
