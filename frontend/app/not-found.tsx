import Link from "next/link";
import { Tx } from "@/components/Bi";
import SiteFooter from "@/components/SiteFooter";
import SiteHeader from "@/components/SiteHeader";
import { fetchMe } from "@/lib/server";

// 없는 글, 없는 주소
export default async function NotFound() {
  const me = await fetchMe();
  return (
    <div className="app">
      <SiteHeader me={me} />
      <main className="main">
        <div className="wrap">
          <div className="art missing">
            <span className="missing-code">404</span>
            <h1>
              <Tx k="notFoundTitle" />
            </h1>
            <p>
              <Tx k="notFoundDesc" />
            </p>
            <Link href="/" className="btn">
              <Tx k="toHome" />
            </Link>
          </div>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
