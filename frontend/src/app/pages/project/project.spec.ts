import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Project } from './project';

describe('Project', () => {
  let fixture: ComponentFixture<Project>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Project],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ projectKey: 'TRK' }) } },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
  });

  it('redirects to the project board, replacing the URL', () => {
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(Project);

    expect(navigateSpy).toHaveBeenCalledWith(['/projects', 'TRK', 'board'], { replaceUrl: true });
  });
});
