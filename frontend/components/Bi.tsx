import { TEXT, type TextKey } from "@/lib/i18n";

// 두 언어를 함께 넣고, <html lang>에 맞지 않는 쪽은 CSS(.bi)가 숨긴다.
// 서버가 언어 설정을 몰라도 처음부터 맞는 언어가 보인다.
export function Bi({ ko, en }: { ko: React.ReactNode; en: React.ReactNode }) {
  return (
    <span className="bi">
      <span lang="ko">{ko}</span>
      <span lang="en">{en}</span>
    </span>
  );
}

// lib/i18n.ts의 화면 문구
export function Tx({ k }: { k: TextKey }) {
  return <Bi ko={TEXT.ko[k]} en={TEXT.en[k]} />;
}

// 화면에는 보이지 않고 화면 낭독기만 읽는 문구 (아이콘 버튼의 이름 등)
export function SrOnly({ k }: { k: TextKey }) {
  return (
    <span className="sr-only">
      <Tx k={k} />
    </span>
  );
}
