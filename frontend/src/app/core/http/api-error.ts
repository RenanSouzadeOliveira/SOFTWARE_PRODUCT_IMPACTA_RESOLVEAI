import { HttpErrorResponse } from '@angular/common/http';

interface ApiErrorBody {
  readonly message?: unknown;
  readonly mensagem?: unknown;
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }

  const body = error.error as ApiErrorBody | string | null;
  if (typeof body === 'string' && body.trim()) {
    return body;
  }

  if (body && typeof body === 'object') {
    const message = typeof body.message === 'string' ? body.message : body.mensagem;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
  }

  return fallback;
}
