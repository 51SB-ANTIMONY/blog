import Image from "next/image";
import { currentYear } from "@/lib/format";
import { Tx } from "./Bi";

// 푸터 배경은 테마와 반대 색이다. 라이트 테마(어두운 푸터)는 밝은 로고, 다크 테마(밝은 푸터)는 어두운 로고.
export default function SiteFooter() {
  return (
    <footer className="foot">
      <div className="wrap">
        <div className="fl" role="img" aria-label="ANTIMONY">
          <Image className="a hide-in-dark" src="/logo-mark-light.png" alt="" width={640} height={558} />
          <Image className="a hide-in-light" src="/logo-mark-dark.png" alt="" width={640} height={558} />
          <Image className="b hide-in-dark" src="/logo-wordmark-light.png" alt="" width={1200} height={136} />
          <Image className="b hide-in-light" src="/logo-wordmark-dark.png" alt="" width={1200} height={136} />
        </div>
        <p>
          <Tx k="contact" /> <a href="mailto:me@antimony.kr">me@antimony.kr</a>
        </p>
        <span className="cp">© {currentYear()} ANTIMONY</span>
      </div>
    </footer>
  );
}
