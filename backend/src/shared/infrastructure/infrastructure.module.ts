import { Global, Inject, Module, OnApplicationShutdown } from '@nestjs/common';
import { Redis } from 'ioredis';
import { requireEnv } from './env.js';
import { PrismaService } from './prisma.service.js';

export const REDIS_CLIENT = Symbol('REDIS_CLIENT');

// Connexions partagées par tous les modules : PostgreSQL via Prisma, et Redis.
@Global()
@Module({
  providers: [
    PrismaService,
    {
      provide: REDIS_CLIENT,
      useFactory: () => new Redis(requireEnv('REDIS_URL')),
    },
  ],
  exports: [PrismaService, REDIS_CLIENT],
})
export class InfrastructureModule implements OnApplicationShutdown {
  constructor(@Inject(REDIS_CLIENT) private readonly redis: Redis) {}

  async onApplicationShutdown() {
    await this.redis.quit();
  }
}
