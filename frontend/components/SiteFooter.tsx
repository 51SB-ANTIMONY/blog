import Image from "next/image";

export default function SiteFooter() {
  return (
    <footer className="footer">
      <div className="footer-brand">
        {/* 라이트 테마의 푸터는 어두운 배경이라 밝은 글자 로고, 다크 테마는 기본 글자 로고 (프로토타입 그대로) */}
        <Image className="footer-word on-light" src="/logo-wordmark-light.png" alt="ANTIMONY" width={1200} height={136} />
        <Image className="footer-word on-dark" src="/logo-wordmark.png" alt="ANTIMONY" width={1200} height={136} />
        <span className="footer-copy">© 2026 ANTIMONY</span>
      </div>
      <p className="footer-contact">
        문의 <a href="mailto:me@antimony.kr">me@antimony.kr</a>
      </p>
    </footer>
  );
}
