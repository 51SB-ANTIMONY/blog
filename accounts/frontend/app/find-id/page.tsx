import type { Metadata } from "next";
import AuthShell from "@/components/AuthShell";
import FindIdForm from "./FindIdForm";

export const metadata: Metadata = { title: "아이디 찾기 · ANTIMONY" };

export default function FindIdPage() {
  return (
    <AuthShell>
      <FindIdForm />
    </AuthShell>
  );
}
