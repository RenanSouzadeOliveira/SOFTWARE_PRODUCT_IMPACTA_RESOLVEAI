import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Perfil, Usuario } from '../../core/auth/auth.models';
import { ApiService } from '../../core/http/api.service';

export interface UsuarioInput {
  readonly nome: string;
  readonly email: string;
  readonly perfil: Perfil;
}
export interface CategoriaInput {
  readonly nome: string;
  readonly descricao: string | null;
}
export interface CategoriaAdmin extends CategoriaInput {
  readonly id: number;
  readonly ativa: boolean;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly api = inject(ApiService);

  listarUsuarios(): Observable<readonly Usuario[]> {
    return this.api.get('admin/usuarios');
  }
  criarUsuario(input: UsuarioInput & { readonly senha: string }): Observable<Usuario> {
    return this.api.post('admin/usuarios', input);
  }
  editarUsuario(id: number, input: UsuarioInput): Observable<Usuario> {
    return this.api.patch(`admin/usuarios/${id}`, input);
  }
  situacaoUsuario(id: number, ativo: boolean): Observable<Usuario> {
    return this.api.patch(`admin/usuarios/${id}/situacao`, { ativo });
  }
  listarCategorias(): Observable<readonly CategoriaAdmin[]> {
    return this.api.get('admin/categorias');
  }
  criarCategoria(input: CategoriaInput): Observable<CategoriaAdmin> {
    return this.api.post('admin/categorias', input);
  }
  editarCategoria(id: number, input: CategoriaInput): Observable<CategoriaAdmin> {
    return this.api.patch(`admin/categorias/${id}`, input);
  }
  situacaoCategoria(id: number, ativa: boolean): Observable<CategoriaAdmin> {
    return this.api.patch(`admin/categorias/${id}/situacao`, { ativa });
  }
}
