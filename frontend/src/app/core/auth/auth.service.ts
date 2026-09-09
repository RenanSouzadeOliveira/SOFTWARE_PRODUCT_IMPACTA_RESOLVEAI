import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';
import { map, Observable, tap } from 'rxjs';

import { ApiService } from '../http/api.service';
import {
  AtualizacaoMinhaContaInput,
  AuthSession,
  CadastroInput,
  LoginInput,
  LoginResponse,
  Perfil,
  Usuario,
} from './auth.models';

const SESSION_KEY = 'resolveai.auth.session';
const CLOCK_SKEW_MS = 5_000;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly document = inject(DOCUMENT);
  private readonly sessionState = signal<AuthSession | null>(this.restoreSession());

  readonly session = this.sessionState.asReadonly();
  readonly currentUser = computed(() => this.sessionState()?.usuario ?? null);
  readonly isAuthenticated = (): boolean => this.getAccessToken() !== null;

  register(input: CadastroInput): Observable<Usuario> {
    return this.api.post<Usuario, CadastroInput>('auth/register', {
      nome: input.nome.trim(),
      email: input.email.trim().toLowerCase(),
      senha: input.senha,
    });
  }

  login(input: LoginInput): Observable<LoginResponse> {
    return this.api
      .post<LoginResponse, LoginInput>('auth/login', {
        email: input.email.trim().toLowerCase(),
        senha: input.senha,
      })
      .pipe(
        map((response) => this.createSession(response)),
        tap((session) => this.persistSession(session)),
        map(({ expiresAt: _expiresAt, ...response }) => response),
      );
  }

  loadCurrentUser(): Observable<Usuario> {
    return this.api.get<Usuario>('usuarios/me').pipe(tap((usuario) => this.updateUser(usuario)));
  }

  updateCurrentUser(input: AtualizacaoMinhaContaInput): Observable<Usuario> {
    return this.api
      .patch<Usuario, AtualizacaoMinhaContaInput>('usuarios/me', { nome: input.nome.trim() })
      .pipe(tap((usuario) => this.updateUser(usuario)));
  }

  getAccessToken(): string | null {
    const session = this.sessionState();
    if (!session) {
      return null;
    }

    if (!this.isSessionValid(session)) {
      this.logout();
      return null;
    }

    return session.accessToken;
  }

  hasRole(role: Perfil): boolean {
    return this.getAccessToken() !== null && this.currentUser()?.perfil === role;
  }

  logout(): void {
    this.sessionState.set(null);
    this.storage()?.removeItem(SESSION_KEY);
  }

  private createSession(response: LoginResponse): AuthSession {
    const jwtExpiration = this.readJwtExpiration(response.accessToken);
    const expiresIn = Number(response.expiresIn);

    if (
      response.tokenType !== 'Bearer' ||
      !Number.isFinite(expiresIn) ||
      expiresIn <= 0 ||
      !jwtExpiration ||
      jwtExpiration <= Date.now() + CLOCK_SKEW_MS
    ) {
      return throwInvalidSession();
    }

    return {
      ...response,
      accessToken: response.accessToken.trim(),
      usuario: normalizeUser(response.usuario),
      expiresAt: jwtExpiration,
    };
  }

  private persistSession(session: AuthSession): void {
    this.storage()?.setItem(SESSION_KEY, JSON.stringify(session));
    this.sessionState.set(session);
  }

  private updateUser(usuario: Usuario): void {
    const session = this.sessionState();
    if (!session) {
      return;
    }

    this.persistSession({ ...session, usuario: normalizeUser(usuario) });
  }

  private restoreSession(): AuthSession | null {
    const rawSession = this.storage()?.getItem(SESSION_KEY);
    if (!rawSession) {
      return null;
    }

    try {
      const candidate: unknown = JSON.parse(rawSession);
      if (!isAuthSession(candidate) || !this.isSessionValid(candidate)) {
        this.storage()?.removeItem(SESSION_KEY);
        return null;
      }
      return { ...candidate, usuario: normalizeUser(candidate.usuario) };
    } catch {
      this.storage()?.removeItem(SESSION_KEY);
      return null;
    }
  }

  private isSessionValid(session: AuthSession): boolean {
    const jwtExpiration = this.readJwtExpiration(session.accessToken);
    return jwtExpiration !== null && jwtExpiration > Date.now() + CLOCK_SKEW_MS;
  }

  private readJwtExpiration(token: string): number | null {
    const payloadPart = token.split('.')[1];
    if (!payloadPart) {
      return null;
    }

    try {
      const normalized = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
      const padding = '='.repeat((4 - (normalized.length % 4)) % 4);
      const decoded: unknown = JSON.parse(this.document.defaultView?.atob(normalized + padding) ?? '');
      if (!isRecord(decoded) || typeof decoded['exp'] !== 'number') {
        return null;
      }
      return decoded['exp'] * 1_000;
    } catch {
      return null;
    }
  }

  private storage(): Storage | null {
    return this.document.defaultView?.sessionStorage ?? null;
  }
}

function normalizeUser(usuario: Usuario): Usuario {
  return {
    ...usuario,
    nome: usuario.nome.trim(),
    email: usuario.email.trim().toLowerCase(),
  };
}

function isAuthSession(value: unknown): value is AuthSession {
  if (!isRecord(value) || !isRecord(value['usuario'])) {
    return false;
  }

  const user = value['usuario'];
  return (
    typeof value['accessToken'] === 'string' &&
    value['tokenType'] === 'Bearer' &&
    typeof value['expiresIn'] === 'number' &&
    typeof value['expiresAt'] === 'number' &&
    typeof user['id'] === 'number' &&
    typeof user['nome'] === 'string' &&
    typeof user['email'] === 'string' &&
    isPerfil(user['perfil']) &&
    typeof user['ativo'] === 'boolean'
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isPerfil(value: unknown): value is Perfil {
  return value === 'SOLICITANTE' || value === 'ATENDENTE' || value === 'ADMIN';
}

function throwInvalidSession(): never {
  throw new Error('A API retornou uma sessão inválida ou expirada.');
}
