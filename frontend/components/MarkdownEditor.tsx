"use client";

import { Extension, type ChainedCommands, type JSONContent } from "@tiptap/core";
import HardBreak from "@tiptap/extension-hard-break";
import { Placeholder } from "@tiptap/extensions";
import { Markdown } from "@tiptap/markdown";
import { Plugin } from "@tiptap/pm/state";
import { EditorContent, useEditor, useEditorState, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { useEffect, useRef, useState } from "react";
import { text, type TextKey } from "@/lib/i18n";
import { useLang } from "@/lib/prefs";
import { Tx } from "./Bi";
import { CodeIcon, LinkIcon, ListIcon, ListOrderedIcon, PhotoIcon, QuoteIcon } from "./icons";

// 본문 편집기. 쓰는 동안 서식이 적용된 모습 그대로 보이고, 저장할 때는 Markdown으로 바꾼다.
// 서식은 도구 버튼으로 넣거나, Markdown 문법을 입력하면 바로 바뀐다.
//   # ~ #### 소제목(1~4단계), > 인용, - 목록, 1. 번호 목록, **굵게**, *기울임*, `코드`, ``` 코드 블록
// 밑줄은 Markdown에 문법이 없어 저장할 수 없으므로 넣지 않는다.

const HEADING_LEVELS = [1, 2, 3, 4] as const;

// 줄바꿈 저장 방식
//   Enter는 언제나 줄바꿈이고 문단을 나누지 않는다. 엔터 두 번은 줄바꿈 두 번(빈 줄 하나)이다.
//   줄바꿈은 Markdown의 "\ + 줄 끝"으로 저장한다. 공백 두 칸 방식은 빈 줄을 문단 구분으로 읽어 줄바꿈이 사라진다.
//   문단 끝의 줄바꿈은 저장하지 않는다 (남기면 글 화면에 \ 글자가 보인다).
const BREAK_MARK = "\u0000";
const LineBreak = HardBreak.extend({ renderMarkdown: () => `${BREAK_MARK}\n` });
const finishMarkdown = (md: string) =>
  md.replace(new RegExp(`(?:${BREAK_MARK}\\n)+(?=\\n|$)`, "g"), "").replaceAll(`${BREAK_MARK}\n`, "\\\n");

// 예전 글처럼 문단이 나뉘어 저장된 글은, 이어진 문단을 줄바꿈 두 번으로 합쳐서 연다 (한 문단 안의 빈 줄)
function mergeParagraphs(doc: JSONContent): JSONContent {
  const content: JSONContent[] = [];
  for (const node of doc.content ?? []) {
    const last = content[content.length - 1];
    if (node.type === "paragraph" && last?.type === "paragraph") {
      last.content = [...(last.content ?? []), { type: "hardBreak" }, { type: "hardBreak" }, ...(node.content ?? [])];
    } else {
      content.push(node.type === "paragraph" ? { ...node, content: [...(node.content ?? [])] } : node);
    }
  }
  return { ...doc, content };
}

// 키 동작
//   Enter: 줄바꿈. 인용 안에서는 인용을 끝내고 다음 줄은 보통 문단 (인용 안에서 줄을 바꾸려면 Shift+Enter)
//          소제목, 목록, 코드 블록은 원래 동작 (소제목은 다음 줄이 보통 문단, 목록은 다음 항목)
//   Backspace: 소제목, 인용, 코드 블록의 맨 앞에서 누르면 서식을 풀고 보통 문단으로
const BlockKeys = Extension.create({
  name: "blockKeys",
  priority: 1000,
  addKeyboardShortcuts() {
    return {
      Enter: ({ editor }) => {
        const { $from, empty } = editor.state.selection;
        if (!empty || $from.parent.type.name !== "paragraph" || editor.isActive("listItem")) return false;
        if (editor.isActive("blockquote")) {
          if ($from.parent.content.size === 0) return editor.commands.lift("blockquote");
          return editor.chain().splitBlock().lift("blockquote").run();
        }
        return editor.commands.setHardBreak();
      },
      Backspace: ({ editor }) => {
        const { $from, empty } = editor.state.selection;
        if (!empty || $from.parentOffset !== 0) return false;
        if (editor.isActive("heading") || editor.isActive("codeBlock")) return editor.commands.setParagraph();
        if (editor.isActive("blockquote") && !editor.isActive("listItem")) return editor.commands.lift("blockquote");
        return false;
      },
    };
  },
});

// 줄바꿈(Enter 한 번) 뒤 줄의 맨 앞에 Markdown 문법을 쓰면, 그 자리에서 문단을 나누고 서식을 넣는다.
// (저장한 Markdown에서는 줄 맨 앞의 #, >, -, 1. 이 서식이 되므로, 쓰는 화면도 같게 보이도록)
// 문단 맨 앞은 기본 입력 규칙이 처리한다.
const LINE_RULES: [RegExp, (chain: ChainedCommands, m: RegExpMatchArray) => ChainedCommands][] = [
  [/^(#{1,4}) $/, (c, m) => c.setHeading({ level: m[1].length as 1 | 2 | 3 | 4 })],
  [/^> $/, (c) => c.toggleBlockquote()],
  [/^[-*+] $/, (c) => c.toggleBulletList()],
  [/^1[.)] $/, (c) => c.toggleOrderedList()],
  [/^```$/, (c) => c.setCodeBlock()],
];
const LineStartSyntax = Extension.create({
  name: "lineStartSyntax",
  addProseMirrorPlugins() {
    const editor = this.editor;
    return [
      new Plugin({
        props: {
          handleTextInput(view, from, to, text) {
            if (from !== to) return false;
            const $from = view.state.doc.resolve(from);
            if ($from.parent.type.name !== "paragraph") return false;
            // 이 줄의 시작: 커서 앞의 마지막 줄바꿈 바로 뒤
            let breakPos = -1;
            $from.parent.forEach((child, offset) => {
              if (child.type.name === "hardBreak" && offset < $from.parentOffset) breakPos = $from.start() + offset;
            });
            if (breakPos < 0) return false;
            const line = view.state.doc.textBetween(breakPos + 1, from) + text;
            for (const [re, apply] of LINE_RULES) {
              const m = line.match(re);
              if (!m) continue;
              apply(editor.chain().deleteRange({ from: breakPos, to: from }).splitBlock(), m).run();
              return true;
            }
            return false;
          },
        },
      }),
    ];
  },
});

// **굵게** 뒤에 띄어쓰기를 했다가 지우고 이어 쓰면, 이어 쓰는 글자는 굵게 하지 않는다.
// (서식 없는 글자를 지워서 서식 있는 글자 바로 뒤로 돌아왔을 때만. 굵은 글자 중간을 고칠 때는 그대로 굵게)
const INLINE_MARKS = ["bold", "italic", "code"];
const PlainAfterBackspace = Extension.create({
  name: "plainAfterBackspace",
  addProseMirrorPlugins() {
    return [
      new Plugin({
        props: {
          handleKeyDown(view, event) {
            if (event.key !== "Backspace" || event.isComposing) return false;
            const { selection, doc } = view.state;
            if (!selection.empty) return false;
            const deleted = selection.$from.nodeBefore;
            if (!deleted?.isText || deleted.text?.length !== 1 || selection.from < 2) return false;
            const before = doc.resolve(selection.from - 1).nodeBefore;
            if (!before?.isText) return false;
            const drop = before.marks.filter((m) => INLINE_MARKS.includes(m.type.name) && !m.isInSet(deleted.marks));
            if (drop.length === 0) return false;
            // 기본 Backspace가 글자를 지운 다음, 이어 쓸 글자의 서식에서 뺀다
            setTimeout(() => {
              const { state } = view;
              if (!state.selection.empty) return;
              const marks = state.selection.$from.marks().filter((m) => !drop.some((d) => d.eq(m)));
              view.dispatch(state.tr.setStoredMarks(marks));
            }, 0);
            return false;
          },
        },
      }),
    ];
  },
});

type Tool = {
  key: TextKey;
  label: React.ReactNode;
  className?: string;
  run: (editor: Editor) => void;
  active: (editor: Editor) => boolean;
};

const MARK_TOOLS: Tool[] = [
  { key: "tbBold", label: "B", className: "tb-b", run: (e) => e.chain().focus().toggleBold().run(), active: (e) => e.isActive("bold") },
  { key: "tbItalic", label: "I", className: "tb-i", run: (e) => e.chain().focus().toggleItalic().run(), active: (e) => e.isActive("italic") },
];

const BLOCK_TOOLS: Tool[] = [
  { key: "tbQuote", label: <QuoteIcon />, run: (e) => e.chain().focus().toggleBlockquote().run(), active: (e) => e.isActive("blockquote") },
  { key: "tbList", label: <ListIcon />, run: (e) => e.chain().focus().toggleBulletList().run(), active: (e) => e.isActive("bulletList") },
  { key: "tbOrdered", label: <ListOrderedIcon />, run: (e) => e.chain().focus().toggleOrderedList().run(), active: (e) => e.isActive("orderedList") },
  { key: "tbCode", label: <CodeIcon />, run: (e) => e.chain().focus().toggleCodeBlock().run(), active: (e) => e.isActive("codeBlock") },
];

// 비어 있을 때의 안내 문구. 그릴 때마다 지금 언어(<html lang>)를 읽는다.
const placeholderText = () => text(document.documentElement.lang === "en" ? "en" : "ko", "bodyPh");

type Props = {
  initial: string;
  // 바뀔 때마다 Markdown을 넘긴다 (비어 있으면 "")
  onChange: (markdown: string) => void;
  invalid: boolean;
  labelId: string;
  describedBy: string;
};

export default function MarkdownEditor({ initial, onChange, invalid, labelId, describedBy }: Props) {
  const lang = useLang();
  // H 버튼을 누르면 1~4단계 버튼 줄을 펼친다
  const [headingOpen, setHeadingOpen] = useState(false);
  // 편집기는 처음 한 번만 만든다. 그 뒤에 바뀌는 값은 ref로 읽는다.
  const onChangeRef = useRef(onChange);
  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  const editor = useEditor({
    // 서버에서는 그리지 않는다 (편집기는 브라우저에서만 만든다)
    immediatelyRender: false,
    extensions: [
      StarterKit.configure({
        underline: false,
        hardBreak: false,
        heading: { levels: [...HEADING_LEVELS] },
        link: { openOnClick: false, autolink: true, defaultProtocol: "https" },
      }),
      // breaks: 줄바꿈 한 번도 줄바꿈으로 읽는다 (글 화면의 remark-breaks와 같게)
      Markdown.configure({ markedOptions: { breaks: true } }),
      LineBreak,
      Placeholder.configure({ placeholder: placeholderText }),
      BlockKeys,
      LineStartSyntax,
      PlainAfterBackspace,
    ],
    // 내용은 만든 뒤(onCreate)에 문단을 합쳐서 넣는다
    editorProps: {
      attributes: { id: "ed-body", class: "edbody body", role: "textbox", "aria-multiline": "true", lang: "ko" },
    },
    onCreate: ({ editor }) => {
      if (!initial || !editor.markdown) return;
      editor.commands.setContent(mergeParagraphs(editor.markdown.parse(initial)), { emitUpdate: false });
    },
    onUpdate: ({ editor }) => onChangeRef.current(editor.isEmpty ? "" : finishMarkdown(editor.getMarkdown())),
  });

  // 오류 표시와 설명은 편집 영역(contenteditable)에 직접 붙인다.
  useEffect(() => {
    if (!editor) return;
    editor.setOptions({
      editorProps: {
        attributes: {
          id: "ed-body",
          class: invalid ? "edbody body err" : "edbody body",
          role: "textbox",
          "aria-multiline": "true",
          "aria-labelledby": labelId,
          ...(describedBy ? { "aria-describedby": describedBy } : {}),
          "aria-invalid": String(invalid),
          lang: "ko",
        },
      },
    });
  }, [editor, invalid, labelId, describedBy]);

  // 언어를 바꾸면 안내 문구를 다시 그린다.
  useEffect(() => {
    if (editor && !editor.isDestroyed) editor.view.dispatch(editor.state.tr);
  }, [editor, lang]);

  // 커서가 있는 곳의 서식을 버튼에 표시한다 (눌림 상태)
  const active = useEditorState({
    editor,
    selector: ({ editor }) => {
      if (!editor) return {} as Record<string, boolean>;
      const map: Record<string, boolean> = { tbLink: editor.isActive("link"), heading: editor.isActive("heading") };
      for (const tool of [...MARK_TOOLS, ...BLOCK_TOOLS]) map[tool.key] = tool.active(editor);
      for (const level of HEADING_LEVELS) map[`h${level}`] = editor.isActive("heading", { level });
      return map;
    },
  });

  const setLink = () => {
    if (!editor) return;
    const current = (editor.getAttributes("link").href as string | undefined) ?? "";
    const input = window.prompt(text(lang, "linkPrompt"), current);
    if (input === null) return;
    const url = input.trim();
    const chain = editor.chain().focus().extendMarkRange("link");
    if (!url) {
      chain.unsetLink().run();
    } else if (editor.state.selection.empty && !editor.isActive("link")) {
      // 고른 글자가 없으면 주소를 글자로 넣고 링크를 건다
      chain.insertContent({ type: "text", text: url, marks: [{ type: "link", attrs: { href: url } }] }).run();
    } else {
      chain.setLink({ href: url }).run();
    }
  };

  // 버튼을 눌러도 편집 영역의 선택이 풀리지 않게 한다
  const keepSelection = (e: React.MouseEvent) => e.preventDefault();

  const toolButton = (tool: Tool) => (
    <button
      key={tool.key}
      type="button"
      className={tool.className}
      aria-label={text(lang, tool.key)}
      title={text(lang, tool.key)}
      aria-pressed={!!active?.[tool.key]}
      disabled={!editor}
      onMouseDown={keepSelection}
      onClick={() => editor && tool.run(editor)}
    >
      {tool.label}
    </button>
  );

  return (
    <>
      <div className="edtb" role="toolbar" aria-label={text(lang, "toolbar")}>
        <button
          type="button"
          className={active?.heading ? "is-on" : undefined}
          aria-label={text(lang, "tbHeading")}
          title={text(lang, "tbHeading")}
          aria-expanded={headingOpen}
          aria-controls="ed-hrow"
          disabled={!editor}
          onMouseDown={keepSelection}
          onClick={() => setHeadingOpen(!headingOpen)}
        >
          H
        </button>
        {MARK_TOOLS.map(toolButton)}
        <span className="tb-sep" />
        {BLOCK_TOOLS.map(toolButton)}
        {toolButton({ key: "tbLink", label: <LinkIcon />, run: setLink, active: () => false })}
        <span className="tb-grow" />
        <button disabled type="button" className="tb-photo">
          <PhotoIcon />
          <Tx k="photoAdd" />
          <span className="soon-label">
            {" "}
            <Tx k="soon" />
          </span>
        </button>
        {headingOpen && (
          <div id="ed-hrow" className="tb-hrow" role="group" aria-label={text(lang, "tbHeading")}>
            {HEADING_LEVELS.map((level) => (
              <button
                key={level}
                type="button"
                className={`tb-h tb-h${level}`}
                aria-label={`${text(lang, "tbHeading")} ${level}`}
                title={`${text(lang, "tbHeading")} ${level}`}
                aria-pressed={!!active?.[`h${level}`]}
                onMouseDown={keepSelection}
                onClick={() => editor?.chain().focus().toggleHeading({ level }).run()}
              >
                H{level}
              </button>
            ))}
          </div>
        )}
      </div>
      {/* 편집기가 준비되기 전에도 같은 크기의 칸을 둬서 화면이 흔들리지 않게 한다 */}
      {editor ? <EditorContent editor={editor} /> : <div className="edbody body" aria-hidden="true" />}
    </>
  );
}
