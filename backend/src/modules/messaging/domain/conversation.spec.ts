import { orderedPair, SelfConversationError } from './conversation.js';

describe('orderedPair', () => {
  it('range la paire dans le même ordre, quel que soit celui qui démarre', () => {
    expect(orderedPair('b', 'a')).toEqual({ userAId: 'a', userBId: 'b' });
    expect(orderedPair('a', 'b')).toEqual({ userAId: 'a', userBId: 'b' });
  });

  it('refuse une conversation avec soi-même', () => {
    expect(() => orderedPair('a', 'a')).toThrow(SelfConversationError);
  });
});
