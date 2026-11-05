import Stripe from 'stripe';
import {
  LivePaymentUnavailableError,
  type LivePaymentGateway,
  type LivePaymentStart,
  type LiveProductCard,
  type VerifiedLivePayment,
} from '../domain/shopping.js';

// Les identifiants de PaymentIntent commencent par « pi_ » : toute autre référence est refusée
// sans appeler Stripe.
const PAYMENT_INTENT_PREFIX = 'pi_';

// Paiement d'un produit de live par Stripe en mode test (ADR-029, D8). Mêmes clés que le Premium
// (ADR-014), mais un port à part : le module premium n'est pas touché. La clé secrète reste ici.
export class StripeLivePaymentGateway implements LivePaymentGateway {
  private readonly stripe: Stripe;

  constructor(
    secretKey: string,
    private readonly publishableKey: string,
  ) {
    this.stripe = new Stripe(secretKey);
  }

  // PaymentIntent au prix lu en base par le serveur, rattaché à l'acheteur, au live et au produit
  // par ses métadonnées : seul le serveur peut les écrire (il détient la clé secrète).
  async start(
    buyerId: string,
    liveId: string,
    product: LiveProductCard,
  ): Promise<LivePaymentStart> {
    let intent: Stripe.PaymentIntent;
    try {
      intent = await this.stripe.paymentIntents.create({
        amount: product.price,
        currency: product.currency,
        automatic_payment_methods: { enabled: true },
        metadata: {
          userId: buyerId,
          liveId,
          productId: product.id,
          productName: product.name.slice(0, 200),
        },
      });
    } catch {
      throw new LivePaymentUnavailableError();
    }
    if (!intent.client_secret) {
      throw new LivePaymentUnavailableError();
    }
    return {
      mode: 'stripe',
      reference: intent.id,
      clientSecret: intent.client_secret,
      publishableKey: this.publishableKey,
      expiresAt: null,
    };
  }

  // Le serveur ne croit pas l'application sur parole : il relit le PaymentIntent chez Stripe.
  async verify(
    buyerId: string,
    liveId: string,
    reference: string,
  ): Promise<VerifiedLivePayment | null> {
    if (!reference.startsWith(PAYMENT_INTENT_PREFIX)) {
      return null;
    }
    let intent: Stripe.PaymentIntent;
    try {
      intent = await this.stripe.paymentIntents.retrieve(reference);
    } catch (error) {
      // Identifiant inconnu de Stripe : refus. Toute autre erreur : Stripe indisponible.
      if (error instanceof Stripe.errors.StripeInvalidRequestError) {
        return null;
      }
      throw new LivePaymentUnavailableError();
    }
    const {
      userId,
      liveId: intentLive,
      productId,
      productName,
    } = intent.metadata;
    const paid =
      intent.status === 'succeeded' &&
      userId === buyerId &&
      intentLive === liveId &&
      !!productId &&
      !!productName;
    // Montant encaissé selon Stripe, et non le prix du produit aujourd'hui : c'est lui qui est
    // enregistré.
    return paid
      ? {
          productId,
          productName,
          amount: intent.amount_received,
          currency: intent.currency,
        }
      : null;
  }
}
