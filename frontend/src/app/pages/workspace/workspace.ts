import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceMember, WorkspaceRole } from '../../core/workspace/models';
import { WorkspaceService } from '../../core/workspace/workspace.service';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-workspace',
  templateUrl: './workspace.html',
  styleUrl: './workspace.css',
})
export class Workspace {
  private readonly formBuilder = inject(FormBuilder);
  private readonly workspaceService = inject(WorkspaceService);
  private readonly authService = inject(AuthService);

  readonly members = signal<WorkspaceMember[]>([]);
  readonly loading = signal(true);
  readonly notAMember = signal(false);
  readonly setupRequired = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly inviting = signal(false);
  readonly settingUp = signal(false);
  private slugManuallyEdited = false;

  readonly myRole = computed<WorkspaceRole | null>(() => {
    const currentUser = this.authService.currentUser();
    if (!currentUser) {
      return null;
    }
    return this.members().find((member) => member.userId === currentUser.id)?.role ?? null;
  });

  readonly canInvite = computed(() => this.myRole() === 'OWNER' || this.myRole() === 'ADMIN');
  readonly canChangeRoles = computed(() => this.myRole() === 'OWNER');

  readonly inviteForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    role: ['MEMBER' as WorkspaceRole, [Validators.required]],
  });

  readonly setupForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(-[a-z0-9]+)*$/)]],
  });

  constructor() {
    this.loadMembers();
    this.setupForm.controls.name.valueChanges.subscribe((name) => {
      if (!this.slugManuallyEdited) {
        this.setupForm.controls.slug.setValue(this.slugify(name), { emitEvent: false });
      }
    });
    this.setupForm.controls.slug.valueChanges.subscribe(() => {
      this.slugManuallyEdited = true;
    });
  }

  loadMembers(): void {
    this.loading.set(true);
    this.notAMember.set(false);
    this.setupRequired.set(false);
    this.errorMessage.set(null);
    this.workspaceService.listMembers().subscribe({
      next: (members) => {
        this.members.set(members);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        if (error.status === 404) {
          this.setupRequired.set(true);
        } else if (error.status === 403) {
          this.notAMember.set(true);
        } else {
          this.errorMessage.set('Failed to load workspace members.');
        }
      },
    });
  }

  submitSetup(): void {
    if (this.setupForm.invalid) {
      this.setupForm.markAllAsTouched();
      return;
    }
    this.settingUp.set(true);
    this.errorMessage.set(null);
    const { name, slug } = this.setupForm.getRawValue();
    this.workspaceService.completeSetup(name, slug).subscribe({
      next: () => {
        this.settingUp.set(false);
        this.setupRequired.set(false);
        this.loadMembers();
      },
      error: (error: HttpErrorResponse) => {
        this.settingUp.set(false);
        this.errorMessage.set(
          error.status === 409
            ? 'This workspace has already been set up.'
            : 'Failed to set up the workspace. Please try again.',
        );
      },
    });
  }

  private slugify(value: string): string {
    return value
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  submitInvite(): void {
    if (this.inviteForm.invalid) {
      this.inviteForm.markAllAsTouched();
      return;
    }
    this.inviting.set(true);
    this.errorMessage.set(null);
    const { email, role } = this.inviteForm.getRawValue();
    this.workspaceService.invite(email, role).subscribe({
      next: () => {
        this.inviting.set(false);
        this.inviteForm.reset({ email: '', role: 'MEMBER' });
        this.loadMembers();
      },
      error: (error: HttpErrorResponse) => {
        this.inviting.set(false);
        this.errorMessage.set(this.describeInviteError(error));
      },
    });
  }

  changeRole(userId: string, role: WorkspaceRole): void {
    this.errorMessage.set(null);
    this.workspaceService.changeRole(userId, role).subscribe({
      next: () => this.loadMembers(),
      error: () => this.errorMessage.set('Failed to change that member’s role.'),
    });
  }

  private describeInviteError(error: HttpErrorResponse): string {
    switch (error.status) {
      case 404:
        return 'No registered user with that email.';
      case 409:
        return 'That user is already a member of this workspace.';
      case 403:
        return 'You do not have permission to invite members.';
      default:
        return 'Failed to send the invite. Please try again.';
    }
  }
}
