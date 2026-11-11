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

import com.autobots.automanager.entidades.Servico;
import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.repositorios.ServicoRepositorio;
import com.autobots.automanager.repositorios.EmpresaRepositorio;

/**
 * Associa e desassocia serviços já cadastrados a uma empresa.
 * Os serviços em si são gerenciados em /servico.
 */
@RestController
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Transactional
@RequestMapping("/empresa/{empresaId}/servico")
public class EmpresaServicoControle {
	private final EmpresaRepositorio repositorio;
	private final ServicoRepositorio filhoRepositorio;

	public EmpresaServicoControle(EmpresaRepositorio repositorio, ServicoRepositorio filhoRepositorio) {
		this.repositorio = repositorio;
		this.filhoRepositorio = filhoRepositorio;
	}

	private Empresa obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
	}

	private CollectionModel<EntityModel<Servico>> montarColecao(long donoId, Empresa dono) {
		List<EntityModel<Servico>> modelos = new ArrayList<>();
		for (Servico item : dono.getServicos()) {
			Link self = linkTo(methodOn(ServicoControle.class).obterServico(item.getId())).withSelfRel();
			modelos.add(EntityModel.of(item, self));
		}
		Link self = linkTo(methodOn(EmpresaServicoControle.class).obterServicos(donoId)).withSelfRel();
		Link donoLink = linkTo(methodOn(EmpresaControle.class).obterEmpresa(donoId))
				.withRel("empresa");
		return CollectionModel.of(modelos, self, donoLink);
	}

	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Servico>>> obterServicos(
			@PathVariable long empresaId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}

	@PutMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Servico>>> associarServico(
			@PathVariable long empresaId, @PathVariable long filhoId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		Servico filho = filhoRepositorio.findById(filhoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
		for (Empresa outro : repositorio.buscarPorServico(filhoId)) {
			if (!outro.getId().equals(dono.getId())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Serviço já está associado a outra empresa");
			}
		}
		boolean presente = false;
		for (Servico item : dono.getServicos()) {
			if (filho.getId().equals(item.getId())) {
				presente = true;
			}
		}
		if (!presente) {
			dono.getServicos().add(filho);
			repositorio.saveAndFlush(dono);
		}
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}

	@DeleteMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Servico>>> desassociarServico(
			@PathVariable long empresaId, @PathVariable long filhoId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		boolean removido = dono.getServicos().removeIf(item -> item.getId() != null && item.getId() == filhoId);
		if (!removido) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não está associado a esta empresa");
		}
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}
}
