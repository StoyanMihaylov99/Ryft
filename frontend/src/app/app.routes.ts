import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Workspace } from './pages/workspace/workspace';
import { Project } from './pages/project/project';
import { Board } from './pages/board/board';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'workspace', component: Workspace },
  { path: 'projects/:projectKey', component: Project },
  { path: 'projects/:projectKey/board', component: Board },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
];
