import { Perfil } from '../../core/auth/auth.models';

export interface ComentarioChamado {
  readonly id: number;
  readonly conteudo: string;
  readonly autor: { readonly id: number; readonly nome: string; readonly perfil: Perfil };
  readonly criadoEm: string;
}

export interface ComentariosChamado {
  readonly content: readonly ComentarioChamado[];
  readonly podeComentar: boolean;
}
