export type Perfil = 'SOLICITANTE' | 'ATENDENTE' | 'ADMIN';

export interface Usuario {
  readonly id: number;
  readonly nome: string;
  readonly email: string;
  readonly perfil: Perfil;
  readonly ativo: boolean;
  readonly criadoEm?: string;
  readonly atualizadoEm?: string;
  readonly versao?: number;
}

export interface CadastroInput {
  readonly nome: string;
  readonly email: string;
  readonly senha: string;
}

export interface LoginInput {
  readonly email: string;
  readonly senha: string;
}

export interface LoginResponse {
  readonly accessToken: string;
  readonly tokenType: 'Bearer';
  readonly expiresIn: number;
  readonly usuario: Usuario;
}

export interface AtualizacaoMinhaContaInput {
  readonly nome: string;
}

export interface AuthSession extends LoginResponse {
  readonly expiresAt: number;
}
