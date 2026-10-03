import Link from "next/link";
import { CATEGORIES } from "@/lib/categories";
import { ACCOUNTS_URL, LOGIN_PATH } from "@/lib/links";
import type { Me } from "@/lib/types";
import Logo from "./Logo";
import LogoutButton from "./LogoutButton";
import { PencilIcon, SearchIcon, UserIcon } from "./icons";

type Props = {
  me: Me | null;
  // home: 카테고리 탭과 검색이 있는 홈 헤더 / post: 글 상세 헤더
  variant: "home" | "post";
  // 홈에서 고른 카테고리 (전체면 undefined)
  currentSlug?: string;
};

function tabHref(slug?: string) {
  return slug ? `/?category=${slug}` : "/";
}

export function CategoryTabs({ currentSlug, className }: { currentSlug?: string; className: string }) {
  const tabs = [{ slug: undefined, label: "전체" }, ...CATEGORIES.map((c) => ({ slug: c.slug, label: c.label }))];
  return (
    <nav aria-label="주 메뉴" className={className}>
      {tabs.map((tab) => (
        <Link key={tab.label} href={tabHref(tab.slug)} className="tab" aria-current={tab.slug === currentSlug ? "page" : undefined}>
          {tab.label}
        </Link>
      ))}
    </nav>
  );
}

export default function SiteHeader({ me, variant, currentSlug }: Props) {
  return (
    <header className={variant === "post" ? "header is-post" : "header"}>
      <Logo />

      {variant === "home" && <CategoryTabs currentSlug={currentSlug} className="tabs only-desktop" />}

      {/* PC */}
      <div className="header-right only-desktop">
        <button type="button" className="search-btn" aria-label="검색 (준비 중)" disabled>
          <SearchIcon />
        </button>
        {me ? (
          <div className="auth">
            <span className="auth-name">
              <b>{me.nickname}</b>님
            </span>
            <Link href="/write" className="pill-outline">
              <PencilIcon />
              글쓰기
            </Link>
            <a href={`${ACCOUNTS_URL}/account`} className="pill-primary">
              마이페이지
            </a>
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
            <a href={`${ACCOUNTS_URL}/account`} className="icon-btn" aria-label={`${me.nickname}님 마이페이지`}>
              <UserIcon />
            </a>
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
