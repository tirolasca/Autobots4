package com.autobots.automanager.controles;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.ArrayList;
import java.util.List;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.autobots.automanager.entidades.Mercadoria;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.repositorios.MercadoriaRepositorio;
import com.autobots.automanager.repositorios.UsuarioRepositorio;

/**
 * Associa e desassocia mercadorias já cadastrados a um usuário.
 * Os mercadorias em si são gerenciados em /mercadoria.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
@Transactional
@RequestMapping("/usuario/{usuarioId}/mercadoria")
public class UsuarioMercadoriaControle {
	private final UsuarioRepositorio repositorio;
	private final MercadoriaRepositorio filhoRepositorio;

	public UsuarioMercadoriaControle(UsuarioRepositorio repositorio, MercadoriaRepositorio filhoRepositorio) {
		this.repositorio = repositorio;
		this.filhoRepositorio = filhoRepositorio;
	}

	private Usuario obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
	}

	private CollectionModel<EntityModel<Mercadoria>> montarColecao(long donoId, Usuario dono) {
		List<EntityModel<Mercadoria>> modelos = new ArrayList<>();
		for (Mercadoria item : dono.getMercadorias()) {
			Link self = linkTo(methodOn(MercadoriaControle.class).obterMercadoria(item.getId())).withSelfRel();
			modelos.add(EntityModel.of(item, self));
		}
		Link self = linkTo(methodOn(UsuarioMercadoriaControle.class).obterMercadorias(donoId)).withSelfRel();
		Link donoLink = linkTo(methodOn(UsuarioControle.class).obterUsuario(donoId))
				.withRel("usuario");
		return CollectionModel.of(modelos, self, donoLink);
	}

	@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR')")
	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Mercadoria>>> obterMercadorias(
			@PathVariable long usuarioId) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		return ResponseEntity.ok(montarColecao(usuarioId, dono));
	}

	@PutMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Mercadoria>>> associarMercadoria(
			@PathVariable long usuarioId, @PathVariable long filhoId) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		Mercadoria filho = filhoRepositorio.findById(filhoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mercadoria não encontrada"));
		for (Usuario outro : repositorio.buscarPorMercadoria(filhoId)) {
			if (!outro.getId().equals(dono.getId())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Mercadoria já é fornecida por outro usuário");
			}
		}
		boolean presente = false;
		for (Mercadoria item : dono.getMercadorias()) {
			if (filho.getId().equals(item.getId())) {
				presente = true;
			}
		}
		if (!presente) {
			dono.getMercadorias().add(filho);
			repositorio.saveAndFlush(dono);
		}
		return ResponseEntity.ok(montarColecao(usuarioId, dono));
	}

	@DeleteMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Mercadoria>>> desassociarMercadoria(
			@PathVariable long usuarioId, @PathVariable long filhoId) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		boolean removido = dono.getMercadorias().removeIf(item -> item.getId() != null && item.getId() == filhoId);
		if (!removido) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mercadoria não é fornecida por este usuário");
		}
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarColecao(usuarioId, dono));
	}
}
