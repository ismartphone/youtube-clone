import {
  ArgumentsHost,
  CallHandler,
  Catch,
  ExceptionFilter,
  ExecutionContext,
  HttpStatus,
  Injectable,
  NestInterceptor,
  PayloadTooLargeException,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { rm } from 'node:fs/promises';
import { finalize, type Observable } from 'rxjs';

// Outils communs aux envois de fichiers depuis l'application (ADR-015) : vidéos et images de posts.

// Seuls le chemin du fichier temporaire et sa présence nous intéressent : le nom et le type
// annoncés par le client sont ignorés.
export interface ReceivedFile {
  path: string;
}

// Le fichier temporaire est supprimé quoi qu'il arrive : refusé, corps invalide ou erreur.
// Accepté, il a déjà été déplacé dans le stockage et la suppression est sans effet.
@Injectable()
export class RemoveTempFileInterceptor implements NestInterceptor {
  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context
      .switchToHttp()
      .getRequest<Request & { file?: ReceivedFile }>();
    return next.handle().pipe(
      finalize(() => {
        if (request.file) {
          void rm(request.file.path, { force: true });
        }
      }),
    );
  }
}

// Fichier trop gros, arrêté par multer pendant la réception : message en français.
@Catch(PayloadTooLargeException)
export class FileTooLargeFilter implements ExceptionFilter {
  constructor(private readonly message: string) {}

  catch(_exception: PayloadTooLargeException, host: ArgumentsHost) {
    host
      .switchToHttp()
      .getResponse<Response>()
      .status(HttpStatus.PAYLOAD_TOO_LARGE)
      .json({
        statusCode: HttpStatus.PAYLOAD_TOO_LARGE,
        message: this.message,
        error: 'Payload Too Large',
      });
  }
}
