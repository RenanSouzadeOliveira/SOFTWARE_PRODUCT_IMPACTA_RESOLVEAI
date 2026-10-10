package br.edu.impacta.resolveai.comentario.dto;

import java.util.List;

public record ComentariosResponse(List<ComentarioResponse> content, boolean podeComentar) { }
