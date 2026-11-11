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

import com.autobots.automanager.entidades.Venda;
import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.repositorios.VendaRepositorio;
import com.autobots.automanager.repositorios.EmpresaRepositorio;

/**
 * Associa e desassocia vendas já cadastrados a uma empresa.
 * Os vendas em si são gerenciados em /venda.
 */
@RestController
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Transactional
@RequestMapping("/empresa/{empresaId}/venda")
public class EmpresaVendaControle {
	private final EmpresaRepositorio repositorio;
	private final VendaRepositorio filhoRepositorio;

	public EmpresaVendaControle(EmpresaRepositorio repositorio, VendaRepositorio filhoRepositorio) {
		this.repositorio = repositorio;
		this.filhoRepositorio = filhoRepositorio;
	}

	private Empresa obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
	}

	private CollectionModel<EntityModel<Venda>> montarColecao(long donoId, Empresa dono) {
		List<EntityModel<Venda>> modelos = new ArrayList<>();
		for (Venda item : dono.getVendas()) {
			Link self = linkTo(methodOn(VendaControle.class).obterVenda(item.getId())).withSelfRel();
			modelos.add(EntityModel.of(item, self));
		}
		Link self = linkTo(methodOn(EmpresaVendaControle.class).obterVendas(donoId)).withSelfRel();
		Link donoLink = linkTo(methodOn(EmpresaControle.class).obterEmpresa(donoId))
				.withRel("empresa");
		return CollectionModel.of(modelos, self, donoLink);
	}

	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Venda>>> obterVendas(
			@PathVariable long empresaId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}

	@PutMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Venda>>> associarVenda(
			@PathVariable long empresaId, @PathVariable long filhoId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		Venda filho = filhoRepositorio.findById(filhoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venda não encontrada"));
		for (Empresa outro : repositorio.buscarPorVenda(filhoId)) {
			if (!outro.getId().equals(dono.getId())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Venda já está associada a outra empresa");
			}
		}
		boolean presente = false;
		for (Venda item : dono.getVendas()) {
			if (filho.getId().equals(item.getId())) {
				presente = true;
			}
		}
		if (!presente) {
			dono.getVendas().add(filho);
			repositorio.saveAndFlush(dono);
		}
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}

	@DeleteMapping("/{filhoId}")
	public ResponseEntity<CollectionModel<EntityModel<Venda>>> desassociarVenda(
			@PathVariable long empresaId, @PathVariable long filhoId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		boolean removido = dono.getVendas().removeIf(item -> item.getId() != null && item.getId() == filhoId);
		if (!removido) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venda não está associada a esta empresa");
		}
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarColecao(empresaId, dono));
	}
}
