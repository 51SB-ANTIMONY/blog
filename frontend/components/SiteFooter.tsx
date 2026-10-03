import Image from "next/image";

export default function SiteFooter() {
  return (
    <footer className="footer">
      <div className="footer-brand">
        {/* 라이트 테마의 푸터는 어두운 배경이라 밝은 글자 로고, 다크 테마는 기본 글자 로고 (프로토타입 그대로) */}
        <Image className="footer-word on-light" src="/logo-wordmark-light.png" alt="ANTIMONY" width={1200} height={136} />
        <Image className="footer-word on-dark" src="/logo-wordmark.png" alt="ANTIMONY" width={1200} height={136} />
        <span className="footer-copy only-desktop">© 2026 ANTIMONY. 일상 · 학습 · 시냇가에 심은 나무</span>
        <span className="footer-copy only-mobile">© 2026 ANTIMONY</span>
      </div>
      <div className="footer-links only-desktop">
        <a href="#about">소개</a>
        <a href="#rss">RSS</a>
        <a href="#contact">[연락처]</a>
      </div>
    </footer>
  );
}
