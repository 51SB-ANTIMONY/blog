import Link from "next/link";
import { ACCOUNTS_URL, LOGIN_PATH } from "@/lib/links";
import type { Me } from "@/lib/types";
import Logo from "./Logo";
import LogoutButton from "./LogoutButton";
import { PencilIcon, SearchIcon } from "./icons";

type Props = {
  me: Me | null;
  // home: 홈 헤더 (글쓰기 버튼이 채워진 색) / post: 글 상세 헤더 (글쓰기 버튼이 테두리만)
  variant: "home" | "post";
};

export default function SiteHeader({ me, variant }: Props) {
  return (
    <header className={variant === "post" ? "header is-post" : "header"}>
      <Logo />

      {/* PC */}
      <div className="header-right only-desktop">
        <button type="button" className="search-btn" aria-label="검색 (준비 중)" disabled>
          <SearchIcon />
        </button>
        {me ? (
          <div className="auth">
            {/* 닉네임을 누르면 accounts의 내 계정 화면 */}
            <a href={`${ACCOUNTS_URL}/account`} className="auth-name">
              <b>{me.nickname}</b>님
            </a>
            <Link href="/write" className={variant === "home" ? "pill-primary is-write" : "pill-outline"}>
              <PencilIcon />
              글쓰기
            </Link>
            <LogoutButton />
          </div>
        ) : (
          <div className="auth">
            <a href={LOGIN_PATH} className="text-link">
              로그인
            </a>
            <a href={`${ACCOUNTS_URL}/signup`} className="pill-primary is-wide">
              회원가입
            </a>
          </div>
        )}
      </div>

      {/* 모바일 */}
      <div className="mobile-icons only-mobile">
        {variant === "home" && (
          <button type="button" className="icon-btn" aria-label="검색 (준비 중)" disabled>
            <SearchIcon size={20} />
          </button>
        )}
        {me ? (
          <>
            <Link href="/write" className="icon-btn" aria-label="글쓰기">
              <PencilIcon size={20} />
            </Link>
            {variant === "home" && <LogoutButton className="text-btn is-mobile" />}
          </>
        ) : (
          <a href={LOGIN_PATH} className="pill-login-mobile">
            로그인
          </a>
        )}
      </div>
    </header>
  );
}
