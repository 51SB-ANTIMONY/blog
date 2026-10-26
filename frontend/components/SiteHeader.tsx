import Link from "next/link";
import { CATEGORIES } from "@/lib/categories";
import { ACCOUNTS_URL, LOGIN_PATH, articlesHref } from "@/lib/links";
import type { Me } from "@/lib/types";
import { Bi, SrOnly, Tx } from "./Bi";
import Logo from "./Logo";
import LogoutButton from "./LogoutButton";
import { LangToggle, ThemeToggle } from "./PrefToggles";
import { PencilIcon, SearchIcon } from "./icons";

// 지금 보고 있는 메뉴 (all: 전체 기록, 카테고리 slug, search: 검색)
export type MenuKey = "all" | "news" | "prayer" | "daily" | "study" | "search";

const MENU = [
  { key: "all", href: articlesHref(), label: <Tx k="all" /> },
  ...CATEGORIES.map((c) => ({ key: c.slug, href: articlesHref({ category: c.slug }), label: <Bi ko={c.ko} en={c.en} /> })),
];

function MenuLinks({ current }: { current?: MenuKey }) {
  return MENU.map((m) => (
    <Link key={m.key} href={m.href} className="mi" aria-current={m.key === current ? "page" : undefined}>
      {m.label}
    </Link>
  ));
}

function WriteLink({ className }: { className: string }) {
  return (
    <Link href="/write" className={className}>
      <PencilIcon />
      <span className="wtxt">
        <Tx k="write" />
      </span>
    </Link>
  );
}

function SearchLink({ current, className }: { current?: MenuKey; className: string }) {
  return (
    <Link href="/articles/search" className={className} aria-current={current === "search" ? "page" : undefined}>
      <SearchIcon />
      <SrOnly k="search" />
    </Link>
  );
}

// 상단바. 넓은 화면은 한 줄, 좁은 화면은 메뉴가 둘째 줄로 내려간다 (.hdr-dsk, .hdr-mob)
export default function SiteHeader({ me, current }: { me: Me | null; current?: MenuKey }) {
  return (
    <header className="top">
      <div className="wrap bar">
        <Logo />
        <nav className="menu hdr-dsk" aria-labelledby="menu-h">
          <span id="menu-h" className="sr-only">
            <Tx k="mainMenu" />
          </span>
          <MenuLinks current={current} />
        </nav>
        <div className="right">
          {me && <WriteLink className="wbtn hdr-dsk" />}
          <SearchLink current={current} className="ib hdr-dsk" />
          <LangToggle />
          <ThemeToggle />
          {me ? (
            <span className="me">
              {/* 닉네임을 누르면 accounts(accounts.antimony.kr)로 */}
              <a href={ACCOUNTS_URL} className="me-name" title={me.nickname}>
                <Bi ko={`${me.nickname}님`} en={`Hi, ${me.nickname}`} />
              </a>
              <LogoutButton />
            </span>
          ) : (
            <a href={LOGIN_PATH} className="pillbtn">
              <Tx k="login" />
            </a>
          )}
        </div>
      </div>
      <nav className="wrap menu2 hdr-mob" aria-labelledby="menu2-h">
        <span id="menu2-h" className="sr-only">
          <Tx k="mainMenu" />
        </span>
        <MenuLinks current={current} />
        <span className="menu2-tools">
          {me && <WriteLink className="wbtn" />}
          <SearchLink current={current} className="ib" />
        </span>
      </nav>
    </header>
  );
}
