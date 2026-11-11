"use client";

import { useState } from "react";
import { ApiError, commentApi, startLogin } from "@/lib/api";
import { formatStamp, isEdited } from "@/lib/format";
import { text, type TextKey } from "@/lib/i18n";
import { useLang } from "@/lib/prefs";
import type { Comment } from "@/lib/types";
import { Tx } from "./Bi";
import { LockIcon } from "./icons";

// 글 아래 댓글. 댓글과 답글 두 층이고, 먼저 쓴 댓글이 위에 온다.
// 잠긴 댓글은 서버가 내용을 비워서 보내므로(content: null), 화면은 "잠긴 댓글입니다."만 보여 준다.
// 서버는 삭제한 댓글을 지우지 않고 "삭제됨"으로 남긴다. 화면에서는
// - 삭제된 답글은 보이지 않는다.
// - 삭제된 댓글은 남은 답글이 있을 때만 "삭제된 댓글입니다."로 보이고, 없으면 보이지 않는다.

type Props = {
  articleId: number;
  initial: Comment[];
  loggedIn: boolean;
};

// 지금 열려 있는 입력 칸. 한 번에 하나만 연다.
type Open =
  | { kind: "none" }
  | { kind: "reply"; rootId: number; to: string }
  | { kind: "edit"; id: number }
  | { kind: "delete"; id: number };

const isMasked = (c: Comment) => c.locked && !c.deleted && c.content === null;

// 화면에 보일 댓글만 남긴다
function visibleThreads(comments: Comment[]): Comment[] {
  return comments
    .map((root) => ({ ...root, replies: root.replies.filter((r) => !r.deleted) }))
    .filter((root) => !root.deleted || root.replies.length > 0);
}

// 개수: 삭제된 댓글과 내가 볼 수 없는 잠긴 댓글은 세지 않는다
function countOf(threads: Comment[]): number {
  return threads.reduce((sum, root) => sum + [root, ...root.replies].filter((c) => !c.deleted && !isMasked(c)).length, 0);
}

