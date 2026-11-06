import { JwtService } from '@nestjs/jwt';
import { participantName } from '../domain/participant-token.js';
import type { LivesRepository } from '../domain/ports.js';
import { JwtParticipantTokenSigner } from '../infrastructure/jwt-participant-token-signer.js';
import {
  IssueParticipantTokenUseCase,
  LiveSdkNotConfiguredError,
  ParticipantUnavailableError,
} from './participant-token.use-case.js';

const SECRET = 'secret-de-test';

function repositoryWith(
  author: { id: string; displayName: string; avatarUrl: string | null } | null,
): LivesRepository {
  return {
    findAuthor: () => Promise.resolve(author),
  } as unknown as LivesRepository;
}

function decode(token: string): Record<string, unknown> {
  return new JwtService({}).verify(token, {
    secret: SECRET,
    algorithms: ['HS256'],
  });
}

describe('participantName', () => {
  it('tronque à 30 caractères visibles sans couper un emoji composé', () => {
    const family = '👨‍👩‍👧';
    const name = participantName(`${'a'.repeat(29)}${family}${family}`);
    expect(name).toBe(`${'a'.repeat(29)}${family}`);
  });

  it('refuse un nom vide', () => {
    expect(participantName('   ')).toBeNull();
  });
});

describe('IssueParticipantTokenUseCase', () => {
  const signer = new JwtParticipantTokenSigner('demo-android', SECRET);

  it('signe un jeton HS256 de 5 minutes avec iss, sub et name', async () => {
    const useCase = new IssueParticipantTokenUseCase(
      repositoryWith({
        id: 'u1',
        displayName: ' Léa ',
        avatarUrl: '/media/avatars/u1.png',
      }),
      signer,
    );
    const claims = decode(await useCase.execute('u1'));
    expect(claims).toMatchObject({
      iss: 'demo-android',
      sub: 'u1',
      name: 'Léa',
    });
    expect(claims.picture).toBeUndefined();
    expect((claims.exp as number) - (claims.iat as number)).toBe(300);
  });

  it("transmet l'avatar seulement en https", async () => {
    const useCase = new IssueParticipantTokenUseCase(
      repositoryWith({
        id: 'u1',
        displayName: 'Léa',
        avatarUrl: 'https://cdn.example.com/a.png',
      }),
      signer,
    );
    expect(decode(await useCase.execute('u1')).picture).toBe(
      'https://cdn.example.com/a.png',
    );
  });

  it('refuse sans configuration, ou sans compte', async () => {
    await expect(
      new IssueParticipantTokenUseCase(repositoryWith(null), null).execute(
        'u1',
      ),
    ).rejects.toBeInstanceOf(LiveSdkNotConfiguredError);
    await expect(
      new IssueParticipantTokenUseCase(repositoryWith(null), signer).execute(
        'u1',
      ),
    ).rejects.toBeInstanceOf(ParticipantUnavailableError);
  });
});
