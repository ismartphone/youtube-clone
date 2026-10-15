// Mise en forme des nombres et des durées pour l'affichage.

const numberFormat = new Intl.NumberFormat("fr-FR");

export function formatNumber(value: number): string {
  return numberFormat.format(value);
}

/** Durée au format YouTube : 4:05, ou 1:02:30 au-delà d'une heure. */
export function formatDuration(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = String(totalSeconds % 60).padStart(2, "0");
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, "0")}:${seconds}`
    : `${minutes}:${seconds}`;
}
