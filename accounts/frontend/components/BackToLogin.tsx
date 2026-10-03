import Link from "next/link";
import { ChevronLeftIcon } from "./icons";

export default function BackToLogin() {
  return (
    <Link href="/login" className="back-link">
      <ChevronLeftIcon />
      로그인으로 돌아가기
    </Link>
  );
}
