/**
 * Chooses a legible text color for an arbitrary, user-chosen background such as a Label's hex
 * color. Assuming white or black text always works fails WCAG contrast for some hues (e.g. a
 * mid-tone yellow reads poorly with white text but fine with black) — this picks whichever gives
 * the better contrast, per the standard "relative luminance" approach recommended for this
 * decision (see WCAG 2.x's relative luminance definition: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance).
 */
export function contrastTextColor(hexColor: string): '#000000' | '#ffffff' {
  const rgb = hexToRgb(hexColor);
  if (!rgb) {
    return '#000000';
  }
  // The crossover point (sqrt(1.05 * 0.05) - 0.05) above which black text contrasts better than
  // white against a background of this luminance.
  return relativeLuminance(rgb) > 0.179 ? '#000000' : '#ffffff';
}

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const match = /^#([0-9a-f]{6})$/i.exec(hex);
  if (!match) {
    return null;
  }
  const value = parseInt(match[1], 16);
  return { r: (value >> 16) & 255, g: (value >> 8) & 255, b: value & 255 };
}

function relativeLuminance({ r, g, b }: { r: number; g: number; b: number }): number {
  const [rs, gs, bs] = [r, g, b].map((channel) => {
    const proportion = channel / 255;
    return proportion <= 0.03928 ? proportion / 12.92 : Math.pow((proportion + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}
