import { Injectable } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

/**
 * Service to manage custom SVG icons from assets directory
 */
@Injectable({
  providedIn: 'root'
})
export class IconsService {

  private readonly ICONS_PATH = '/assets/icons/';

  constructor(private sanitizer: DomSanitizer) { }

  /**
   * Get safe URL for custom icon SVG from assets
   * @param iconName Name of the icon file (without .svg extension)
   * @returns Safe resource URL for binding in templates
   */
  getIconUrl(iconName: string): SafeResourceUrl {
    const iconPath = `${this.ICONS_PATH}${iconName}.svg`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(iconPath);
  }

  /**
   * List of available custom icons
   */
  readonly ICONS = {
    heart: 'heart',
    close: 'close',
    star: 'star',
    info: 'info',
    location: 'location',
    back: 'back',
    send: 'send',
    image: 'image',
    closeX: 'close-x'
  };
}

