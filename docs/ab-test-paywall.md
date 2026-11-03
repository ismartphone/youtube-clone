# Test A/B du paywall

Test prévu par l'[ADR-006](adr/ADR-006-analytics-posthog.md), mené avec PostHog.

## Hypothèse

Un titre qui annonce le prix dès l'ouverture du paywall rassure sur le coût et fait plus souvent passer à l'achat qu'un titre qui met en avant les avantages.

| Variante | Titre affiché | Rôle |
|---|---|---|
| `benefits` | « Passez à Premium » | Témoin, variante par défaut |
| `price` | « Premium pour 4,99 € par mois » | Variante testée |

Seul le titre change. Le reste de l'écran est identique dans les deux variantes (avantages, bouton), à une exception près : la variante `price` n'affiche pas le sous-titre « 4,99 € par mois », qui répéterait son titre.

## Métrique regardée

**Taux de conversion du paywall** : nombre de `purchase_confirmed` divisé par le nombre de `paywall_viewed`, par variante.

Métriques secondaires :
- taux de clic sur « S'abonner » : `purchase_started` / `paywall_viewed` ;
- taux d'abandon : `paywall_dismissed` / `paywall_viewed`.

Les quatre événements portent la propriété `paywall_variant`.

## Mise en place

- Drapeau multivariant PostHog `paywall-title`, variantes `benefits` et `price`, réparties à 50/50.
- L'application charge les drapeaux au démarrage et les garde en cache sur l'appareil. La variante est lue à l'ouverture du paywall et ne change plus tant que l'écran est affiché.
- Repli : drapeau absent ou pas encore chargé, PostHog injoignable, ou « Partager des statistiques d'usage » désactivé. La variante `benefits` s'affiche alors, et l'application n'attend jamais le réseau.

## Limites

- L'attribution suit l'identifiant PostHog. Un visiteur qui se connecte change d'identifiant (ADR-013) et peut changer de variante.
- Avec le peu d'utilisateurs d'un projet d'école, l'écart mesuré ne sera pas statistiquement significatif. Le test démontre la mise en place, pas un résultat.
