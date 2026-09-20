import { Component, computed, input, output } from '@angular/core';
import { Label } from '../../core/issue/models';
import { contrastTextColor } from '../color-contrast';

/**
 * A colored chip for a Label. Read-only (for badge use, e.g. on a card) unless `selectable` is
 * set, in which case it renders as a real toggle `<button>` with `aria-pressed` — used by the
 * label-attachment UI on the create form and the issue detail panel. Selected state is never
 * conveyed by color alone (the label's own color is arbitrary and may already be low-contrast):
 * a checkmark and a fixed-color outline both accompany it.
 */
@Component({
  selector: 'app-label-chip',
  templateUrl: './label-chip.html',
  styleUrl: './label-chip.css',
})
export class LabelChip {
  readonly label = input.required<Label>();
  readonly selectable = input(false);
  readonly selected = input(false);
  readonly disabled = input(false);
  readonly toggled = output<void>();

  readonly textColor = computed(() => contrastTextColor(this.label().color));
}
