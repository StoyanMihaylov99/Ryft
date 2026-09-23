import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Velocity } from '../../core/sprint/models';
import { VelocityChart } from './velocity-chart';

function velocity(overrides: Partial<Velocity> = {}): Velocity {
  return {
    projectId: 'p1',
    projectKey: 'TRK',
    sprints: [
      {
        sprintId: 's1',
        sprintName: 'Sprint 1',
        committedPoints: 10,
        completedPoints: 8,
        completedAt: '2026-01-15T00:00:00Z',
      },
      {
        sprintId: 's2',
        sprintName: 'Sprint 2',
        committedPoints: 12,
        completedPoints: 12,
        completedAt: '2026-01-29T00:00:00Z',
      },
    ],
    ...overrides,
  };
}

/** Extracts every y-coordinate from an SVG path's "M"/"L"/"Q" command string, in order. */
function extractPathYValues(d: string): number[] {
  return d
    .split(' ')
    .filter((token) => token.includes(','))
    .map((token) => Number(token.replace(/^[A-Z]/, '').split(',')[1]));
}

describe('VelocityChart', () => {
  let fixture: ComponentFixture<VelocityChart>;

  function render(value: Velocity): void {
    fixture = TestBed.createComponent(VelocityChart);
    fixture.componentRef.setInput('velocity', value);
    fixture.detectChanges();
  }

  it('renders one committed/completed bar pair per sprint', () => {
    const value = velocity();
    render(value);

    const committedBars = fixture.debugElement.queryAll(By.css('.committed-bar'));
    const completedBars = fixture.debugElement.queryAll(By.css('.completed-bar'));

    expect(committedBars).toHaveLength(value.sprints.length);
    expect(completedBars).toHaveLength(value.sprints.length);
  });

  it('scales bar heights from a shared zero baseline without NaN coordinates', () => {
    render(velocity());

    const paths = fixture.debugElement.queryAll(By.css('.committed-bar, .completed-bar'));
    for (const path of paths) {
      const d = path.nativeElement.getAttribute('d') as string;
      expect(d).not.toContain('NaN');
      expect(d).toContain(`,${fixture.componentInstance.plotBottom}`);
    }
  });

  it('the tallest bar corresponds to the sprint with the highest points value', () => {
    render(
      velocity({
        sprints: [
          {
            sprintId: 's1',
            sprintName: 'Sprint 1',
            committedPoints: 5,
            completedPoints: 5,
            completedAt: '2026-01-15T00:00:00Z',
          },
          {
            sprintId: 's2',
            sprintName: 'Sprint 2',
            committedPoints: 20,
            completedPoints: 18,
            completedAt: '2026-01-29T00:00:00Z',
          },
        ],
      }),
    );

    const groups = fixture.componentInstance.barGroups();
    expect(groups[1].committed.value).toBeGreaterThan(groups[0].committed.value);
    expect(groups[1].committed.labelY).toBeLessThan(groups[0].committed.labelY);
  });

  it('renders sensibly without dividing by zero when every sprint has 0 points', () => {
    render(
      velocity({
        sprints: [
          {
            sprintId: 's1',
            sprintName: 'Sprint 1',
            committedPoints: 0,
            completedPoints: 0,
            completedAt: '2026-01-15T00:00:00Z',
          },
        ],
      }),
    );

    const paths = fixture.debugElement.queryAll(By.css('.committed-bar, .completed-bar'));
    for (const path of paths) {
      expect(path.nativeElement.getAttribute('d')).not.toContain('NaN');
    }
  });

  it('renders a minimum-height stub bar for a sprint with 0 committed and 0 completed points', () => {
    render(
      velocity({
        sprints: [
          {
            sprintId: 's1',
            sprintName: 'Sprint 1',
            committedPoints: 0,
            completedPoints: 0,
            completedAt: '2026-01-15T00:00:00Z',
          },
        ],
      }),
    );

    const { plotBottom } = fixture.componentInstance;
    const paths = fixture.debugElement.queryAll(By.css('.committed-bar, .completed-bar'));
    for (const path of paths) {
      const d = path.nativeElement.getAttribute('d') as string;
      const yValues = extractPathYValues(d);
      expect(Math.min(...yValues)).toBeLessThan(plotBottom);
    }
  });

  it('renders numeric committed/completed value labels above each bar', () => {
    render(velocity());

    const groups = fixture.componentInstance.barGroups();
    const valueLabels = fixture.debugElement.queryAll(By.css('.value-label'));

    expect(valueLabels).toHaveLength(groups.length * 2);
    expect(valueLabels[0].nativeElement.textContent).toBe('10');
    expect(valueLabels[1].nativeElement.textContent).toBe('8');
    for (const [index, group] of groups.entries()) {
      expect(valueLabels[index * 2].nativeElement.getAttribute('y')).toBe(String(group.committed.labelY));
      expect(valueLabels[index * 2 + 1].nativeElement.getAttribute('y')).toBe(String(group.completed.labelY));
    }
  });

  it('truncates a sprint name that overflows its slot at the default 5-sprint window and keeps the full name on an SVG title', () => {
    const longName = 'Sprint 24 - Q3 Growth Initiative';
    render(
      velocity({
        sprints: [
          {
            sprintId: 's1',
            sprintName: longName,
            committedPoints: 10,
            completedPoints: 8,
            completedAt: '2026-01-15T00:00:00Z',
          },
          ...['s2', 's3', 's4', 's5'].map((sprintId) => ({
            sprintId,
            sprintName: sprintId,
            committedPoints: 10,
            completedPoints: 8,
            completedAt: '2026-01-15T00:00:00Z',
          })),
        ],
      }),
    );

    const group = fixture.componentInstance.barGroups()[0];
    expect(group.label.length).toBeLessThan(longName.length);
    expect(group.label.endsWith('…')).toBe(true);
    expect(longName.startsWith(group.label.slice(0, -1))).toBe(true);

    const labelText = fixture.debugElement.query(By.css('text.sprint-label'));
    expect(labelText.nativeElement.textContent).toContain(group.label);
    const title = labelText.query(By.css('title'));
    expect(title.nativeElement.textContent).toBe(longName);
  });

  it('does not truncate a sprint name that already fits its slot', () => {
    render(velocity());

    const groups = fixture.componentInstance.barGroups();
    expect(groups[0].label).toBe('Sprint 1');
    expect(groups[1].label).toBe('Sprint 2');
  });

  it('renders an accessible table fallback with a row per sprint', () => {
    const value = velocity();
    render(value);

    const rows = fixture.debugElement.queryAll(By.css('table tbody tr'));
    expect(rows).toHaveLength(value.sprints.length);
    expect(rows[0].nativeElement.textContent).toContain('Sprint 1');
    expect(rows[0].nativeElement.textContent).toContain('10');
    expect(rows[0].nativeElement.textContent).toContain('8');
  });

  it('shows an empty-state message instead of a chart when there are no completed sprints', () => {
    render(velocity({ sprints: [] }));

    expect(fixture.debugElement.query(By.css('svg'))).toBeNull();
    expect(fixture.debugElement.query(By.css('.empty-state'))).not.toBeNull();
  });
});
