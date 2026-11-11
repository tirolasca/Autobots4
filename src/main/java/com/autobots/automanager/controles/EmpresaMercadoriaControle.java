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
import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.repositorios.MercadoriaRepositorio;
import com.autobots.automanager.repositorios.EmpresaRepositorio;

/**
 * Associa e desassocia mercadorias já cadastrados a uma empresa.
 * Os mercadorias em si são gerenciados em /mercadoria.
 */
@RestController
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Transactional
@RequestMapping("/empresa/{empresaId}/mercadoria")
public class EmpresaMercadoriaControle {
	private final EmpresaRepositorio repositorio;
	private final MercadoriaRepositorio filhoRepositorio;

	public EmpresaMercadoriaControle(EmpresaRepositorio repositorio, MercadoriaRepositorio filhoRepositorio) {
		this.repositorio = repositorio;
		this.filhoRepositorio = filhoRepositorio;
	}

	private Empresa obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
	}

	private CollectionModel<EntityModel<Mercadoria>> montarColecao(long donoId, Empresa dono) {
		List<EntityModel<Mercadoria>> modelos = new ArrayList<>();
		for (Mercadoria item : dono.getMercadorias()) {
			Link self = linkTo(methodOn(MercadoriaControle.class).obterMercadoria(item.getId())).withSelfRel();
			modelos.add(EntityModel.of(item, self));
		}
		Link self = linkTo(methodOn(EmpresaMercadoriaControle.class).obterMercadorias(donoId)).withSelfRel();
		Link donoLink = linkTo(methodOn(EmpresaControle.class).obterEmpresa(donoId))
				.withRel("empresa");
		return CollectionModel.of(modelos, self, donoLink);
	}

	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Mercadoria>>> obterMercadorias(
			@PathVariable long empresaId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}

	@PutMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Mercadoria>>> associarMercadoria(
			@PathVariable long empresaId, @PathVariable long filhoId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		Mercadoria filho = filhoRepositorio.findById(filhoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mercadoria não encontrada"));
		for (Empresa outro : repositorio.buscarPorMercadoria(filhoId)) {
			if (!outro.getId().equals(dono.getId())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Mercadoria já está associada a outra empresa");
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
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}

	@DeleteMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Mercadoria>>> desassociarMercadoria(
			@PathVariable long empresaId, @PathVariable long filhoId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		boolean removido = dono.getMercadorias().removeIf(item -> item.getId() != null && item.getId() == filhoId);
		if (!removido) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mercadoria não está associada a esta empresa");
		}
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}
}
