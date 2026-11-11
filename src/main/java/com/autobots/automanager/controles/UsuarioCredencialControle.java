
package com.autobots.automanager.controles;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Date;
import java.util.List;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.autobots.automanager.entidades.Credencial;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.modelo.CodificadorCredencial;
import com.autobots.automanager.modelo.CredencialAtualizador;
import com.autobots.automanager.repositorios.UsuarioRepositorio;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@RestController
@PreAuthorize("@acesso.podeGerenciarUsuario(#usuarioId)")
@Transactional
@RequestMapping("/usuario/{usuarioId}/credencial")
public class UsuarioCredencialControle {

    private final UsuarioRepositorio repositorio;
    private final CredencialAtualizador atualizador =
            new CredencialAtualizador();
    private final CodificadorCredencial codificador;

    @PersistenceContext
    private EntityManager entityManager;

    public UsuarioCredencialControle(UsuarioRepositorio repositorio,
            CodificadorCredencial codificador) {
        this.repositorio = repositorio;
        this.codificador = codificador;
    }

    private Usuario obterDonoOuFalhar(long donoId) {
        return repositorio.findById(donoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuário não encontrado"));
    }

    private Credencial buscar(Usuario dono, long id) {
        for (Credencial item : dono.getCredenciais()) {
            if (item.getId() != null
                    && item.getId().longValue() == id) {
                return item;
            }
        }
        return null;
    }

    private EntityModel<Credencial> montarModelo(
            long donoId, Credencial item) {

        if (item.getId() == null) {
            throw new IllegalStateException(
                    "A credencial persistida não possui ID.");
        }

        long id = item.getId().longValue();

        Link self = linkTo(methodOn(
                UsuarioCredencialControle.class)
                .obterCredencial(donoId, id))
                .withSelfRel();

        Link lista = linkTo(methodOn(
                UsuarioCredencialControle.class)
                .obterCredenciais(donoId))
                .withRel("credenciais");

        Link dono = linkTo(methodOn(
                UsuarioControle.class)
                .obterUsuario(donoId))
                .withRel("usuario");

        return EntityModel.of(item, self, lista, dono);
    }

    @PreAuthorize("@acesso.podeLerUsuario(#usuarioId)")
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Credencial>> obterCredencial(
            @PathVariable long usuarioId,
            @PathVariable long id) {

        Usuario dono = obterDonoOuFalhar(usuarioId);
        Credencial item = buscar(dono, id);

        if (item == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(montarModelo(usuarioId, item));
    }

    @PreAuthorize("@acesso.podeLerUsuario(#usuarioId)")
    @GetMapping("/lista")
    public ResponseEntity<CollectionModel<EntityModel<Credencial>>>
            obterCredenciais(@PathVariable long usuarioId) {

        Usuario dono = obterDonoOuFalhar(usuarioId);

        List<EntityModel<Credencial>> modelos =
                dono.getCredenciais()
                        .stream()
                        .map(item -> montarModelo(usuarioId, item))
                        .toList();

        Link self = linkTo(methodOn(
                UsuarioCredencialControle.class)
                .obterCredenciais(usuarioId))
                .withSelfRel();

        Link donoLink = linkTo(methodOn(
                UsuarioControle.class)
                .obterUsuario(usuarioId))
                .withRel("usuario");

        return ResponseEntity.ok(
                CollectionModel.of(modelos, self, donoLink));
    }

    @PostMapping("/cadastro")
    public ResponseEntity<EntityModel<Credencial>> cadastrarCredencial(
            @PathVariable long usuarioId,
            @RequestBody Credencial credencial) {

        Usuario dono = obterDonoOuFalhar(usuarioId);

        credencial.setId(null);
        codificador.codificar(credencial);

        if (credencial.getCriacao() == null) {
            credencial.setCriacao(new Date());
        }

        if (credencial.getInativo() == null) {
            credencial.setInativo(false);
        }

        /*
         * Persiste a entidade e obtém o ID gerado.
         * O IDENTITY exige que o Hibernate execute o INSERT.
         */
        dono.getCredenciais().add(credencial);

        repositorio.saveAndFlush(dono);
        entityManager.flush();

        Long idGerado = credencial.getId();

        /*
         * Caso o ID ainda não esteja disponível na instância,
         * recarrega o usuário e procura a credencial persistida.
         */
        if (idGerado == null) {
            entityManager.refresh(dono);

            idGerado = dono.getCredenciais()
                    .stream()
                    .filter(item -> item.getClass()
                            .equals(credencial.getClass()))
                    .filter(item -> item.getId() != null)
                    .map(Credencial::getId)
                    .max(Long::compareTo)
                    .orElse(null);
        }

        if (idGerado == null) {
            throw new IllegalStateException(
                    "Não foi possível obter o ID da credencial. "
                    + "Verifique o mapeamento JPA e a geração "
                    + "de IDs da entidade Credencial.");
        }

        /*
         * Limpa o contexto de persistência e recupera o usuário
         * do banco, garantindo que as credenciais sejam relidas.
         */
        entityManager.flush();
        entityManager.clear();

        Usuario usuarioAtualizado = obterDonoOuFalhar(usuarioId);

        Credencial persistida = buscar(
                usuarioAtualizado, idGerado.longValue());

        if (persistida == null) {
            throw new IllegalStateException(
                    "A credencial foi inserida, mas não foi "
                    + "encontrada na coleção do usuário. "
                    + "Verifique Usuario.credenciais.");
        }

        EntityModel<Credencial> modelo =
                montarModelo(usuarioId, persistida);

        return ResponseEntity.status(HttpStatus.CREATED)
                .location(modelo.getRequiredLink(
                        IanaLinkRelations.SELF).toUri())
                .body(modelo);
    }

    @PutMapping("/atualizar")
    public ResponseEntity<EntityModel<Credencial>>
            atualizarCredencial(
                    @PathVariable long usuarioId,
                    @RequestBody Credencial atualizacao) {

        if (atualizacao.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe o id da credencial a atualizar");
        }

        Usuario dono = obterDonoOuFalhar(usuarioId);

        Credencial item = buscar(
                dono, atualizacao.getId().longValue());

        if (item == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Credencial não encontrada para este usuário");
        }

        if (!atualizador.mesmoTipo(item, atualizacao)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O tipo de uma credencial existente "
                    + "não pode ser alterado");
        }

        codificador.codificar(atualizacao);
        atualizador.atualizar(item, atualizacao);

        repositorio.saveAndFlush(dono);

        return ResponseEntity.ok(
                montarModelo(usuarioId, item));
    }

    @DeleteMapping("/excluir/{id}")
    public ResponseEntity<Void> excluirCredencial(
            @PathVariable long usuarioId,
            @PathVariable long id) {

        Usuario dono = obterDonoOuFalhar(usuarioId);

        boolean removido = dono.getCredenciais()
                .removeIf(item ->
                        item.getId() != null
                        && item.getId().longValue() == id);

        if (!removido) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Credencial não encontrada para este usuário");
        }

        repositorio.saveAndFlush(dono);

        return ResponseEntity.noContent().build();
    }
}
