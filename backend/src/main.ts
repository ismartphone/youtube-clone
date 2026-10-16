import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { WsAdapter } from '@nestjs/platform-ws';
import { AppModule } from './app.module.js';
import {
  MEDIA_URL_PREFIX,
  mediaDirectory,
} from './shared/infrastructure/media.config.js';

// En développement, les variables viennent du fichier .env ; en production, de l'environnement.
try {
  process.loadEnvFile();
} catch {
  // Pas de fichier .env : on garde les variables déjà définies.
}

async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule);
  // Fichiers générés par le serveur (miniatures), servis en lecture seule : /media/thumbnails/<id>.jpg.
  // Chaque nom est unique : les clients peuvent garder l'image en cache longtemps.
  app.useStaticAssets(mediaDirectory(), {
    prefix: `${MEDIA_URL_PREFIX}/`,
    index: false,
    maxAge: '7d',
  });
  // Refuse les paramètres inconnus et convertit les paramètres de requête dans leur type.
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );
  // WebSocket standard (bibliothèque ws), sur le même port que l'API : le client
  // OkHttp de l'application parle ce protocole, pas celui de Socket.IO (ADR-004).
  app.useWebSocketAdapter(new WsAdapter(app));
  // Déclenche les hooks d'arrêt, pour reporter les vues en attente avant de couper.
  app.enableShutdownHooks();
  await app.listen(process.env.PORT ?? 3000);
}
await bootstrap();
