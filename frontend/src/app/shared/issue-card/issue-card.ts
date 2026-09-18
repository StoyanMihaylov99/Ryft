import { Component, input } from '@angular/core';
import { Issue } from '../../core/issue/models';
import { ComponentChip } from '../component-chip/component-chip';
import { IssueTypeBadge } from '../issue-type-badge/issue-type-badge';
import { LabelChip } from '../label-chip/label-chip';

@Component({
  imports: [IssueTypeBadge, LabelChip, ComponentChip],
  selector: 'app-issue-card',
  templateUrl: './issue-card.html',
  styleUrl: './issue-card.css',
})
export class IssueCard {
  readonly issue = input.required<Issue>();
  /** The linked Epic's title, resolved by the host page from its already-loaded issue list — null
   *  when unlinked, or when the epic isn't among the issues currently loaded there. */
  readonly epicTitle = input<string | null>(null);
}
