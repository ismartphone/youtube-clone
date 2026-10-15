import { Module } from '@nestjs/common';
import { JwtModule, JwtService } from '@nestjs/jwt';
import type { Redis } from 'ioredis';
import { requireEnv } from '../../shared/infrastructure/env.js';
import { REDIS_CLIENT } from '../../shared/infrastructure/infrastructure.module.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { GetMeUseCase } from './application/get-me.use-case.js';
import { LoginUseCase } from './application/login.use-case.js';
import { RefreshUseCase } from './application/refresh.use-case.js';
import { RegisterUseCase } from './application/register.use-case.js';
import {
  PASSWORD_HASHER,
  REFRESH_TOKEN_STORE,
  TOKEN_SERVICE,
  USER_REPOSITORY,
  type PasswordHasher,
  type RefreshTokenStore,
  type TokenService,
  type UserRepository,
} from './domain/ports.js';
import { Argon2PasswordHasher } from './infrastructure/argon2-password-hasher.js';
import { JwtTokenService } from './infrastructure/jwt-token.service.js';
import { PrismaUserRepository } from './infrastructure/prisma-user.repository.js';
import { RedisRefreshTokenStore } from './infrastructure/redis-refresh-token.store.js';
import { AuthController } from './interfaces/http/auth.controller.js';
import { JwtAuthGuard } from './interfaces/http/jwt-auth.guard.js';

// Les cas d'usage ne dépendent que des ports du domaine : c'est ici qu'on branche les adaptateurs.
@Module({
  // Secrets passés à chaque signature : JwtModule ne fournit que le JwtService.
  imports: [JwtModule.register({})],
  controllers: [AuthController],
  providers: [
    {
      provide: USER_REPOSITORY,
      useFactory: (prisma: PrismaService) => new PrismaUserRepository(prisma),
      inject: [PrismaService],
    },
    { provide: PASSWORD_HASHER, useClass: Argon2PasswordHasher },
    {
      provide: TOKEN_SERVICE,
      useFactory: (jwt: JwtService) =>
        new JwtTokenService(jwt, {
          accessSecret: requireEnv('JWT_ACCESS_SECRET'),
          refreshSecret: requireEnv('JWT_REFRESH_SECRET'),
        }),
      inject: [JwtService],
    },
    {
      provide: REFRESH_TOKEN_STORE,
      useFactory: (redis: Redis) => new RedisRefreshTokenStore(redis),
      inject: [REDIS_CLIENT],
    },
    {
      provide: RegisterUseCase,
      useFactory: (users: UserRepository, hasher: PasswordHasher) =>
        new RegisterUseCase(users, hasher),
      inject: [USER_REPOSITORY, PASSWORD_HASHER],
    },
    {
      provide: LoginUseCase,
      useFactory: (
        users: UserRepository,
        hasher: PasswordHasher,
        tokens: TokenService,
        store: RefreshTokenStore,
      ) => new LoginUseCase(users, hasher, tokens, store),
      inject: [
        USER_REPOSITORY,
        PASSWORD_HASHER,
        TOKEN_SERVICE,
        REFRESH_TOKEN_STORE,
      ],
    },
    {
      provide: RefreshUseCase,
      useFactory: (
        users: UserRepository,
        tokens: TokenService,
        store: RefreshTokenStore,
      ) => new RefreshUseCase(users, tokens, store),
      inject: [USER_REPOSITORY, TOKEN_SERVICE, REFRESH_TOKEN_STORE],
    },
    {
      provide: GetMeUseCase,
      useFactory: (users: UserRepository) => new GetMeUseCase(users),
      inject: [USER_REPOSITORY],
    },
    JwtAuthGuard,
  ],
  // Pour protéger les routes d'autres modules : importer AuthModule, puis @UseGuards(JwtAuthGuard).
  exports: [TOKEN_SERVICE, JwtAuthGuard],
})
export class AuthModule {}
