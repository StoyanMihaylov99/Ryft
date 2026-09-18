import { Component, input, output } from '@angular/core';
import { ProjectComponent } from '../../core/issue/models';

/**
 * A plain, neutral-styled chip for a project Component. The backend gives components no per-item
 * color (unlike Label), so this always uses the app's fixed badge tokens. Read-only unless
 * `selectable`, mirroring `LabelChip`'s toggle-button behavior.
 */
@Component({
  selector: 'app-component-chip',
  templateUrl: './component-chip.html',
  styleUrl: './component-chip.css',
})
export class ComponentChip {
  readonly component = input.required<ProjectComponent>();
  readonly selectable = input(false);
  readonly selected = input(false);
  readonly disabled = input(false);
  readonly toggled = output<void>();
}
