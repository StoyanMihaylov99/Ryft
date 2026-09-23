/** First + last initial of a display name, e.g. "Jane Doe" -> "JD", "Cher" -> "C". Shared by any
 *  avatar-style badge in the app (sidebar's user avatar, the board Filters panel's per-result
 *  assignee indicator) so they render the same convention. */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
  return (first + last).toUpperCase();
}
