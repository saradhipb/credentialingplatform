import { Component, Input } from '@angular/core';
import { riskTone } from './status';

/**
 * risk-meter.component.ts
 *
 * A horizontal bar showing a 0-100 composite risk score.
 *
 * The bar is marked up with the ARIA progressbar role and explicit minimum,
 * maximum and current values, so assistive technology announces the score
 * rather than describing a decorative rectangle. The numeric score is also
 * printed beside the bar, because a bar alone is not readable when the page is
 * printed in greyscale.
 *
 * When the platform's recommendation is stricter than the score band alone
 * would produce, the component says so. That happens whenever an escalation
 * rule fires - a deactivated identifier, an unresolved exclusion result, or
 * several field conflicts. Without this line a reviewer would see a low score
 * next to a Review recommendation and have no way to tell why they disagree.
 */
@Component({
  selector: 'cp-risk-meter',
  standalone: true,
  template: `
    <div class="meter tone-{{ tone }}"
         role="progressbar"
         [attr.aria-valuemin]="0"
         [attr.aria-valuemax]="100"
         [attr.aria-valuenow]="score"
         [attr.aria-label]="'Composite risk score ' + score + ' out of 100'">
      <span [style.width.%]="score"></span>
    </div>
    <p class="hint">{{ score }} out of 100 &mdash; {{ description }}</p>

    @if (escalated) {
      <p class="hint escalation" role="note">
        This record was escalated to <strong>Review</strong> by policy even though the
        score is below the review threshold. The findings listed under
        &ldquo;Why this score&rdquo; must be confirmed by a person.
      </p>
    }
  `,
  styles: [`
    .escalation {
      border-left: 4px solid var(--warn);
      background: var(--warn-tint);
      color: var(--warn);
      padding: 0.5rem 0.75rem;
      border-radius: 0 var(--radius) var(--radius) 0;
    }
  `]
})
export class RiskMeterComponent {

  /** The composite risk score, 0 to 100. */
  @Input({ required: true }) score = 0;

  /** The platform's recommendation, used to detect a policy escalation. */
  @Input() recommendation: string | null = null;

  /** @returns the tone band the score falls into */
  get tone(): string {
    return riskTone(this.score);
  }

  /** @returns true when the recommendation is stricter than the score band */
  get escalated(): boolean {
    return this.recommendation === 'REVIEW' && this.score < 40;
  }

  /** @returns a plain-language reading of the score band */
  get description(): string {
    if (this.score >= 70) { return 'high risk'; }
    if (this.score >= 40) { return 'elevated risk, review recommended'; }
    return 'low risk';
  }
}
