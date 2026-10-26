import ReactMarkdown, { type Components } from "react-markdown";
import remarkBreaks from "remark-breaks";

// 본문은 Markdown으로 쓴다. remark-breaks로 줄바꿈 한 번도 그대로 줄을 바꾼다 (Markdown 기본은 앞 줄과 이어 붙인다).
// 본문 안의 HTML은 실행하지 않고 글자로 보여 준다 (react-markdown 기본값).
// node(문서 구조 정보)는 HTML 속성이 아니라서 빼고 넘긴다.
/* eslint-disable @typescript-eslint/no-unused-vars */
const components: Components = {
  // 소제목은 1~4단계(# ~ ####). 글 제목이 h1이라 한 단계씩 낮춰 그리고, 크기는 단계(h-1 ~ h-4)로 정한다.
  h1: ({ node, ...props }) => <h2 {...props} className="h-1" />,
  h2: ({ node, ...props }) => <h3 {...props} className="h-2" />,
  h3: ({ node, ...props }) => <h4 {...props} className="h-3" />,
  h4: ({ node, ...props }) => <h5 {...props} className="h-4" />,
  h5: ({ node, ...props }) => <h6 {...props} className="h-4" />,
  h6: ({ node, ...props }) => <h6 {...props} className="h-4" />,
  // 번호 목록은 CSS 카운터로 번호를 그린다. "3. 항목"처럼 3부터 시작하면 그 번호부터.
  ol: ({ node, start, ...props }) => <ol {...props} style={{ counterReset: `item ${(start ?? 1) - 1}` }} />,
};
/* eslint-enable @typescript-eslint/no-unused-vars */

export default function Markdown({ children }: { children: string }) {
  return (
    <div className="body" lang="ko">
      <ReactMarkdown remarkPlugins={[remarkBreaks]} components={components}>
        {children}
      </ReactMarkdown>
    </div>
  );
}
