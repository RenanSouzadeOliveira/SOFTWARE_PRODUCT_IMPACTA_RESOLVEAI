package br.edu.impacta.resolveai.shared.error;

import java.util.List;

public class DadosInvalidosException extends RuntimeException {
    private final List<FieldViolation> violations;

    public DadosInvalidosException(String message) {
        this(message, List.of());
    }

    public DadosInvalidosException(String message, List<FieldViolation> violations) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() { return violations; }
}
