import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  imports: [],
  selector: 'app-project',
  template: '',
})
export class Project {
  constructor() {
    const route = inject(ActivatedRoute);
    const router = inject(Router);
    const projectKey = route.snapshot.paramMap.get('projectKey');
    router.navigate(['/projects', projectKey, 'board'], { replaceUrl: true });
  }
}
