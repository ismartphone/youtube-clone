import { registerDecorator, type ValidationOptions } from 'class-validator';

// Message renvoyé quand une URL vise la machine qui héberge le serveur.
export const LOCAL_URL_MESSAGE =
  "Cette adresse pointe vers localhost : elle n'est pas joignable depuis un téléphone. " +
  'Utilisez le chemin relatif (/media/...) ou une adresse publique.';

/**
 * Vrai pour une URL complète vers la machine locale (localhost, 127.x, 0.0.0.0, ::1) ou vers
 * 10.0.2.2, l'alias de l'ordinateur vu depuis l'émulateur Android. Un chemin relatif ou une
 * valeur qui n'est pas une URL ne sont pas concernés : les autres contrôles s'en chargent.
 * Une adresse du réseau local (192.168.x.x) reste permise, un téléphone sur le même Wi-Fi la joint.
 */
export function isLocalUrl(value: unknown): boolean {
  if (typeof value !== 'string') {
    return false;
  }
  let hostname: string;
  try {
    hostname = new URL(value.trim()).hostname.toLowerCase();
  } catch {
    return false;
  }
  return (
    hostname === 'localhost' ||
    hostname.endsWith('.localhost') ||
    hostname === '0.0.0.0' ||
    hostname === '[::1]' ||
    hostname === '10.0.2.2' ||
    /^127(\.\d{1,3}){3}$/.test(hostname)
  );
}

/** Refuse une URL complète vers la machine locale. À placer après `@IsUrl`. */
export function IsNotLocalUrl(validationOptions?: ValidationOptions) {
  return (object: object, propertyName: string) => {
    registerDecorator({
      name: 'isNotLocalUrl',
      target: object.constructor,
      propertyName,
      options: { message: LOCAL_URL_MESSAGE, ...validationOptions },
      validator: {
        validate: (value: unknown) => !isLocalUrl(value),
      },
    });
  };
}
