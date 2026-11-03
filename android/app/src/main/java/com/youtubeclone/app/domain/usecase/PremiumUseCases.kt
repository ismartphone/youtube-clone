package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.PremiumStatus
import com.youtubeclone.app.domain.repository.PremiumRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

// Offre Premium : achat simulé par notre backend (ADR-012), état toujours décidé par le serveur.

/** État Premium partagé par tous les écrans ; null tant qu'il n'est pas connu. */
class ObservePremiumStatusUseCase @Inject constructor(
    private val repository: PremiumRepository,
) {
    operator fun invoke(): StateFlow<PremiumStatus?> = repository.status
}

class RefreshPremiumStatusUseCase @Inject constructor(
    private val repository: PremiumRepository,
) {
    suspend operator fun invoke(): PremiumStatus = repository.refresh()
}

/**
 * L'utilisateur est-il Premium en ce moment ? Sert aux décisions d'affichage (publicité,
 * lecture en arrière-plan). Si l'état ne peut pas être lu, il est traité comme non Premium :
 * l'accès aux vidéos exclusives, lui, reste vérifié par le serveur.
 */
class IsPremiumUseCase @Inject constructor(
    private val repository: PremiumRepository,
) {
    suspend operator fun invoke(): Boolean {
        repository.status.value?.let { return it.isPremium }
        return try {
            repository.refresh().isPremium
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}

/** Étape 1 de l'achat : le serveur émet un jeton. Exige une session ouverte. */
class StartPremiumCheckoutUseCase @Inject constructor(
    private val repository: PremiumRepository,
) {
    suspend operator fun invoke(): String = repository.startCheckout()
}

/** Étape 2 : le serveur vérifie le jeton et active l'abonnement s'il l'accepte. */
class ConfirmPremiumPurchaseUseCase @Inject constructor(
    private val repository: PremiumRepository,
) {
    suspend operator fun invoke(purchaseToken: String): PremiumStatus = repository.confirmPurchase(purchaseToken)
}

/** Fin immédiate de l'abonnement. */
class CancelPremiumUseCase @Inject constructor(
    private val repository: PremiumRepository,
) {
    suspend operator fun invoke(): PremiumStatus = repository.cancel()
}
