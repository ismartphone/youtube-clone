"use client";

import { useState } from "react";
import { errorMessage } from "./useRemoteData";
import { useSession } from "./session";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export interface LoginErrors {
  email?: string;
  password?: string;
}

export function useLoginController() {
  const { login } = useSession();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<LoginErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    const fieldErrors: LoginErrors = {};
    if (!EMAIL_PATTERN.test(email.trim())) fieldErrors.email = "Saisissez une adresse email valide.";
    if (!password) fieldErrors.password = "Saisissez votre mot de passe.";
    setErrors(fieldErrors);
    if (Object.keys(fieldErrors).length > 0) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      // En cas de succès, la session passe à « connecté » et la page redirige vers le tableau de bord.
      await login(email.trim(), password);
    } catch (error) {
      setSubmitError(errorMessage(error));
      setSubmitting(false);
    }
  }

  return { email, setEmail, password, setPassword, errors, submitError, submitting, submit };
}
