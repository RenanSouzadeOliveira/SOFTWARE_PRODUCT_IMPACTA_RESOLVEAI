package br.edu.impacta.resolveai.chamado;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import br.edu.impacta.resolveai.shared.error.ConflitoException;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.FieldViolation;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import org.springframework.security.access.AccessDeniedException;

/** Regras compartilhadas pelo comando de atribuicao, ciclo de vida e acoes da interface. */
public final class MaquinaEstadosChamado {
    private MaquinaEstadosChamado() { }

    public static void validarAtribuicao(Chamado chamado, Usuario usuario) {
        if (!operador(usuario)) throw new AccessDeniedException("Atribuicao exige operador ativo");
        if (chamado.status() != StatusChamado.ABERTO || chamado.atendente() != null)
            throw new ConflitoException("Chamado nao esta disponivel para atribuicao");
    }

    public static String validarTransicao(Chamado chamado, Usuario usuario, StatusChamado novo, String justificativa) {
        validarParticipante(chamado, usuario);
        if (novo == null) throw new DadosInvalidosException("Status obrigatorio",
                List.of(new FieldViolation("status", "campo obrigatorio")));
        boolean resolver = chamado.status() == StatusChamado.EM_ATENDIMENTO && novo == StatusChamado.RESOLVIDO;
        boolean reabrir = chamado.status() == StatusChamado.RESOLVIDO && novo == StatusChamado.EM_ATENDIMENTO;
        boolean fechar = chamado.status() == StatusChamado.RESOLVIDO && novo == StatusChamado.FECHADO;
        if (!resolver && !reabrir && !fechar)
            throw new ConflitoException("Transicao de status nao permitida; atribuicao exige a operacao assumir");
        if (resolver ? !podeAtender(chamado, usuario) : !podeConfirmar(chamado, usuario))
            throw new AccessDeniedException("Usuario sem permissao para esta transicao");
        String texto = justificativa == null ? null : justificativa.trim();
        if ((resolver || reabrir) && (texto == null || texto.length() < 10 || texto.length() > 500))
            throw new DadosInvalidosException("Justificativa invalida", List.of(
                    new FieldViolation("justificativa", "deve conter entre 10 e 500 caracteres apos remover espacos")));
        if (fechar && texto != null && texto.length() > 500)
            throw new DadosInvalidosException("Justificativa invalida", List.of(
                    new FieldViolation("justificativa", "deve conter no maximo 500 caracteres")));
        return texto == null || texto.isEmpty() ? null : texto;
    }

    public static void validarPrioridade(Chamado chamado, Usuario usuario, PrioridadeChamado nova) {
        validarParticipante(chamado, usuario);
        if (!podePriorizar(chamado, usuario)) {
            if (chamado.status() == StatusChamado.FECHADO)
                throw new ConflitoException("Chamado fechado nao pode ser alterado");
            throw new AccessDeniedException("Usuario sem permissao para alterar prioridade neste estado");
        }
        if (nova == null) throw new DadosInvalidosException("Prioridade obrigatoria", List.of(
                new FieldViolation("prioridade", "campo obrigatorio")));
        if (chamado.prioridade() == nova) throw new ConflitoException("Chamado ja possui esta prioridade");
    }

    public static List<AcaoChamado> acoesPermitidas(Chamado chamado, Usuario usuario) {
        List<AcaoChamado> acoes = new ArrayList<>();
        if (!usuario.ativo()) return List.of();
        if (chamado.status() == StatusChamado.EM_ATENDIMENTO && podeAtender(chamado, usuario))
            acoes.add(AcaoChamado.RESOLVER);
        if (chamado.status() == StatusChamado.RESOLVIDO && podeConfirmar(chamado, usuario)) {
            acoes.add(AcaoChamado.REABRIR);
            acoes.add(AcaoChamado.FECHAR);
        }
        if (podePriorizar(chamado, usuario)) acoes.add(AcaoChamado.ALTERAR_PRIORIDADE);
        return List.copyOf(acoes);
    }

    public static boolean podeConsultar(Chamado chamado, Usuario usuario) {
        return usuario.ativo() && (admin(usuario)
                || usuario.perfil() == Perfil.SOLICITANTE && mesmo(chamado.solicitante(), usuario)
                || usuario.perfil() == Perfil.ATENDENTE && mesmo(chamado.atendente(), usuario));
    }

    private static void validarParticipante(Chamado chamado, Usuario usuario) {
        if (!podeConsultar(chamado, usuario)) throw new AccessDeniedException("Usuario nao participa deste chamado");
    }
    private static boolean podeAtender(Chamado c, Usuario u) {
        return u.ativo() && (admin(u) || u.perfil() == Perfil.ATENDENTE && mesmo(c.atendente(), u));
    }
    private static boolean podeConfirmar(Chamado c, Usuario u) {
        return u.ativo() && (admin(u) || u.perfil() == Perfil.SOLICITANTE && mesmo(c.solicitante(), u));
    }
    private static boolean podePriorizar(Chamado c, Usuario u) {
        return c.status() != StatusChamado.FECHADO && u.ativo()
                && (admin(u) || c.status() == StatusChamado.EM_ATENDIMENTO && podeAtender(c, u));
    }
    private static boolean mesmo(Usuario a, Usuario b) {
        return a != null && (a == b || a.id() != null && Objects.equals(a.id(), b.id()));
    }
    private static boolean admin(Usuario u) { return u.perfil() == Perfil.ADMIN; }
    private static boolean operador(Usuario u) {
        return u.ativo() && (u.perfil() == Perfil.ATENDENTE || admin(u));
    }
}
