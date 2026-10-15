// Éléments d'interface communs aux vues du back-office. Aucun appel à l'API ici.

import type { ReactNode } from "react";

type ButtonVariant = "primary" | "secondary" | "danger";

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  primary: "bg-accent text-white hover:bg-accent-hover",
  secondary: "border border-border bg-surface text-foreground hover:bg-surface-hover",
  danger: "border border-border text-danger hover:bg-surface-hover",
};

export function Button({
  children,
  variant = "primary",
  type = "button",
  disabled,
  onClick,
}: {
  children: ReactNode;
  variant?: ButtonVariant;
  type?: "button" | "submit";
  disabled?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type={type}
      disabled={disabled}
      onClick={onClick}
      className={`rounded-full px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${BUTTON_STYLES[variant]}`}
    >
      {children}
    </button>
  );
}

export function PageHeader({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {action}
    </div>
  );
}

export function ErrorBanner({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger"
    >
      <span>{message}</span>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          Réessayer
        </Button>
      )}
    </div>
  );
}

export function Loading({ label = "Chargement…" }: { label?: string }) {
  return (
    <p role="status" className="py-12 text-center text-sm text-muted">
      {label}
    </p>
  );
}

const FIELD_STYLES =
  "w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-foreground outline-none focus:border-foreground aria-invalid:border-danger";

export function Field({
  id,
  label,
  error,
  hint,
  children,
}: {
  id: string;
  label: string;
  error?: string | null;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-sm text-danger">
          {error}
        </p>
      ) : (
        hint && <p className="text-sm text-muted">{hint}</p>
      )}
    </div>
  );
}

export function TextInput({
  id,
  value,
  onChange,
  error,
  type = "text",
  placeholder,
  autoComplete,
  inputMode,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
  error?: string | null;
  type?: "text" | "email" | "password" | "url";
  placeholder?: string;
  autoComplete?: string;
  inputMode?: "numeric";
}) {
  return (
    <input
      id={id}
      type={type}
      value={value}
      placeholder={placeholder}
      autoComplete={autoComplete}
      inputMode={inputMode}
      aria-invalid={!!error}
      aria-describedby={error ? `${id}-error` : undefined}
      onChange={(event) => onChange(event.target.value)}
      className={FIELD_STYLES}
    />
  );
}

export function TextArea({
  id,
  value,
  onChange,
  error,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
  error?: string | null;
}) {
  return (
    <textarea
      id={id}
      value={value}
      rows={5}
      aria-invalid={!!error}
      aria-describedby={error ? `${id}-error` : undefined}
      onChange={(event) => onChange(event.target.value)}
      className={FIELD_STYLES}
    />
  );
}

export function Select({
  id,
  value,
  onChange,
  options,
  error,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  error?: string | null;
}) {
  return (
    <select
      id={id}
      value={value}
      aria-invalid={!!error}
      aria-describedby={error ? `${id}-error` : undefined}
      onChange={(event) => onChange(event.target.value)}
      className={FIELD_STYLES}
    >
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
}

export function Pagination({
  page,
  pageCount,
  onChange,
}: {
  page: number;
  pageCount: number;
  onChange: (page: number) => void;
}) {
  if (pageCount <= 1) return null;
  return (
    <nav aria-label="Pagination" className="mt-6 flex items-center justify-center gap-4 text-sm">
      <Button variant="secondary" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        Précédente
      </Button>
      <span className="text-muted">
        Page {page} sur {pageCount}
      </span>
      <Button variant="secondary" disabled={page >= pageCount} onClick={() => onChange(page + 1)}>
        Suivante
      </Button>
    </nav>
  );
}
