"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiError, startLogin } from "@/lib/api";
import { CATEGORIES } from "@/lib/categories";
import { text } from "@/lib/i18n";
import { useLang } from "@/lib/prefs";
import type { Article, Category } from "@/lib/types";
import { Tx } from "./Bi";
import Logo from "./Logo";
import MarkdownEditor from "./MarkdownEditor";
import { ThemeToggle } from "./PrefToggles";

const VISIBILITY = [
  { key: "public", label: "vis1", desc: "vis1d" },
  { key: "members", label: "vis2", desc: "vis2d" },
  { key: "private", label: "vis3", desc: "vis3d" },
] as const;

function Soon() {
  return (
    <span className="soon-label">
      {" "}
      <Tx k="soon" />
    </span>
  );
}

// 새 글 쓰기(article 없음)와 글 수정(article 있음). 본문은 서식이 보이는 편집기로 쓰고 Markdown으로 저장한다.
export default function Editor({ article }: { article?: Article }) {
  const router = useRouter();
  const lang = useLang();
  const isEdit = !!article;
  const [category, setCategory] = useState<Category>(article?.category ?? "DAILY");
  const [title, setTitle] = useState(article?.title ?? "");
  const [content, setContent] = useState(article?.content ?? "");
  const [summary, setSummary] = useState(article?.summary ?? "");
  const [tried, setTried] = useState(false);
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({});
  const [status, setStatus] = useState<{ key?: "publishing" | "updating"; message?: string; error: boolean }>({ error: false });
  const [busy, setBusy] = useState(false);

  const titleErrText =
    serverErrors.title || (!title.trim() ? text(lang, "titleErrEmpty") : title.length > 255 ? text(lang, "titleErrLong") : "");
  const contentErrText = serverErrors.content || (!content.trim() ? text(lang, "bodyErr") : "");
  const summaryErrText = serverErrors.summary || (summary.length > 300 ? text(lang, "sumErr") : "");
  const show = (message: string, field: string) => (tried || !!serverErrors[field]) && !!message;

  const clear = (field: string) => {
    if (serverErrors[field]) setServerErrors({ ...serverErrors, [field]: "" });
  };

  const publish = async () => {
    setTried(true);
    if (titleErrText || contentErrText || summaryErrText) {
      setStatus({ error: false });
      return;
    }
    setBusy(true);
    setStatus({ key: isEdit ? "updating" : "publishing", error: false });
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
        setStatus({ error: false });
      } else {
        // 서버가 보낸 오류 문구(한국어)는 그대로 보여 준다.
        setStatus({ message: e instanceof ApiError ? e.message : text(lang, "saveFailed"), error: true });
      }
    }
  };

  return (
    <div className="app">
      <header className="top">
        <div className="wrap bar">
          <Logo />
          <span className="edx ed-sep" />
          <span className="edx ed-title">
            <Tx k={isEdit ? "edTitle2" : "edTitle"} />
          </span>
          <div className="right ed-right">
            <ThemeToggle />
            <span className="edx ed-drafts" aria-disabled="true">
              <Tx k="draftList" />
            </span>
            <button type="button" className="edb edx" disabled>
              <Tx k="draft" />
            </button>
            <button type="button" className="edb edx" disabled>
              <Tx k="preview" />
            </button>
            <button type="button" className="edpub" onClick={publish} disabled={busy}>
              <Tx k={isEdit ? "publishEdit" : "publish"} />
            </button>
          </div>
        </div>
      </header>

      <div className="edw">
        <main className="edm">
          <p role="status" className={status.error ? "ed-status is-error" : "ed-status"}>
            {status.key ? <Tx k={status.key} /> : status.message}
          </p>
          <div className="ed-cat">
            <label htmlFor="ed-cat">
              <Tx k="wCat" />
            </label>
            <select id="ed-cat" className="edsel" value={category} onChange={(e) => setCategory(e.target.value as Category)}>
              {CATEGORIES.map((c) => (
                <option key={c.code} value={c.code}>
                  {lang === "en" ? c.en : c.ko}
                </option>
              ))}
            </select>
          </div>

          <div className="ed-field">
            <label htmlFor="ed-title" className="sr-only">
              <Tx k="wTitle" />
            </label>
            <input
              id="ed-title"
              type="text"
              className={show(titleErrText, "title") ? "edt err" : "edt"}
              placeholder={text(lang, "titlePh")}
              value={title}
              autoComplete="off"
              onChange={(e) => {
                setTitle(e.target.value);
                clear("title");
              }}
              aria-invalid={show(titleErrText, "title")}
              aria-describedby={show(titleErrText, "title") ? "ed-title-err" : undefined}
            />
            {show(titleErrText, "title") && (
              <p id="ed-title-err" role="alert" className="ederr">
                {titleErrText}
              </p>
            )}
          </div>

          <div className="ed-field">
            <span id="ed-body-label" className="sr-only">
              <Tx k="wBody" />
            </span>
            <MarkdownEditor
              initial={article?.content ?? ""}
              onChange={(markdown) => {
                setContent(markdown);
                clear("content");
              }}
              invalid={show(contentErrText, "content")}
              labelId="ed-body-label"
              describedBy={show(contentErrText, "content") ? "ed-body-err" : ""}
            />
            {show(contentErrText, "content") && (
              <p id="ed-body-err" role="alert" className="ederr">
                {contentErrText}
              </p>
            )}
          </div>

          <div className="soon ed-photos">
            <span className="ed-sub">
              <Tx k="photosHead" />
            </span>
            <div className="ed-photo-row">
              <div className="ph">[1]</div>
              <div className="ph">[2]</div>
              <button disabled type="button" className="ph ph-add" aria-label={text(lang, "photoAdd")}>
                +
              </button>
            </div>
          </div>
        </main>

        <aside className="eda">
          <section className="edsec soon">
            <h2>
              <Tx k="visHead" />
              <Soon />
            </h2>
            {VISIBILITY.map((v) => (
              <label key={v.key} className={v.key === "public" ? "vis on" : "vis"}>
                <input disabled type="radio" name="vis" defaultChecked={v.key === "public"} />
                <span className="vis-text">
                  <span>
                    <Tx k={v.label} />
                  </span>
                  <span>
                    <Tx k={v.desc} />
                  </span>
                </span>
              </label>
            ))}
          </section>

          <section className="edsec">
            <label htmlFor="ed-sum" className="edsec-title">
              <Tx k="sumHead" />
            </label>
            <textarea
              id="ed-sum"
              className={show(summaryErrText, "summary") ? "edsum err" : "edsum"}
              placeholder={text(lang, "sumPh")}
              value={summary}
              onChange={(e) => {
                setSummary(e.target.value);
                clear("summary");
              }}
              aria-invalid={show(summaryErrText, "summary")}
            />
            {show(summaryErrText, "summary") && (
              <p role="alert" className="ederr">
                {summaryErrText}
              </p>
            )}
          </section>

          <section className="edsec soon">
            <label htmlFor="ed-tag" className="edsec-title">
              <Tx k="tagHead" />
              <Soon />
            </label>
            <div className="chips">
              <span className="chip">#[태그]</span>
              <span className="chip">#[태그]</span>
            </div>
            <input disabled id="ed-tag" type="text" placeholder={text(lang, "tagPh")} className="edsel" />
          </section>

          <section className="edsec soon">
            <span className="edsec-title">
              <Tx k="coverHead" />
              <Soon />
            </span>
            <span className="ed-desc">
              <Tx k="coverNote" />
            </span>
            <button disabled type="button" className="cover-pick">
              <Tx k="coverBtn" />
            </button>
          </section>

          <section className="edsec soon is-list">
            <label className="switch-row">
              <span>
                <Tx k="commentsHead" />
                <Soon />
              </span>
              <input disabled type="checkbox" defaultChecked />
            </label>
            <div className="ed-line" />
            <div className="switch-row schedule-row">
              <label htmlFor="ed-when">
                <Tx k="schedHead" />
              </label>
              <input disabled id="ed-when" type="datetime-local" className="edsel" />
            </div>
          </section>

          <p className="ed-desc">
            <Tx k="soonNote" />
          </p>
        </aside>
      </div>
    </div>
  );
}
