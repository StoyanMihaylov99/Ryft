import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { Project } from '../../core/project/models';
import { ProjectService } from '../../core/project/project.service';

@Component({
  imports: [ReactiveFormsModule, RouterLink, Sidebar],
  selector: 'app-project-list',
  templateUrl: './project-list.html',
  styleUrl: './project-list.css',
})
export class ProjectList {
  private readonly formBuilder = inject(FormBuilder);
  private readonly projectService = inject(ProjectService);
  private readonly router = inject(Router);

  readonly projects = signal<Project[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly creating = signal(false);
  readonly showCreateForm = signal(false);

  readonly createForm = this.formBuilder.nonNullable.group({
    key: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(10), Validators.pattern(/^[A-Z][A-Z0-9]*$/)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: [''],
  });

  constructor() {
    this.loadProjects();
    this.createForm.controls.key.valueChanges.subscribe((value) => {
      const upper = value.toUpperCase();
      if (upper !== value) {
        this.createForm.controls.key.setValue(upper, { emitEvent: false });
      }
    });
  }

  loadProjects(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.projectService.list().subscribe({
      next: (projects) => {
        this.projects.set(projects);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load projects.');
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
    this.errorMessage.set(null);
    const { key, name, description } = this.createForm.getRawValue();
    this.projectService.create({ key, name, description: description || null }).subscribe({
      next: (project) => this.router.navigateByUrl(`/projects/${project.key}/board`),
      error: (error: HttpErrorResponse) => {
        this.creating.set(false);
        this.errorMessage.set(
          error.status === 409 ? 'A project with that key already exists.' : 'Failed to create the project.',
        );
      },
    });
  }
}
