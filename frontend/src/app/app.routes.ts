import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { Login } from './pages/login/login';
import { Oauth2Callback } from './pages/oauth2-callback/oauth2-callback';
import { Workspace } from './pages/workspace/workspace';
import { Project } from './pages/project/project';
import { Board } from './pages/board/board';

export const routes: Routes = [
  { path: 'login', component: Login },
  // Not the backend's own /login/oauth2/code/{provider} callback (Spring Security handles that
  // server-side) — this is where the backend redirects the browser back to afterwards.
  { path: 'oauth2/callback', component: Oauth2Callback },
  { path: 'workspace', component: Workspace, canActivate: [authGuard] },
  { path: 'projects/:projectKey', component: Project, canActivate: [authGuard] },
  { path: 'projects/:projectKey/board', component: Board, canActivate: [authGuard] },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
];
