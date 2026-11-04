import { subscriptionEnd } from './premium.js';

describe('subscriptionEnd', () => {
  it("fait expirer l'abonnement le même jour du mois suivant", () => {
    const start = new Date(2026, 10, 3, 21, 30);
    expect(subscriptionEnd(start)).toEqual(new Date(2026, 11, 3, 21, 30));
  });

  it('passe à l’année suivante en décembre', () => {
    expect(subscriptionEnd(new Date(2026, 11, 15))).toEqual(
      new Date(2027, 0, 15),
    );
  });

  it('cale sur le dernier jour du mois quand le jour n’existe pas : 31 janvier, 28 février', () => {
    expect(subscriptionEnd(new Date(2026, 0, 31, 10, 0))).toEqual(
      new Date(2026, 1, 28, 10, 0),
    );
  });

  it('cale sur le 29 février une année bissextile', () => {
    expect(subscriptionEnd(new Date(2028, 0, 31))).toEqual(
      new Date(2028, 1, 29),
    );
  });

  it('cale le 31 mars sur le 30 avril', () => {
    expect(subscriptionEnd(new Date(2026, 2, 31))).toEqual(
      new Date(2026, 3, 30),
    );
  });

  it('ne modifie pas la date de départ', () => {
    const start = new Date(2026, 10, 3);
    subscriptionEnd(start);
    expect(start).toEqual(new Date(2026, 10, 3));
  });
});
