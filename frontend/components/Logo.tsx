import Image from "next/image";
import Link from "next/link";
import { SrOnly } from "./Bi";

// 로고 마크 + "blog" 글자 로고. 크기는 CSS(.lock)가 화면 폭에 맞춰 정한다.
export default function Logo() {
  return (
    <Link href="/" className="lock">
      <Image className="lg" src="/logo-mark.png" alt="" width={640} height={558} priority />
      <Image className="wm" src="/logo-blog.png" alt="" width={536} height={232} priority />
      <SrOnly k="home" />
    </Link>
  );
}
