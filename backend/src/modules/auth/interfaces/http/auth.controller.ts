import {
  Body,
  ConflictException,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Post,
  UnauthorizedException,
  UseGuards,
} from '@nestjs/common';
import { GetMeUseCase } from '../../application/get-me.use-case.js';
import { LoginUseCase } from '../../application/login.use-case.js';
import { RefreshUseCase } from '../../application/refresh.use-case.js';
import { RegisterUseCase } from '../../application/register.use-case.js';
import {
  EmailAlreadyUsedError,
  InvalidCredentialsError,
  InvalidRefreshTokenError,
} from '../../domain/auth.errors.js';
import type { AuthenticatedUser } from '../../domain/user.js';
import { LoginBody, RefreshBody, RegisterBody } from './auth.bodies.js';
import { presentTokens, presentUser } from './auth.presenter.js';
import { CurrentUser } from './current-user.decorator.js';
import { JwtAuthGuard } from './jwt-auth.guard.js';

@Controller('auth')
export class AuthController {
  constructor(
    private readonly register: RegisterUseCase,
    private readonly login: LoginUseCase,
    private readonly refresh: RefreshUseCase,
    private readonly getMe: GetMeUseCase,
  ) {}

  @Post('register')
  async registerUser(@Body() body: RegisterBody) {
    try {
      return presentUser(await this.register.execute(body));
    } catch (error) {
      if (error instanceof EmailAlreadyUsedError) {
        throw new ConflictException(error.message);
      }
      throw error;
    }
  }

  @Post('login')
  @HttpCode(HttpStatus.OK)
  async loginUser(@Body() body: LoginBody) {
    try {
      return presentTokens(await this.login.execute(body.email, body.password));
    } catch (error) {
      if (error instanceof InvalidCredentialsError) {
        throw new UnauthorizedException(error.message);
      }
      throw error;
    }
  }

  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  async refreshTokens(@Body() body: RefreshBody) {
    try {
      return presentTokens(await this.refresh.execute(body.refreshToken));
    } catch (error) {
      if (error instanceof InvalidRefreshTokenError) {
        throw new UnauthorizedException(error.message);
      }
      throw error;
    }
  }

  @Get('me')
  @UseGuards(JwtAuthGuard)
  async me(@CurrentUser() current: AuthenticatedUser) {
    const user = await this.getMe.execute(current.id);
    if (!user) {
      // Token encore valide, mais le compte a été supprimé entre-temps.
      throw new NotFoundException('Utilisateur introuvable');
    }
    return presentUser(user);
  }
}
