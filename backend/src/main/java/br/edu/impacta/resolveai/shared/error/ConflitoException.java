package br.edu.impacta.resolveai.shared.error;

import java.util.List;

public class ConflitoException extends RuntimeException {

    private final List<FieldViolation> violations;

    public ConflitoException(String message) {
        this(message, List.of());
    }

    public ConflitoException(String message, List<FieldViolation> violations) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
