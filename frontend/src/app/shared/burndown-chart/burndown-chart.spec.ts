import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Burndown } from '../../core/sprint/models';
import { BurndownChart } from './burndown-chart';

function burndown(overrides: Partial<Burndown> = {}): Burndown {
  return {
    sprintId: 's1',
    sprintName: 'Sprint 1',
    startDate: '2026-01-01',
    endDate: '2026-01-14',
    committedPoints: 20,
    idealBurndown: [
      { date: '2026-01-01', remainingPoints: 20 },
      { date: '2026-01-08', remainingPoints: 10 },
      { date: '2026-01-14', remainingPoints: 0 },
    ],
    actualBurndown: [
      { date: '2026-01-01', remainingPoints: 20 },
      { date: '2026-01-08', remainingPoints: 15 },
    ],
    ...overrides,
  };
}

describe('BurndownChart', () => {
  let fixture: ComponentFixture<BurndownChart>;

  function render(value: Burndown): void {
    fixture = TestBed.createComponent(BurndownChart);
    fixture.componentRef.setInput('burndown', value);
    fixture.detectChanges();
  }

  it('renders the ideal and actual polylines with one point per data entry', () => {
    const value = burndown();
    render(value);

    const idealLine = fixture.debugElement.query(By.css('.ideal-line'));
    const actualLine = fixture.debugElement.query(By.css('.actual-line'));

    const idealPoints = idealLine.nativeElement.getAttribute('points').trim().split(' ');
    const actualPoints = actualLine.nativeElement.getAttribute('points').trim().split(' ');

    expect(idealPoints).toHaveLength(value.idealBurndown.length);
    expect(actualPoints).toHaveLength(value.actualBurndown.length);
  });

  it('renders a single dot instead of a polyline when a sprint has just started (one actual point)', () => {
    render(burndown({ actualBurndown: [{ date: '2026-01-01', remainingPoints: 20 }] }));

    expect(fixture.debugElement.query(By.css('.actual-line'))).toBeNull();
    expect(fixture.debugElement.query(By.css('.actual-point'))).not.toBeNull();
  });

  it('renders sensibly without dividing by zero when committedPoints is 0', () => {
    render(
      burndown({
        committedPoints: 0,
        idealBurndown: [
          { date: '2026-01-01', remainingPoints: 0 },
          { date: '2026-01-14', remainingPoints: 0 },
        ],
        actualBurndown: [{ date: '2026-01-01', remainingPoints: 0 }],
      }),
    );

    const idealLine = fixture.debugElement.query(By.css('.ideal-line'));
    expect(idealLine.nativeElement.getAttribute('points')).not.toContain('NaN');
    expect(fixture.debugElement.query(By.css('.actual-point')).nativeElement.getAttribute('cy')).not.toBe('NaN');
  });
});
