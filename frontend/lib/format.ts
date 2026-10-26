// 날짜와 시각은 모두 한국 시간(Asia/Seoul) 기준으로 보여 주고 계산한다.
// (서버가 UTC로 돌아도 같은 결과가 나오도록 시간대를 직접 정한다)
const KST = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

export type KstTime = { year: string; month: string; day: string; hour: string; minute: string };

export function kst(value: string | Date = new Date()): KstTime {
  const parts = Object.fromEntries(KST.formatToParts(new Date(value)).map((p) => [p.type, p.value]));
  return { year: parts.year, month: parts.month, day: parts.day, hour: parts.hour, minute: parts.minute };
}

// 2026.09.28
export function formatDate(value: string): string {
  const t = kst(value);
  return `${t.year}.${t.month}.${t.day}`;
}

// 09.28
export function formatShortDate(value: string): string {
  const t = kst(value);
  return `${t.month}.${t.day}`;
}

// 2026.09.28 14:30
export function formatStamp(value: string): string {
  const t = kst(value);
  return `${t.year}.${t.month}.${t.day} ${t.hour}:${t.minute}`;
}

// 한국 시간으로 올해 (예: "2026")
export function currentYear(): string {
  return kst().year;
}

export function yearOf(value: string): string {
  return kst(value).year;
}

const MONTHS_EN = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// 월 이름: ko "10월", en "Oct"
export function monthName(value: string): { ko: string; en: string } {
  const month = kst(value).month;
  return { ko: `${month}월`, en: MONTHS_EN[Number(month) - 1] };
}

// 한국 시간 날짜로 며칠 전인가 (오늘 0, 어제 1)
export function daysAgo(value: string, now: Date = new Date()): number {
  const dayNumber = (t: KstTime) => Date.UTC(Number(t.year), Number(t.month) - 1, Number(t.day)) / 86_400_000;
  return dayNumber(kst(now)) - dayNumber(kst(value));
}

// 수정한 적이 있는가 (작성과 수정 시각이 1초 넘게 다르면)
export function isEdited(createdAt: string, updatedAt: string): boolean {
  return Math.abs(new Date(updatedAt).getTime() - new Date(createdAt).getTime()) > 1000;
}
