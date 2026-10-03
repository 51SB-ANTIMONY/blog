// 2026-09-28T14:30:00.123 → 2026.09.28
export function formatDate(value: string): string {
  return value.slice(0, 10).replaceAll("-", ".");
}

// 수정한 적이 있는가 (작성과 수정 시각이 1초 넘게 다르면)
export function isEdited(createdAt: string, updatedAt: string): boolean {
  return Math.abs(new Date(updatedAt).getTime() - new Date(createdAt).getTime()) > 1000;
}
