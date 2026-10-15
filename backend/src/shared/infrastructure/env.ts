// Lit une variable d'environnement obligatoire et échoue au démarrage si elle manque.
export function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Variable d'environnement manquante : ${name}`);
  }
  return value;
}
