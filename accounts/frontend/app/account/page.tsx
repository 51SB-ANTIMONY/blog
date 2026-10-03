import type { Metadata } from "next";
import { Suspense } from "react";
import AuthShell from "@/components/AuthShell";
import AccountView from "./AccountView";

export const metadata: Metadata = { title: "내 계정 · ANTIMONY" };

export default function AccountPage() {
  return (
    <AuthShell>
      <Suspense>
        <AccountView />
      </Suspense>
    </AuthShell>
  );
}
