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

import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.repositorios.UsuarioRepositorio;
import com.autobots.automanager.repositorios.EmpresaRepositorio;

/**
 * Associa e desassocia usuários já cadastrados a uma empresa.
 * Os usuários em si são gerenciados em /usuario.
 */
@RestController
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Transactional
@RequestMapping("/empresa/{empresaId}/usuario")
public class EmpresaUsuarioControle {
	private final EmpresaRepositorio repositorio;
	private final UsuarioRepositorio filhoRepositorio;

	public EmpresaUsuarioControle(EmpresaRepositorio repositorio, UsuarioRepositorio filhoRepositorio) {
		this.repositorio = repositorio;
		this.filhoRepositorio = filhoRepositorio;
	}

	private Empresa obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
	}

	private CollectionModel<EntityModel<Usuario>> montarColecao(long donoId, Empresa dono) {
		List<EntityModel<Usuario>> modelos = new ArrayList<>();
		for (Usuario item : dono.getUsuarios()) {
			Link self = linkTo(methodOn(UsuarioControle.class).obterUsuario(item.getId())).withSelfRel();
			modelos.add(EntityModel.of(item, self));
		}
		Link self = linkTo(methodOn(EmpresaUsuarioControle.class).obterUsuarios(donoId)).withSelfRel();
		Link donoLink = linkTo(methodOn(EmpresaControle.class).obterEmpresa(donoId))
				.withRel("empresa");
		return CollectionModel.of(modelos, self, donoLink);
	}

	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Usuario>>> obterUsuarios(
			@PathVariable long empresaId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}

	@PutMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Usuario>>> associarUsuario(
			@PathVariable long empresaId, @PathVariable long filhoId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		Usuario filho = filhoRepositorio.findById(filhoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
		for (Empresa outro : repositorio.buscarPorUsuario(filhoId)) {
			if (!outro.getId().equals(dono.getId())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Usuário já está associado a outra empresa");
			}
		}
		boolean presente = false;
		for (Usuario item : dono.getUsuarios()) {
			if (filho.getId().equals(item.getId())) {
				presente = true;
			}
		}
		if (!presente) {
			dono.getUsuarios().add(filho);
			repositorio.saveAndFlush(dono);
		}
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}

	@DeleteMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Usuario>>> desassociarUsuario(
			@PathVariable long empresaId, @PathVariable long filhoId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		boolean removido = dono.getUsuarios().removeIf(item -> item.getId() != null && item.getId() == filhoId);
		if (!removido) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não está associado a esta empresa");
		}
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}
}
