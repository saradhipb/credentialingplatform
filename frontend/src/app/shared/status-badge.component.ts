import { Component, Input } from '@angular/core';
import { Tone } from './status';

/**
 * status-badge.component.ts
 *
 * A pill showing one status. The component pairs the tone colour with a glyph
 * (supplied by the stylesheet) and the label text, so the status can be read
 * without perceiving the colour. The glyph itself is hidden from assistive
 * technology because the label already carries the meaning, and announcing
 * "check mark Verified" would be noise.
 */
@Component({
  selector: 'cp-status-badge',
  standalone: true,
  template: `<span class="badge {{ tone }}">{{ label }}</span>`
})
export class StatusBadgeComponent {

  /** The text shown inside the badge. */
  @Input({ required: true }) label = '';

  /** Which of the four visual tones to use. */
  @Input() tone: Tone = 'info';
}
