package br.edu.impacta.resolveai.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

class RepositoryContractTest {

    @Test
    void repositoriesShouldExposeOnlyExplicitSaveAndFindOperations() {
        List<Class<?>> repositories = List.of(
                UsuarioRepository.class,
                CategoriaRepository.class,
                ChamadoRepository.class);

        repositories.forEach(repository -> {
            assertThat(repository.getInterfaces()).containsExactly(Repository.class);
            assertThat(CrudRepository.class.isAssignableFrom(repository)).isFalse();
            assertThat(repository.getMethods())
                    .extracting(Method::getName)
                    .allMatch(name -> name.equals("save") || name.startsWith("find"))
                    .noneMatch(name -> name.startsWith("delete"));
        });
    }
}
