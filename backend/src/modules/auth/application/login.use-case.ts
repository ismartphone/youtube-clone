import { InvalidCredentialsError } from '../domain/auth.errors.js';
import type {
  PasswordHasher,
  RefreshTokenStore,
  TokenService,
  UserRepository,
} from '../domain/ports.js';
import { normalizeEmail } from '../domain/user.js';
import { issueTokenPair, type TokenPair } from './token-pair.js';

export class LoginUseCase {
  // Hash factice vérifié quand l'email est inconnu : la réponse prend alors le même temps
  // qu'avec un mauvais mot de passe, ce qui évite de deviner les comptes existants.
  private dummyHash?: Promise<string>;

  constructor(
    private readonly users: UserRepository,
    private readonly hasher: PasswordHasher,
    private readonly tokens: TokenService,
    private readonly refreshStore: RefreshTokenStore,
  ) {}

  async execute(email: string, password: string): Promise<TokenPair> {
    const credentials = await this.users.findCredentialsByEmail(
      normalizeEmail(email),
    );
    if (!credentials) {
      this.dummyHash ??= this.hasher.hash('mot-de-passe-factice');
      await this.hasher.verify(await this.dummyHash, password);
      throw new InvalidCredentialsError();
    }
    if (!(await this.hasher.verify(credentials.passwordHash, password))) {
      throw new InvalidCredentialsError();
    }
    const { id, role } = credentials.user;
    return issueTokenPair({ id, role }, this.tokens, this.refreshStore);
  }
}
