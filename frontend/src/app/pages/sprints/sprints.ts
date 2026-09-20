import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { AuthService } from '../../core/auth/auth.service';
import { ProjectRole } from '../../core/project/models';
import { canManageSprints as canManageSprintsPermission } from '../../core/project/permissions';
import { ProjectService } from '../../core/project/project.service';
import { Burndown, Sprint } from '../../core/sprint/models';
import { SprintService } from '../../core/sprint/sprint.service';
import { BurndownChart } from '../../shared/burndown-chart/burndown-chart';

@Component({
  imports: [DatePipe, ReactiveFormsModule, RouterLink, Sidebar, BurndownChart],
  selector: 'app-sprints',
  templateUrl: './sprints.html',
  styleUrl: './sprints.css',
})
export class Sprints {
  private readonly route = inject(ActivatedRoute);
  private readonly sprintService = inject(SprintService);
  private readonly projectService = inject(ProjectService);
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);

  readonly projectKey = this.route.snapshot.paramMap.get('projectKey')!;
  readonly sprints = signal<Sprint[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly showCreateForm = signal(false);
  readonly creating = signal(false);
  readonly updatingSprintId = signal<string | null>(null);

  /** Only one completed sprint's burndown is expanded at a time — keeps the lazy-load simple. */
  readonly openBurndownSprintId = signal<string | null>(null);
  readonly burndown = signal<Burndown | null>(null);
  readonly burndownLoading = signal(false);
  readonly burndownError = signal<string | null>(null);

  /** Only Owner/Admin plan, start or complete sprints. */
  readonly myRole = signal<ProjectRole | null>(null);
  readonly canManageSprints = computed(() => canManageSprintsPermission(this.myRole()));
  readonly currentUserId = computed(() => this.authService.currentUser()?.id ?? null);

  readonly createForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    goal: [''],
    startDate: [''],
    endDate: [''],
  });

  constructor() {
    this.loadSprints();
    this.loadProjectRole();
  }

  private loadProjectRole(): void {
    this.projectService.get(this.projectKey).subscribe({
      next: (project) => this.myRole.set(project.callerRole),
      // Leave myRole null on failure — canManageSprints() then stays false, the safe default.
      error: () => {},
    });
  }

  private loadSprints(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.sprintService.listForProject(this.projectKey).subscribe({
      next: (sprints) => {
        this.sprints.set(sprints);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load sprints.');
      },
    });
  }

  toggleBurndown(sprint: Sprint): void {
    if (this.openBurndownSprintId() === sprint.id) {
      this.openBurndownSprintId.set(null);
      return;
    }
    this.openBurndownSprintId.set(sprint.id);
    this.burndown.set(null);
    this.burndownError.set(null);
    this.burndownLoading.set(true);
    this.sprintService.getBurndown(sprint.id).subscribe({
      next: (burndown) => {
        this.burndownLoading.set(false);
        this.burndown.set(burndown);
      },
      error: () => {
        this.burndownLoading.set(false);
        this.burndownError.set(`Failed to load the burndown chart for ${sprint.name}.`);
      },
    });
  }

  toggleCreateForm(): void {
    this.showCreateForm.update((shown) => !shown);
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.creating.set(true);
    const { name, goal, startDate, endDate } = this.createForm.getRawValue();
    this.sprintService
      .create(this.projectKey, {
        name,
        goal: goal.trim() || null,
        startDate: startDate || null,
        endDate: endDate || null,
      })
      .subscribe({
        next: (sprint) => {
          this.creating.set(false);
          this.showCreateForm.set(false);
          this.sprints.update((sprints) => [...sprints, sprint]);
          this.createForm.reset({ name: '', goal: '', startDate: '', endDate: '' });
        },
        error: () => {
          this.creating.set(false);
          this.errorMessage.set('Failed to create the sprint.');
        },
      });
  }

  start(sprint: Sprint): void {
    this.applySprintUpdate(sprint, this.sprintService.start(sprint.id));
  }

  complete(sprint: Sprint): void {
    this.applySprintUpdate(sprint, this.sprintService.complete(sprint.id));
  }

  private applySprintUpdate(sprint: Sprint, request$: Observable<Sprint>): void {
    this.errorMessage.set(null);
    this.updatingSprintId.set(sprint.id);
    request$.subscribe({
      next: (updated) => {
        this.updatingSprintId.set(null);
        const sprints = this.sprints();
        const index = sprints.findIndex((candidate) => candidate.id === updated.id);
        if (index !== -1) {
          sprints[index] = updated;
        }
        this.sprints.set([...sprints]);
      },
      error: () => {
        this.updatingSprintId.set(null);
        this.errorMessage.set(`Failed to update ${sprint.name}. Please try again.`);
      },
    });
  }
}
