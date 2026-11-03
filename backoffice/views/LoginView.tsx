import type { LoginErrors } from "@/controllers/useLoginController";
import { Logo } from "./AdminShell";
import { Button, ErrorBanner, Field, TextInput } from "./ui";

export function LoginView({
  email,
  onEmailChange,
  password,
  onPasswordChange,
  errors,
  submitError,
  submitting,
  onSubmit,
}: {
  email: string;
  onEmailChange: (value: string) => void;
  password: string;
  onPasswordChange: (value: string) => void;
  errors: LoginErrors;
  submitError: string | null;
  submitting: boolean;
  onSubmit: () => void;
}) {
  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit();
        }}
        aria-busy={submitting}
        className="flex w-full max-w-sm flex-col gap-5 rounded-xl border border-border bg-surface p-8"
      >
        <div>
          <div className="mb-4 flex items-center gap-2.5 text-sm font-semibold">
            <Logo />
            YouTube Clone
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">Back-office</h1>
          <p className="mt-1 text-sm text-muted">Connexion réservée aux administrateurs.</p>
        </div>
        <Field id="email" label="Adresse email" error={errors.email}>
          <TextInput
            id="email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={onEmailChange}
            error={errors.email}
          />
        </Field>
        <Field id="password" label="Mot de passe" error={errors.password}>
          <TextInput
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={onPasswordChange}
            error={errors.password}
          />
        </Field>
        {submitError && <ErrorBanner message={submitError} />}
        <Button type="submit" disabled={submitting}>
          {submitting ? "Connexion…" : "Se connecter"}
        </Button>
      </form>
    </main>
  );
}
