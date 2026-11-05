import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Put,
  ServiceUnavailableException,
  UnprocessableEntityException,
  UploadedFile,
  UseFilters,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { Transform } from 'class-transformer';
import {
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Length,
  Max,
  Min,
} from 'class-validator';
import { tmpdir } from 'node:os';
import {
  FileTooLargeFilter,
  RemoveTempFileInterceptor,
  type ReceivedFile,
} from '../../../../shared/interfaces/http/file-upload.js';
import { AdminGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import {
  ChannelImageUnavailableError,
  InvalidChannelImageError,
} from '../../../channels/domain/channel.errors.js';
import { ManageProductsUseCase } from '../../application/products.use-cases.js';
import {
  PRODUCT_DESCRIPTION_MAX_LENGTH,
  PRODUCT_IMAGE_MAX_BYTES,
  PRODUCT_MAX_PRICE_CENTS,
  PRODUCT_MIN_PRICE_CENTS,
  PRODUCT_NAME_MAX_LENGTH,
  ProductChannelNotFoundError,
  ProductNotFoundError,
  type Product,
} from '../../domain/product.js';

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;
// Les champs d'un envoi multipart arrivent en texte : le prix est converti en nombre.
const toNumber = ({ value }: { value: unknown }) =>
  typeof value === 'string' && value.trim() !== '' ? Number(value) : value;

const NAME_MESSAGE = `Le nom doit faire entre 1 et ${PRODUCT_NAME_MAX_LENGTH} caractères.`;
const DESCRIPTION_MESSAGE = `La description doit faire entre 1 et ${PRODUCT_DESCRIPTION_MAX_LENGTH} caractères.`;
const PRICE_MESSAGE = `Le prix, en centimes, doit être un entier entre ${PRODUCT_MIN_PRICE_CENTS} (0,50 €, minimum de Stripe) et ${PRODUCT_MAX_PRICE_CENTS}.`;

// Champs texte de l'ajout multipart, à côté de l'image (champ « image »).
export class CreateProductBody {
  @IsUUID(undefined, { message: 'Choisissez une chaîne.' })
  channelId: string;

  @Transform(trim)
  @IsString()
  @Length(1, PRODUCT_NAME_MAX_LENGTH, { message: NAME_MESSAGE })
  name: string;

  @Transform(toNumber)
  @IsInt({ message: PRICE_MESSAGE })
  @Min(PRODUCT_MIN_PRICE_CENTS, { message: PRICE_MESSAGE })
  @Max(PRODUCT_MAX_PRICE_CENTS, { message: PRICE_MESSAGE })
  price: number;

  @Transform(trim)
  @IsString()
  @Length(1, PRODUCT_DESCRIPTION_MAX_LENGTH, { message: DESCRIPTION_MESSAGE })
  description: string;
}

// Corps de PATCH /admin/products/:id : un champ absent n'est pas modifié.
export class UpdateProductBody {
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, PRODUCT_NAME_MAX_LENGTH, { message: NAME_MESSAGE })
  name?: string;

  @IsOptional()
  @Transform(toNumber)
  @IsInt({ message: PRICE_MESSAGE })
  @Min(PRODUCT_MIN_PRICE_CENTS, { message: PRICE_MESSAGE })
  @Max(PRODUCT_MAX_PRICE_CENTS, { message: PRICE_MESSAGE })
  price?: number;

  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, PRODUCT_DESCRIPTION_MAX_LENGTH, { message: DESCRIPTION_MESSAGE })
  description?: string;
}

const IMAGE_TOO_LARGE = new FileTooLargeFilter(
  `L'image dépasse la taille maximale de ${PRODUCT_IMAGE_MAX_BYTES / 1024 / 1024} Mo.`,
);

// Même forme pour la liste, l'ajout et la modification.
function present(product: Product) {
  return {
    id: product.id,
    channel: product.channel,
    name: product.name,
    price: product.price,
    currency: product.currency,
    imageUrl: product.imageUrl,
    description: product.description,
    createdAt: product.createdAt,
  };
}

// Produits du back-office (ADR-029, D11) : liste, ajout avec image, modification, suppression.
// Le garde passe avant la réception : seul un administrateur écrit sur le disque.
@Controller('admin/products')
@UseGuards(AdminGuard)
export class AdminProductsController {
  constructor(private readonly products: ManageProductsUseCase) {}

  @Get()
  async list() {
    return { items: (await this.products.list()).map(present) };
  }

  @Post()
  @UseFilters(IMAGE_TOO_LARGE)
  @UseInterceptors(
    FileInterceptor('image', {
      // Nom aléatoire sans extension, généré par multer : celui envoyé n'est jamais repris.
      dest: tmpdir(),
      limits: { fileSize: PRODUCT_IMAGE_MAX_BYTES, files: 1 },
    }),
    RemoveTempFileInterceptor,
  )
  async create(
    @UploadedFile() image: ReceivedFile | undefined,
    @Body() body: CreateProductBody,
  ) {
    if (!image) {
      throw new BadRequestException('Choisissez une image.');
    }
    try {
      return present(
        await this.products.create(image.path, {
          channelId: body.channelId,
          name: body.name,
          price: body.price,
          description: body.description,
        }),
      );
    } catch (error) {
      throw this.translate(error);
    }
  }

  @Patch(':id')
  async update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: UpdateProductBody,
  ) {
    try {
      return present(
        await this.products.update(id, {
          name: body.name,
          price: body.price,
          description: body.description,
        }),
      );
    } catch (error) {
      throw this.translate(error);
    }
  }

  @Put(':id/image')
  @UseFilters(IMAGE_TOO_LARGE)
  @UseInterceptors(
    FileInterceptor('image', {
      dest: tmpdir(),
      limits: { fileSize: PRODUCT_IMAGE_MAX_BYTES, files: 1 },
    }),
    RemoveTempFileInterceptor,
  )
  async setImage(
    @Param('id', ParseUUIDPipe) id: string,
    @UploadedFile() image: ReceivedFile | undefined,
  ) {
    if (!image) {
      throw new BadRequestException('Choisissez une image.');
    }
    try {
      return present(await this.products.setImage(id, image.path));
    } catch (error) {
      throw this.translate(error);
    }
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(@Param('id', ParseUUIDPipe) id: string) {
    try {
      await this.products.remove(id);
    } catch (error) {
      throw this.translate(error);
    }
  }

  private translate(error: unknown): unknown {
    if (
      error instanceof ProductNotFoundError ||
      error instanceof ProductChannelNotFoundError
    ) {
      return new NotFoundException(error.message);
    }
    if (error instanceof InvalidChannelImageError) {
      return new UnprocessableEntityException(error.message);
    }
    if (error instanceof ChannelImageUnavailableError) {
      return new ServiceUnavailableException(error.message);
    }
    return error;
  }
}
