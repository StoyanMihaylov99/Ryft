import { Component, input } from '@angular/core';
import { IssueType } from '../../core/issue/models';

/** Icon + colored label for an issue's type, shared by the issue card and the detail panel header. */
@Component({
  selector: 'app-issue-type-badge',
  templateUrl: './issue-type-badge.html',
  styleUrl: './issue-type-badge.css',
})
export class IssueTypeBadge {
  readonly type = input.required<IssueType>();
}
