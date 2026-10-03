import Image from "next/image";
import Link from "next/link";

// 로고 마크 + 글자 로고. 크기는 CSS(.logo-mark, .logo-word)가 화면 폭에 맞춰 정한다.
export default function Logo() {
  return (
    <Link href="/" aria-label="ANTIMONY 홈" className="logo">
      <Image className="logo-mark" src="/logo-mark.png" alt="" width={640} height={558} priority />
      <Image className="logo-word" src="/logo-wordmark.png" alt="" width={1200} height={136} priority />
    </Link>
  );
}
