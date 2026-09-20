import { Component, computed, inject, input } from '@angular/core';
import { ActivatedRoute, IsActiveMatchOptions, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ProjectRole } from '../../core/project/models';
import {
  canManageIssues as canManageIssuesPermission,
  canManageProjectSettings,
} from '../../core/project/permissions';
import { ThemeService } from '../../core/theme/theme.service';

@Component({
  imports: [RouterLink, RouterLinkActive],
  selector: 'app-sidebar',
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
})
export class Sidebar {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly themeService = inject(ThemeService);

  readonly currentUser = this.authService.currentUser;

  /** `null` on pages with no project in scope (`/projects`, `/workspace`). A one-time snapshot read
   *  is safe here: navigating between the 4 project-scoped routes always maps to a distinct `Route`
   *  (see `app.routes.ts`), so Angular's default reuse strategy destroys and recreates `Sidebar`
   *  every time — unlike a query-param-only navigation, which reuses the instance (see `board.ts`'s
   *  `panelParam` for the case where a snapshot read would go stale). */
  readonly projectKey = this.route.snapshot.paramMap.get('projectKey');

  /** Populated by the including page (already fetched there) rather than self-fetched here, to avoid
   *  a 5th redundant `GET /projects/{key}` call per page load. */
  readonly myRole = input<ProjectRole | null>(null);
  readonly canManageWorkflow = computed(() => canManageProjectSettings(this.myRole()));
  readonly canManageIssues = computed(() => canManageIssuesPermission(this.myRole()));

  /** `queryParams: 'exact'` matters even for Board's own link: the default `'subset'` match treats a
   *  link with no `[queryParams]` binding (an implicit `{}`) as a subset of any actual query string,
   *  so Board's item would stay highlighted even while `?panel=workflow` is open. */
  readonly exactMatch: IsActiveMatchOptions = {
    paths: 'exact',
    queryParams: 'exact',
    matrixParams: 'ignored',
    fragment: 'ignored',
  };

  initials(name: string): string {
    const parts = name.trim().split(/\s+/);
    const first = parts[0]?.[0] ?? '';
    const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
    return (first + last).toUpperCase();
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
