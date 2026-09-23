import { Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Velocity } from '../../core/sprint/models';

interface AxisTick {
  value: number;
  y: number;
}

interface Bar {
  path: string;
  value: number;
  labelX: number;
  labelY: number;
}

interface BarGroup {
  sprintId: string;
  sprintName: string;
  label: string;
  completedAt: string;
  committed: Bar;
  completed: Bar;
  labelX: number;
}

const VIEW_BOX_WIDTH = 600;
const VIEW_BOX_HEIGHT = 300;
const MARGIN = { top: 16, right: 24, bottom: 36, left: 44 };
const PLOT_WIDTH = VIEW_BOX_WIDTH - MARGIN.left - MARGIN.right;
const PLOT_HEIGHT = VIEW_BOX_HEIGHT - MARGIN.top - MARGIN.bottom;

/** Bars stay narrow and capped so a pair never fills its whole category slot. */
const MAX_BAR_WIDTH = 24;
const BAR_GAP = 4;
const CORNER_RADIUS = 4;

/** A zero-point bar still renders this tall so "no data" reads as a real value, not a gap. */
const MIN_BAR_HEIGHT = 2;

/** Vertical gap between a value label's baseline and the bar's top edge. */
const VALUE_LABEL_CLEARANCE = 4;

/** Conservative average glyph width for the 10px axis-label font, used to fit sprint names to their slot. */
const AXIS_LABEL_FONT_SIZE = 10;
const AVG_CHAR_WIDTH = AXIS_LABEL_FONT_SIZE * 0.6;
const LABEL_HORIZONTAL_PADDING = 8;

@Component({
  imports: [DatePipe],
  selector: 'app-velocity-chart',
  templateUrl: './velocity-chart.html',
  styleUrl: './velocity-chart.css',
})
export class VelocityChart {
  readonly velocity = input.required<Velocity>();

  readonly isEmpty = computed(() => this.velocity().sprints.length === 0);

  readonly viewBoxWidth = VIEW_BOX_WIDTH;
  readonly viewBoxHeight = VIEW_BOX_HEIGHT;
  readonly margin = MARGIN;
  readonly plotBottom = VIEW_BOX_HEIGHT - MARGIN.bottom;

  private readonly maxPoints = computed(() => {
    const sprints = this.velocity().sprints;
    const max = sprints.reduce(
      (highest, sprint) => Math.max(highest, sprint.committedPoints, sprint.completedPoints),
      0,
    );
    return max;
  });

  readonly yTicks = computed<AxisTick[]>(() => {
    const max = this.maxPoints();
    if (max === 0) {
      return [{ value: 0, y: this.yScale(0) }];
    }
    const half = Math.round(max / 2);
    return [
      { value: max, y: this.yScale(max) },
      { value: half, y: this.yScale(half) },
      { value: 0, y: this.yScale(0) },
    ];
  });

  readonly barGroups = computed<BarGroup[]>(() => {
    const sprints = this.velocity().sprints;
    const bandWidth = PLOT_WIDTH / Math.max(sprints.length, 1);
    const barWidth = Math.max(Math.min(MAX_BAR_WIDTH, (bandWidth - BAR_GAP) / 2), 1);
    const pairWidth = barWidth * 2 + BAR_GAP;

    return sprints.map((sprint, index) => {
      const groupStart = MARGIN.left + bandWidth * index + (bandWidth - pairWidth) / 2;
      const committedX = groupStart;
      const completedX = groupStart + barWidth + BAR_GAP;

      return {
        sprintId: sprint.sprintId,
        sprintName: sprint.sprintName,
        label: this.truncateLabel(sprint.sprintName, bandWidth),
        completedAt: sprint.completedAt,
        committed: this.buildBar(committedX, barWidth, sprint.committedPoints),
        completed: this.buildBar(completedX, barWidth, sprint.completedPoints),
        labelX: MARGIN.left + bandWidth * (index + 0.5),
      };
    });
  });

  private yScale(points: number): number {
    const max = this.maxPoints();
    if (max === 0) {
      return this.plotBottom;
    }
    return MARGIN.top + (1 - points / max) * PLOT_HEIGHT;
  }

  /** A bar grown from the baseline with rounded corners at the data (top) end, square at the baseline. */
  private buildBar(x: number, width: number, points: number): Bar {
    const rawY = this.yScale(points);
    const height = Math.max(this.plotBottom - rawY, MIN_BAR_HEIGHT);
    const y = this.plotBottom - height;
    const radius = Math.min(CORNER_RADIUS, width / 2, height);

    const path = [
      `M${x},${this.plotBottom}`,
      `L${x},${y + radius}`,
      `Q${x},${y} ${x + radius},${y}`,
      `L${x + width - radius},${y}`,
      `Q${x + width},${y} ${x + width},${y + radius}`,
      `L${x + width},${this.plotBottom}`,
      'Z',
    ].join(' ');

    return {
      path,
      value: points,
      labelX: x + width / 2,
      labelY: Math.max(y - VALUE_LABEL_CLEARANCE, VALUE_LABEL_CLEARANCE),
    };
  }

  /** Fits a sprint name to its slot width, falling back to an ellipsis; the full name still reaches an SVG <title>. */
  private truncateLabel(name: string, slotWidth: number): string {
    const maxWidth = Math.max(slotWidth - LABEL_HORIZONTAL_PADDING, 0);
    const maxChars = Math.max(Math.floor(maxWidth / AVG_CHAR_WIDTH), 1);

    if (name.length <= maxChars) {
      return name;
    }
    if (maxChars === 1) {
      return '…';
    }
    return `${name.slice(0, maxChars - 1).trimEnd()}…`;
  }
}
