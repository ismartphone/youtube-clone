import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module.js';

// En développement, les variables viennent du fichier .env ; en production, de l'environnement.
try {
  process.loadEnvFile();
} catch {
  // Pas de fichier .env : on garde les variables déjà définies.
}

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  // Refuse les paramètres inconnus et convertit les paramètres de requête dans leur type.
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );
  // Déclenche les hooks d'arrêt, pour reporter les vues en attente avant de couper.
  app.enableShutdownHooks();
  await app.listen(process.env.PORT ?? 3000);
}
await bootstrap();
