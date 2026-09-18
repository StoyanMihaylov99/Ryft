import { Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Burndown } from '../../core/sprint/models';

interface ChartPoint {
  x: number;
  y: number;
}

interface AxisTick {
  value: number;
  y: number;
}

interface DateTick {
  date: string;
  x: number;
}

const VIEW_BOX_WIDTH = 600;
const VIEW_BOX_HEIGHT = 300;
const MARGIN = { top: 16, right: 24, bottom: 36, left: 44 };
const PLOT_WIDTH = VIEW_BOX_WIDTH - MARGIN.left - MARGIN.right;
const PLOT_HEIGHT = VIEW_BOX_HEIGHT - MARGIN.top - MARGIN.bottom;

@Component({
  imports: [DatePipe],
  selector: 'app-burndown-chart',
  templateUrl: './burndown-chart.html',
  styleUrl: './burndown-chart.css',
})
export class BurndownChart {
  readonly burndown = input.required<Burndown>();

  readonly viewBoxWidth = VIEW_BOX_WIDTH;
  readonly viewBoxHeight = VIEW_BOX_HEIGHT;
  readonly margin = MARGIN;
  readonly plotBottom = VIEW_BOX_HEIGHT - MARGIN.bottom;

  /** The ideal line always spans the full sprint, so its length (minus one) is the x-axis's day span. */
  private readonly totalDays = computed(() => Math.max(this.burndown().idealBurndown.length - 1, 1));

  readonly idealPoints = computed<ChartPoint[]>(() =>
    this.burndown().idealBurndown.map((point, index) => this.toChartPoint(index, point.remainingPoints)),
  );

  readonly actualPoints = computed<ChartPoint[]>(() =>
    this.burndown().actualBurndown.map((point, index) => this.toChartPoint(index, point.remainingPoints)),
  );

  readonly idealPolylinePoints = computed(() => this.toPolylineAttr(this.idealPoints()));
  readonly actualPolylinePoints = computed(() => this.toPolylineAttr(this.actualPoints()));

  /** A single-point polyline renders nothing, so a sprint that just started (one data point) needs a dot instead. */
  readonly actualIsSinglePoint = computed(() => this.actualPoints().length === 1);
  readonly actualSinglePoint = computed<ChartPoint | null>(() => this.actualPoints()[0] ?? null);

  readonly yTicks = computed<AxisTick[]>(() => {
    const committed = this.burndown().committedPoints;
    if (committed === 0) {
      return [{ value: 0, y: this.yScale(0, 0) }];
    }
    const half = Math.round(committed / 2);
    return [
      { value: committed, y: this.yScale(committed, committed) },
      { value: half, y: this.yScale(half, committed) },
      { value: 0, y: this.yScale(0, committed) },
    ];
  });

  readonly xTicks = computed<DateTick[]>(() => {
    const ideal = this.burndown().idealBurndown;
    if (ideal.length === 0) {
      return [];
    }
    const lastIndex = ideal.length - 1;
    const midIndex = Math.floor(lastIndex / 2);
    const indices = midIndex === 0 || midIndex === lastIndex ? [0, lastIndex] : [0, midIndex, lastIndex];
    return [...new Set(indices)].map((index) => ({ date: ideal[index].date, x: this.xScale(index) }));
  });

  private toChartPoint(dayIndex: number, remainingPoints: number): ChartPoint {
    return { x: this.xScale(dayIndex), y: this.yScale(remainingPoints, this.burndown().committedPoints) };
  }

  private xScale(dayIndex: number): number {
    return MARGIN.left + (dayIndex / this.totalDays()) * PLOT_WIDTH;
  }

  private yScale(remainingPoints: number, committedPoints: number): number {
    if (committedPoints === 0) {
      return MARGIN.top + PLOT_HEIGHT;
    }
    return MARGIN.top + (1 - remainingPoints / committedPoints) * PLOT_HEIGHT;
  }

  private toPolylineAttr(points: ChartPoint[]): string {
    return points.map((point) => `${point.x},${point.y}`).join(' ');
  }
}
