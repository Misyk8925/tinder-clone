import { describe, expect, it } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import {
  HeicConversionError,
  PHOTO_UPLOAD_MAX_BYTES,
  PhotoTooLargeError,
  photoUploadFailureMessage,
  preparePhotoForUpload
} from './photo-upload';

describe('photo upload helpers', () => {
  it('Given a file under the 5MB cap, when prepared, then it is uploaded as-is', async () => {
    const file = new File([new Uint8Array(1024)], 'shot.jpg', { type: 'image/jpeg' });

    const prepared = await preparePhotoForUpload(file, { maxSizeBytes: PHOTO_UPLOAD_MAX_BYTES });

    expect(prepared).toBe(file);
    expect(prepared.size).toBe(1024);
  });

  it('Given a 400 whose message is Image too large, when mapping the toast, then the user is told the photo is too large', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { code: 'INVALID_IMAGE', message: 'Image too large (6869479 bytes)' }
    });

    expect(photoUploadFailureMessage(error)).toBe('Photo is too large. Please choose a smaller image.');
  });

  it('Given a local PhotoTooLargeError, when mapping the toast, then the same message is shown', () => {
    expect(photoUploadFailureMessage(new PhotoTooLargeError()))
      .toBe('Photo is too large. Please choose a smaller image.');
  });

  it('Given a HEIC conversion failure, when mapping the toast, then a conversion message is shown', () => {
    expect(photoUploadFailureMessage(new HeicConversionError()))
      .toBe('HEIC conversion failed. Please choose another photo.');
  });
});
