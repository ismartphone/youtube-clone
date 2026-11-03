// Éléments d'interface communs aux vues du back-office. Aucun appel à l'API ici.

import Link from "next/link";
import type { ReactNode } from "react";

type ButtonVariant = "primary" | "secondary" | "danger";
type ButtonSize = "md" | "sm";

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  primary: "bg-accent text-white hover:bg-accent-hover",
  secondary: "border border-border bg-surface text-foreground hover:bg-surface-hover",
  danger: "border border-border text-danger hover:border-danger/50 hover:bg-danger/10",
};

const BUTTON_SIZES: Record<ButtonSize, string> = {
  md: "px-4 py-2",
  sm: "px-3 py-1.5",
};

function buttonClass(variant: ButtonVariant, size: ButtonSize) {
  return `inline-flex items-center justify-center whitespace-nowrap rounded-full text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground disabled:cursor-not-allowed disabled:opacity-50 ${BUTTON_STYLES[variant]} ${BUTTON_SIZES[size]}`;
}

export function Button({
  children,
  variant = "primary",
  size = "md",
  type = "button",
  disabled,
  onClick,
}: {
  children: ReactNode;
  variant?: ButtonVariant;
  size?: ButtonSize;
  type?: "button" | "submit";
  disabled?: boolean;
  onClick?: () => void;
}) {
  return (
    <button type={type} disabled={disabled} onClick={onClick} className={buttonClass(variant, size)}>
      {children}
    </button>
  );
}

/** Lien de navigation présenté comme un bouton. */
export function LinkButton({
  href,
  children,
  variant = "secondary",
  size = "md",
}: {
  href: string;
  children: ReactNode;
  variant?: ButtonVariant;
  size?: ButtonSize;
}) {
  return (
    <Link href={href} className={buttonClass(variant, size)}>
      {children}
    </Link>
  );
}

export function PageHeader({
  title,
  description,
  action,
}: {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted">{description}</p>}
      </div>
      {action}
    </div>
  );
}

/** Bloc encadré : regroupe un formulaire ou une table. */
export function Panel({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="rounded-xl border border-border bg-surface">
      {title && <h2 className="border-b border-border px-5 py-3 text-sm font-semibold">{title}</h2>}
      <div className="p-5">{children}</div>
    </section>
  );
}

/** Groupe de champs d'un formulaire, avec son titre. */
export function FormSection({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <fieldset className="rounded-xl border border-border bg-surface p-5">
      <legend className="float-left mb-4 w-full">
        <span className="block text-sm font-semibold">{title}</span>
        {description && <span className="mt-0.5 block text-sm text-muted">{description}</span>}
      </legend>
      <div className="clear-left flex flex-col gap-5">{children}</div>
    </fieldset>
  );
}

export function ErrorBanner({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger"
    >
      <span className="flex items-center gap-2">
        <span aria-hidden className="flex size-5 shrink-0 items-center justify-center rounded-full border border-danger/60 text-xs font-bold">
          !
        </span>
        {message}
      </span>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Réessayer
        </Button>
      )}
    </div>
  );
}

export function Loading({ label = "Chargement…" }: { label?: string }) {
  return (
    <p role="status" className="flex items-center justify-center gap-3 py-12 text-sm text-muted">
      <span
        aria-hidden
        className="size-4 rounded-full border-2 border-border border-t-foreground motion-safe:animate-spin"
      />
      {label}
    </p>
  );
}

/** Bloc gris qui tient la place d'un contenu en cours de chargement. */
export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden className={`rounded-md bg-surface-hover motion-safe:animate-pulse ${className}`} />;
}

/** Table en cours de chargement : en-têtes réels, lignes grises. */
export function TableSkeleton({ columns, rows = 6 }: { columns: string[]; rows?: number }) {
  return (
    <div role="status" aria-label="Chargement…">
      <DataTable minWidth={0}>
        <thead>
          <tr>
            {columns.map((column) => (
              <Th key={column}>{column}</Th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }, (_, row) => (
            <tr key={row} className={ROW_STYLES}>
              {columns.map((column, index) => (
                <Td key={column}>
                  <Skeleton className={`h-4 ${index === 0 ? "w-48" : "w-16"}`} />
                </Td>
              ))}
            </tr>
          ))}
        </tbody>
      </DataTable>
    </div>
  );
}

/** Message affiché à la place d'une liste vide, avec l'action qui permet d'en sortir. */
export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-border px-6 py-14 text-center">
      <p className="font-medium">{title}</p>
      {description && <p className="max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

// Lignes alternées et survol : partagées par toutes les tables.
const ROW_STYLES = "border-t border-border even:bg-surface-alt hover:bg-surface-hover";

export function DataTable({ children, minWidth = 720 }: { children: ReactNode; minWidth?: number }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-border bg-surface">
      <table className="w-full text-left text-sm" style={{ minWidth }}>
        {children}
      </table>
    </div>
  );
}

export function Th({ children, align = "left" }: { children?: ReactNode; align?: "left" | "right" }) {
  return (
    <th
      scope="col"
      className={`bg-surface-alt px-4 py-2.5 text-xs font-medium tracking-wide text-muted uppercase ${align === "right" ? "text-right" : ""}`}
    >
      {children}
    </th>
  );
}

export function Tr({ children }: { children: ReactNode }) {
  return <tr className={ROW_STYLES}>{children}</tr>;
}

export function Td({
  children,
  align = "left",
  className = "",
}: {
  children?: ReactNode;
  align?: "left" | "right";
  className?: string;
}) {
  return (
    <td className={`px-4 py-2.5 align-middle ${align === "right" ? "text-right tabular-nums" : ""} ${className}`}>
      {children}
    </td>
  );
}

/** Étiquette courte : format, Premium. */
export function Badge({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "accent" }) {
  const styles = tone === "accent" ? "border-accent/50 text-accent" : "border-border text-muted";
  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${styles}`}>
      {children}
    </span>
  );
}

const FIELD_STYLES =
  "w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground outline-none transition-colors placeholder:text-muted/60 focus:border-foreground aria-invalid:border-danger";

export function Field({
  id,
  label,
  error,
  hint,
  hideLabel,
  children,
}: {
  id: string;
  label: string;
  error?: string | null;
  hint?: string;
  // Libellé lu par les lecteurs d'écran seulement (champ dans une table).
  hideLabel?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={hideLabel ? "sr-only" : "text-sm font-medium"}>
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-sm text-danger">
          {error}
        </p>
      ) : (
        hint && <p className="text-xs text-muted">{hint}</p>
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
    <nav aria-label="Pagination" className="mt-4 flex items-center justify-end gap-3 text-sm">
      <span className="text-muted tabular-nums">
        Page {page} sur {pageCount}
      </span>
      <Button variant="secondary" size="sm" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        Précédente
      </Button>
      <Button variant="secondary" size="sm" disabled={page >= pageCount} onClick={() => onChange(page + 1)}>
        Suivante
      </Button>
    </nav>
  );
}
