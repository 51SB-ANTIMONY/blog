import Link from "next/link";
import SiteFooter from "@/components/SiteFooter";
import SiteHeader from "@/components/SiteHeader";
import { fetchMe } from "@/lib/server";

// 없는 글, 없는 주소
export default async function NotFound() {
  const me = await fetchMe();
  return (
    <div className="page">
      <SiteHeader me={me} variant="post" />
      <main className="article-main">
        <div className="article-col">
          <div className="missing">
            <span className="missing-code">404</span>
            <h1>글을 찾을 수 없어요</h1>
            <p>삭제되었거나 주소가 잘못되었을 수 있어요.</p>
            <Link href="/" className="pill-primary">
              목록으로 가기
            </Link>
          </div>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
