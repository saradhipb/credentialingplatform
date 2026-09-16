import { Component, Input } from '@angular/core';
import { Tone } from './status';

/**
 * kpi-tile.component.ts
 *
 * One figure on the dashboard. The value is rendered before the label in the
 * document so that a screen reader announces the number with its meaning
 * immediately after, and the accent colour is carried on a left border rather
 * than on the text, which keeps the text contrast constant across tones.
 */
@Component({
  selector: 'cp-kpi-tile',
  standalone: true,
  template: `
    <div class="kpi tone-{{ tone }}">
      <span class="value">{{ value }}</span>
      <span class="label">{{ label }}</span>
    </div>
  `
})
export class KpiTileComponent {

  /** The figure to display; already formatted by the caller. */
  @Input({ required: true }) value: string | number = 0;

  /** What the figure means. */
  @Input({ required: true }) label = '';

  /** Accent tone for the tile's left border. */
  @Input() tone: Tone = 'info';
}
