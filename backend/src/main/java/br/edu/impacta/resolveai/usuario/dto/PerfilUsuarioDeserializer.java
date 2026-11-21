package br.edu.impacta.resolveai.usuario.dto;

import java.io.IOException;

import br.edu.impacta.resolveai.usuario.Perfil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/** Impede interpretar índices numéricos como perfis administrativos. */
public class PerfilUsuarioDeserializer extends StdDeserializer<Perfil> {
    public PerfilUsuarioDeserializer() {
        super(Perfil.class);
    }

    @Override
    public Perfil deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (Perfil) context.handleUnexpectedToken(Perfil.class, parser);
        }
        String valor = parser.getText();
        try {
            return Perfil.valueOf(valor);
        } catch (IllegalArgumentException exception) {
            throw context.weirdStringException(valor, Perfil.class, "Perfil inválido");
        }
    }
}