export default function Comments({ articleId, initial, loggedIn }: Props) {
  const lang = useLang();
  const [comments, setComments] = useState(initial);
  const [open, setOpen] = useState<Open>({ kind: "none" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const threads = visibleThreads(comments);
  const count = countOf(threads);

  // 요청을 보내고, 성공하면 목록을 다시 받아 온다. 성공하면 true
  const run = async (action: () => Promise<unknown>): Promise<boolean> => {
    setBusy(true);
    setError("");
    try {
      await action();
      setComments(await commentApi.list(articleId));
      setOpen({ kind: "none" });
      return true;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : text(lang, "cFailed"));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const changeOpen = (next: Open) => {
    setOpen(next);
    setError("");
  };

  const item = (c: Comment, rootId: number) => (
    <CommentItem
      key={c.id}
      comment={c}
      open={open}
      busy={busy}
      canReply={loggedIn}
      onReply={() => changeOpen({ kind: "reply", rootId, to: c.author ?? "" })}
      onEdit={() => changeOpen({ kind: "edit", id: c.id })}
      onAskDelete={() => changeOpen({ kind: "delete", id: c.id })}
      onCancel={() => changeOpen({ kind: "none" })}
      onSave={(content, locked) => run(() => commentApi.update(c.id, { content, locked }))}
      onDelete={() => run(() => commentApi.remove(c.id))}
    />
  );

  return (
    <section className="cm" aria-labelledby="cm-h">
      <div className="cmh">
        <h2 id="cm-h" className="cmt">
          <Tx k="comments" />
          <span className="cmn">{count}</span>
        </h2>
      </div>

      {threads.length === 0 && (
        <p className="cmnote">
          <Tx k="cEmpty" />
        </p>
      )}
      {error && (
        <p role="alert" className="cmerr">
          {error}
        </p>
      )}

      {threads.length > 0 && (
        <ul className="cml">
          {threads.map((root) => (
            <li key={root.id} className="thr">
              {item(root, root.id)}
              {root.replies.length > 0 && <ul className="rps">{root.replies.map((r) => <li key={r.id}>{item(r, root.id)}</li>)}</ul>}
              {open.kind === "reply" && open.rootId === root.id && (
                <div className="rbox">
                  <span className="rl">{lang === "ko" ? `${open.to}에게 답글` : `Replying to ${open.to}`}</span>
                  <CommentForm
                    placeholder="rPh"
                    submitLabel="cSubmit"
                    busy={busy}
                    onSubmit={(content, locked) => run(() => commentApi.create(articleId, { content, locked, parentId: root.id }))}
                    onCancel={() => changeOpen({ kind: "none" })}
                    autoFocus
                  />
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {loggedIn ? (
        <div className="cbox">
          <CommentForm
            placeholder="cPh"
            submitLabel="cSubmit"
            busy={busy}
            onSubmit={(content, locked) => run(() => commentApi.create(articleId, { content, locked, parentId: null }))}
          />
        </div>
      ) : (
        <div className="cbox cl">
          <span>
            <Tx k="cLoginNote" />
          </span>
          <button type="button" className="pillbtn" onClick={() => startLogin(`/articles/${articleId}`)}>
            <Tx k="login" />
          </button>
        </div>
      )}
    </section>
  );
}

type ItemProps = {
  comment: Comment;
  open: Open;
  busy: boolean;
  canReply: boolean;
  onReply: () => void;
  onEdit: () => void;
  onAskDelete: () => void;
  onCancel: () => void;
  onSave: (content: string, locked: boolean) => Promise<boolean>;
  onDelete: () => void;
};

function CommentItem({ comment: c, open, busy, canReply, onReply, onEdit, onAskDelete, onCancel, onSave, onDelete }: ItemProps) {
  const lang = useLang();
  if (isMasked(c)) {
    return (
      <div className="cmi">
        <p className="cmb cmd mk">
          <LockIcon size={15} />
          <Tx k="cMasked" />
        </p>
      </div>
    );
  }

  const editing = open.kind === "edit" && open.id === c.id;
  const confirming = open.kind === "delete" && open.id === c.id;

  return (
    <div className="cmi">
      <div className="cmm">
        {c.author && (
          <b>
            {c.author}
            {c.locked && (
              <span className="lk" title={text(lang, "lockOn")}>
                <LockIcon size={13} />
              </span>
            )}
          </b>
        )}
        <span>{formatStamp(c.createdAt)}</span>
        {!c.deleted && isEdited(c.createdAt, c.updatedAt) && (
          <span>
            <Tx k="edited" /> {formatStamp(c.updatedAt)}
          </span>
        )}
      </div>

      {editing ? (
        <CommentForm
          placeholder="cPh"
          submitLabel="cSave"
          busy={busy}
          initialContent={c.content ?? ""}
          initialLocked={c.locked}
          onSubmit={onSave}
          onCancel={onCancel}
          autoFocus
        />
      ) : (
        <>
          {c.deleted ? (
            <p className="cmb cmd">
              <Tx k="cDeleted" />
            </p>
          ) : (
            <p className="cmb" lang="ko">
              {c.content}
            </p>
          )}
          {!c.deleted && (canReply || c.editable) && (
            <div className="cact">
              {canReply && (
                <button type="button" className="cb" onClick={onReply}>
                  <Tx k="reply" />
                </button>
              )}
              {c.editable && !confirming && (
                <>
                  <button type="button" className="cb" onClick={onEdit}>
                    <Tx k="editBtn" />
                  </button>
                  <button type="button" className="cb danger" onClick={onAskDelete}>
                    <Tx k="delBtn" />
                  </button>
                </>
              )}
              {c.editable && confirming && (
                <>
                  <span className="cf">
                    <Tx k="cConfirm" />
                  </span>
                  <button type="button" className="cb danger" onClick={onDelete} disabled={busy}>
                    <Tx k="delBtn" />
                  </button>
                  <button type="button" className="cb" onClick={onCancel} disabled={busy} autoFocus>
                    <Tx k="cancel" />
                  </button>
                </>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}

type FormProps = {
  placeholder: TextKey;
  submitLabel: TextKey;
  busy: boolean;
  initialContent?: string;
  initialLocked?: boolean;
  // 성공하면 true. 새 댓글 칸은 성공했을 때만 비운다
  onSubmit: (content: string, locked: boolean) => Promise<boolean>;
  onCancel?: () => void;
  autoFocus?: boolean;
};

// 댓글, 답글, 수정에 함께 쓰는 입력 칸 (내용 + 잠금 + 등록/취소)
function CommentForm({ placeholder, submitLabel, busy, initialContent = "", initialLocked = false, onSubmit, onCancel, autoFocus }: FormProps) {
  const lang = useLang();
  const [content, setContent] = useState(initialContent);
  const [locked, setLocked] = useState(initialLocked);
  const [empty, setEmpty] = useState(false);

  const submit = async () => {
    const body = content.trim();
    if (!body) {
      setEmpty(true);
      return;
    }
    setEmpty(false);
    if (await onSubmit(body, locked)) {
      setContent("");
      setLocked(false);
    }
  };

  return (
    <>
      <textarea
        className="cmta"
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder={text(lang, placeholder)}
        aria-label={text(lang, placeholder)}
        aria-invalid={empty || undefined}
        autoFocus={autoFocus}
      />
      {empty && (
        <p role="alert" className="cmerr">
          <Tx k="cEmptyErr" />
        </p>
      )}
      <div className="cma" style={onCancel ? undefined : { justifyContent: "flex-end" }}>
        <button
          type="button"
          className="lkt"
          aria-pressed={locked}
          onClick={() => setLocked(!locked)}
          aria-label={text(lang, "lockOn")}
          title={text(lang, "lockOn")}
        >
          <LockIcon />
        </button>
        <button type="button" className="btn sm" onClick={submit} disabled={busy}>
          <Tx k={submitLabel} />
        </button>
        {onCancel && (
          <button type="button" className="cb cx" onClick={onCancel} disabled={busy}>
            <Tx k="cancel" />
          </button>
        )}
      </div>
    </>
  );
}
