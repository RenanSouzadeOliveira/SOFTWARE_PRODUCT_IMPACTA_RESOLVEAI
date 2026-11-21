import { HttpErrorResponse } from '@angular/common/http';

export interface ApiFieldViolation {
  readonly field: string;
  readonly message: string;
}

/**
 * Mensagem segura para exibição. Falhas de rede e 5xx usam sempre o texto de fallback,
 * e corpos não estruturados (HTML de proxy, texto bruto) nunca são exibidos.
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse) || error.status === 0 || error.status >= 500) {
    return fallback;
  }

  const body: unknown = error.error;
  if (isRecord(body)) {
    const message = readString(body, 'message') ?? readString(body, 'mensagem');
    if (message) {
      return message;
    }
  }

  return fallback;
}

export function readFieldViolations(error: unknown): readonly ApiFieldViolation[] {
  if (!(error instanceof HttpErrorResponse) || !isRecord(error.error)) {
    return [];
  }

  const candidate = error.error['violations'] ?? error.error['fieldViolations'];
  if (!Array.isArray(candidate)) {
    return [];
  }

  return candidate.flatMap((item): ApiFieldViolation[] => {
    if (!isRecord(item)) {
      return [];
    }

    const field = readString(item, 'field') ?? readString(item, 'campo');
    const message = readString(item, 'message') ?? readString(item, 'mensagem');
    return field && message ? [{ field, message }] : [];
  });
}

function readString(record: Record<string, unknown>, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' && value.trim() ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
