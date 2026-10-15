"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useSession } from "@/controllers/session";
import { useLoginController } from "@/controllers/useLoginController";
import { LoginView } from "@/views/LoginView";
import { Loading } from "@/views/ui";

export default function LoginPage() {
  const { session } = useSession();
  const router = useRouter();
  const login = useLoginController();

  // Déjà connecté, ou connexion réussie : direction le tableau de bord.
  useEffect(() => {
    if (session.status === "authenticated") router.replace("/dashboard");
  }, [session.status, router]);

  if (session.status === "loading" || session.status === "authenticated") {
    return <Loading />;
  }
  return (
    <LoginView
      email={login.email}
      onEmailChange={login.setEmail}
      password={login.password}
      onPasswordChange={login.setPassword}
      errors={login.errors}
      submitError={login.submitError}
      submitting={login.submitting}
      onSubmit={login.submit}
    />
  );
}
