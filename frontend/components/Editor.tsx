"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiError, startLogin } from "@/lib/api";
import { CATEGORIES } from "@/lib/categories";
import type { Article, Category } from "@/lib/types";
import DeleteConfirm from "./DeleteConfirm";
import Logo from "./Logo";
import { BackIcon, CodeIcon, LinkIcon, ListIcon, PhotoIcon, QuoteIcon } from "./icons";

const VISIBILITY = [
  { key: "public", label: "공개", desc: "누구나 볼 수 있어요" },
  { key: "members", label: "회원에게만 공개", desc: "로그인한 회원만 볼 수 있어요" },
  { key: "private", label: "비공개", desc: "나만 볼 수 있어요" },
];

function Soon() {
  return <span className="soon"> (준비 중)</span>;
}

// 새 글 쓰기(article 없음)와 글 수정(article 있음)
export default function Editor({ article }: { article?: Article }) {
  const router = useRouter();
  const isEdit = !!article;
  const [category, setCategory] = useState<Category>(article?.category ?? "DAILY");
  const [title, setTitle] = useState(article?.title ?? "");
  const [content, setContent] = useState(article?.content ?? "");
  const [summary, setSummary] = useState(article?.summary ?? "");
  const [tried, setTried] = useState(false);
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({});
  const [status, setStatus] = useState({ text: "", error: false });
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const titleErrText =
    serverErrors.title || (!title.trim() ? "제목을 입력해 주세요." : title.length > 255 ? "제목은 255자 이하로 입력해 주세요." : "");
  const contentErrText = serverErrors.content || (!content.trim() ? "내용을 입력해 주세요." : "");
  const summaryErrText = serverErrors.summary || (summary.length > 300 ? "요약은 300자 이하로 입력해 주세요." : "");
  const show = (text: string, field: string) => (tried || !!serverErrors[field]) && !!text;
  const backHref = isEdit ? `/articles/${article.id}` : "/";

  const clear = (field: string) => {
    if (serverErrors[field]) setServerErrors({ ...serverErrors, [field]: "" });
  };

  const publish = async () => {
    setTried(true);
    if (titleErrText || contentErrText || summaryErrText) {
      setStatus({ text: "", error: false });
      return;
    }
    setBusy(true);
    setStatus({ text: isEdit ? "수정하는 중…" : "게시하는 중…", error: false });
    const input = { title: title.trim(), content, category, summary: summary.trim() };
    try {
      const saved = isEdit ? await api.update(article.id, input) : await api.create(input);
      router.push(`/articles/${saved.id}`);
      router.refresh();
    } catch (e) {
      setBusy(false);
      if (e instanceof ApiError && e.status === 401) {
        // 로그인이 풀렸으면 다시 로그인한 뒤 이 화면으로 돌아온다. (쓰던 내용은 남지 않는다)
        startLogin(isEdit ? `/articles/${article.id}/edit` : "/write");
        return;
      }
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) {
        setServerErrors(e.fieldErrors);
        setStatus({ text: "", error: false });
      } else {
        setStatus({ text: e instanceof ApiError ? e.message : "저장하지 못했어요.", error: true });
      }
    }
  };

  const pageTitle = isEdit ? "글 수정" : "새 글 쓰기";
  const publishLabel = isEdit ? "수정 완료" : "게시";
  const statusClass = (base: string) => (status.error ? `${base} is-error` : base);

  return (
    <div className="page">
      {/* PC 헤더 */}
      <header className="editor-header only-desktop">
        <div className="editor-title-area">
          <Logo />
          <span className="vbar" />
          <span className="editor-page-title">{pageTitle}</span>
        </div>
        <div className="editor-actions">
          <span role="status" className={statusClass("status")}>
            {status.text}
          </span>
          <span className="drafts-link" aria-disabled="true">
            임시저장 글
          </span>
          <button type="button" className="btn-box" disabled>
            임시저장
          </button>
          <button type="button" className="btn-box" disabled>
            미리보기
          </button>
          <button type="button" className="btn-publish" onClick={publish} disabled={busy}>
            {publishLabel}
          </button>
        </div>
      </header>

      {/* 모바일 헤더 */}
      <header className="editor-header-mobile only-mobile">
        <Link href={backHref} aria-label="뒤로" className="icon-btn">
          <BackIcon />
        </Link>
        <span>{pageTitle}</span>
        <button type="button" className="draft-mobile" disabled>
          임시저장
        </button>
      </header>
      <span role="status" className={statusClass("status-mobile only-mobile")} style={{ display: "block" }}>
        {status.text}
      </span>

      <div className="editor-body">
        <main className="editor-main">
          <div className="cat-row">
            <label htmlFor="ed-cat">카테고리</label>
            <select id="ed-cat" className="select" value={category} onChange={(e) => setCategory(e.target.value as Category)}>
              {CATEGORIES.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <div className="field-col">
            <label htmlFor="ed-title" style={{ position: "absolute", width: 1, height: 1, overflow: "hidden", clip: "rect(0 0 0 0)" }}>
              제목
            </label>
            <input
              id="ed-title"
              type="text"
              className="title-input"
              placeholder="제목을 입력하세요"
              value={title}
              onChange={(e) => {
                setTitle(e.target.value);
                clear("title");
              }}
              aria-invalid={show(titleErrText, "title")}
            />
            {show(titleErrText, "title") && (
              <p role="alert" className="field-error">
                {titleErrText}
              </p>
            )}
          </div>

          <div role="toolbar" aria-label="서식 (준비 중)" className="toolbar">
            <button type="button" className="tool" aria-label="소제목" disabled style={{ fontSize: 16, fontWeight: 700 }}>
              H
            </button>
            <button type="button" className="tool" aria-label="굵게" disabled style={{ fontSize: 17, fontWeight: 800 }}>
              B
            </button>
            <button type="button" className="tool" aria-label="기울임" disabled style={{ fontSize: 17, fontStyle: "italic", fontFamily: "var(--body)" }}>
              I
            </button>
            <button type="button" className="tool only-desktop" aria-label="밑줄" disabled style={{ fontSize: 16, textDecoration: "underline" }}>
              U
            </button>
            <span className="tool-sep" />
            <button type="button" className="tool" aria-label="인용문" disabled>
              <QuoteIcon />
            </button>
            <button type="button" className="tool" aria-label="목록" disabled>
              <ListIcon />
            </button>
            <button type="button" className="tool" aria-label="링크" disabled>
              <LinkIcon />
            </button>
            <button type="button" className="tool" aria-label="코드 블록" disabled>
              <CodeIcon />
            </button>
            <button type="button" className="tool-photo" disabled>
              <PhotoIcon />
              <span className="only-desktop">사진 추가</span>
              <span className="only-mobile">사진</span>
            </button>
          </div>

          <div className="field-col">
            <label htmlFor="ed-body" style={{ position: "absolute", width: 1, height: 1, overflow: "hidden", clip: "rect(0 0 0 0)" }}>
              내용
            </label>
            <textarea
              id="ed-body"
              className="body-input"
              placeholder="오늘은 어떤 이야기를 남길까요?"
              value={content}
              onChange={(e) => {
                setContent(e.target.value);
                clear("content");
              }}
              aria-invalid={show(contentErrText, "content")}
            />
            {show(contentErrText, "content") && (
              <p role="alert" className="field-error">
                {contentErrText}
              </p>
            )}
          </div>

          <div className="photos">
            <span className="photos-label">첨부한 사진 · 끌어서 순서를 바꾸고, 클릭하면 본문에 넣어요</span>
            <div className="photo-row">
              <div className="photo">[사진 1]</div>
              <div className="photo">[사진 2]</div>
              <button type="button" className="photo-add" aria-label="사진 추가" disabled>
                +
              </button>
            </div>
          </div>
        </main>

        <aside className="editor-aside">
          <section className="side is-disabled">
            <h2>
              공개 범위
              <Soon />
            </h2>
            {VISIBILITY.map((v) => (
              <label key={v.key} className={v.key === "public" ? "vis is-checked" : "vis"}>
                <input type="radio" name="vis" disabled defaultChecked={v.key === "public"} />
                <span className="vis-text">
                  <span>{v.label}</span>
                  <span>{v.desc}</span>
                </span>
              </label>
            ))}
          </section>

          <section className="side">
            <label htmlFor="ed-sum" className="side-title">
              요약
            </label>
            <textarea
              id="ed-sum"
              className="summary-input"
              placeholder="최근 글 목록에 보일 두세 줄 요약 (비워 두면 본문 앞부분이 쓰여요)"
              value={summary}
              onChange={(e) => {
                setSummary(e.target.value);
                clear("summary");
              }}
              aria-invalid={show(summaryErrText, "summary")}
            />
            {show(summaryErrText, "summary") && (
              <p role="alert" className="field-error">
                {summaryErrText}
              </p>
            )}
          </section>

          <section className="side is-disabled">
            <label htmlFor="ed-tag" className="side-title">
              태그
              <Soon />
            </label>
            <div className="chips">
              <span className="chip">#[태그]</span>
              <span className="chip">#[태그]</span>
            </div>
            <input id="ed-tag" type="text" className="side-input" placeholder="태그 입력 후 Enter" disabled />
          </section>

          <section className="side is-disabled">
            <span className="side-title">
              대표 이미지
              <Soon />
            </span>
            <span className="side-desc">링크를 공유할 때 미리보기로 보여요.</span>
            <button type="button" className="image-pick" disabled>
              첨부한 사진에서 고르기
            </button>
          </section>

          <section className="side is-list is-disabled">
            <label className="switch-row">
              <span>
                댓글 허용
                <Soon />
              </span>
              <input type="checkbox" defaultChecked disabled />
            </label>
            <div className="divider" />
            <div className="switch-row schedule-row">
              <label htmlFor="ed-when">예약 게시</label>
              <input id="ed-when" type="datetime-local" disabled />
            </div>
          </section>

          <p className="hint">흐리게 표시된 기능은 준비 중이에요.</p>

          {isEdit &&
            (confirming ? (
              <DeleteConfirm id={article.id} onCancel={() => setConfirming(false)} />
            ) : (
              <button type="button" className="delete-link" onClick={() => setConfirming(true)}>
                이 글 삭제
              </button>
            ))}
        </aside>
      </div>

      {/* 모바일 아래 버튼 */}
      <div className="bottom-bar only-mobile">
        <button type="button" className="btn-box" disabled>
          미리보기
        </button>
        <button type="button" className="btn-publish" onClick={publish} disabled={busy}>
          {publishLabel}
        </button>
      </div>
    </div>
  );
}
