import { Component, input } from '@angular/core';
import { Issue } from '../../core/issue/models';

@Component({
  selector: 'app-issue-card',
  templateUrl: './issue-card.html',
  styleUrl: './issue-card.css',
})
export class IssueCard {
  readonly issue = input.required<Issue>();
}
