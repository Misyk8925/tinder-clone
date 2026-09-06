/** Phrases that preview mode and the keyword classifier treat as blocked. */
export function previewTextBlocked(text: string | null | undefined): boolean {
  const value = (text ?? '').toLowerCase();
  return (
    value.includes('hate all') ||
    value.includes('should die') ||
    value.includes('nazi') ||
    value.includes('kill yourself') ||
    value.includes('kys') ||
    value.includes('worthless trash') ||
    value.includes('i will kill you') ||
    value.includes("i'll kill you")
  );
}
