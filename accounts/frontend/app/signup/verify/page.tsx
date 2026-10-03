import type { Metadata } from "next";
import AuthShell from "@/components/AuthShell";
import VerifyForm from "./VerifyForm";

export const metadata: Metadata = { title: "이메일 인증 · ANTIMONY" };

export default function VerifyPage() {
  return (
    <AuthShell>
      <VerifyForm />
    </AuthShell>
  );
}
