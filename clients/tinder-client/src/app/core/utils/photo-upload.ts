import { HttpErrorResponse } from '@angular/common/http';

export const PHOTO_UPLOAD_MAX_BYTES = 5 * 1024 * 1024;
export const PHOTO_UPLOAD_MAX_EDGE_PX = 4096;
const JPEG_QUALITY_STEPS = [0.9, 0.8, 0.7, 0.6, 0.5, 0.4];

export class PhotoTooLargeError extends Error {
  override readonly name = 'PhotoTooLargeError';
  constructor() {
    super('Photo is too large. Please choose a smaller image.');
  }
}

export class HeicConversionError extends Error {
  override readonly name = 'HeicConversionError';
  constructor() {
    super('HEIC conversion failed. Please choose another photo.');
  }
}

export function isHeicPhoto(file: File): boolean {
  return file.type === 'image/heic' || file.type === 'image/heif' || /\.(heic|heif)$/i.test(file.name);
}

/** HEIC conversion, optional downscale, then JPEG quality steps until under the byte cap. */
export async function preparePhotoForUpload(
  file: File,
  options: { maxSizeBytes?: number; maxEdgePx?: number } = {}
): Promise<File> {
  const maxSizeBytes = options.maxSizeBytes ?? PHOTO_UPLOAD_MAX_BYTES;
  const maxEdgePx = options.maxEdgePx;
  let prepared = file;

  if (isHeicPhoto(file)) {
    try {
      const heic2any = (await import('heic2any')).default;
      const converted = await heic2any({ blob: file, toType: 'image/jpeg', quality: 0.9 });
      const blob = Array.isArray(converted) ? converted[0] : converted;
      prepared = new File([blob], file.name.replace(/\.(heic|heif)$/i, '.jpg'), { type: 'image/jpeg' });
    } catch {
      throw new HeicConversionError();
    }
  }

  const needsResize = maxEdgePx != null && await exceedsEdge(prepared, maxEdgePx);
  if (prepared.size <= maxSizeBytes && !needsResize) {
    return prepared;
  }

  const compressed = await compressToJpegUnderLimit(prepared, maxSizeBytes, maxEdgePx);
  const result = compressed ?? prepared;
  if (result.size > maxSizeBytes) {
    throw new PhotoTooLargeError();
  }
  return result;
}

export function photoUploadFailureMessage(error: unknown): string {
  if (error instanceof PhotoTooLargeError || error instanceof HeicConversionError) {
    return error.message;
  }
  const http = error as HttpErrorResponse;
  const backend = extractBackendErrorMessage(http);
  if (http?.status === 429) {
    return 'Too many uploads. Please wait before uploading again.';
  }
  if (http?.status === 413 || backend?.includes('Image too large')) {
    return 'Photo is too large. Please choose a smaller image.';
  }
  if (backend?.includes('Invalid image type')) {
    return 'Unsupported image type. Please upload JPEG, PNG, or WEBP.';
  }
  if (backend?.includes('Image too small') || backend?.includes('Image dimensions too large')) {
    return 'Photo dimensions are not supported. Please choose another image.';
  }
  return 'Photo upload failed. Please try again.';
}

function extractBackendErrorMessage(error: HttpErrorResponse | undefined): string | null {
  if (typeof error?.error === 'string') {
    return error.error;
  }
  if (typeof error?.error?.message === 'string') {
    return error.error.message;
  }
  return null;
}

async function exceedsEdge(file: File, maxEdgePx: number): Promise<boolean> {
  if (!file.type.startsWith('image/')) {
    return false;
  }
  const image = await loadImage(file);
  const width = image.naturalWidth || image.width;
  const height = image.naturalHeight || image.height;
  return width > maxEdgePx || height > maxEdgePx;
}

async function compressToJpegUnderLimit(
  file: File,
  maxSizeBytes: number,
  maxEdgePx?: number
): Promise<File | null> {
  if (!file.type.startsWith('image/')) {
    return null;
  }

  const image = await loadImage(file);
  const sourceWidth = image.naturalWidth || image.width;
  const sourceHeight = image.naturalHeight || image.height;
  const scale = maxEdgePx
    ? Math.min(1, maxEdgePx / Math.max(sourceWidth, sourceHeight))
    : 1;
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(sourceWidth * scale));
  canvas.height = Math.max(1, Math.round(sourceHeight * scale));

  const context = canvas.getContext('2d');
  if (!context) {
    return null;
  }
  context.drawImage(image, 0, 0, canvas.width, canvas.height);

  let smallestBlob: Blob | null = null;
  for (const quality of JPEG_QUALITY_STEPS) {
    const blob = await canvasToJpegBlob(canvas, quality);
    if (!blob) {
      continue;
    }
    if (!smallestBlob || blob.size < smallestBlob.size) {
      smallestBlob = blob;
    }
    if (blob.size <= maxSizeBytes) {
      return new File([blob], file.name.replace(/\.[^.]+$/, '.jpg'), { type: 'image/jpeg' });
    }
  }
  if (!smallestBlob) {
    return null;
  }
  return new File([smallestBlob], file.name.replace(/\.[^.]+$/, '.jpg'), { type: 'image/jpeg' });
}

async function loadImage(file: File): Promise<HTMLImageElement> {
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error('Failed to read image'));
    reader.readAsDataURL(file);
  });
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Failed to decode image'));
    img.src = dataUrl;
  });
}

function canvasToJpegBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob | null> {
  return new Promise(resolve => {
    canvas.toBlob(blob => resolve(blob), 'image/jpeg', quality);
  });
}
