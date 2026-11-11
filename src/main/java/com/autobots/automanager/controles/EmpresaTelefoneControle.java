package com.autobots.automanager.controles;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

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

import com.autobots.automanager.entidades.Telefone;
import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.modelo.TelefoneAtualizador;
import com.autobots.automanager.repositorios.EmpresaRepositorio;

@RestController
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Transactional
@RequestMapping("/empresa/{empresaId}/telefone")
public class EmpresaTelefoneControle {
	private final EmpresaRepositorio repositorio;
	private final TelefoneAtualizador atualizador = new TelefoneAtualizador();

	public EmpresaTelefoneControle(EmpresaRepositorio repositorio) {
		this.repositorio = repositorio;
	}

	private Empresa obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
	}

	private Telefone buscar(Empresa dono, long id) {
		for (Telefone item : dono.getTelefones()) {
			if (item.getId() != null && item.getId() == id) {
				return item;
			}
		}
		return null;
	}

	private EntityModel<Telefone> montarModelo(long donoId, Telefone item) {
		Link self = linkTo(methodOn(EmpresaTelefoneControle.class).obterTelefone(donoId, item.getId())).withSelfRel();
		Link lista = linkTo(methodOn(EmpresaTelefoneControle.class).obterTelefones(donoId)).withRel("telefones");
		Link dono = linkTo(methodOn(EmpresaControle.class).obterEmpresa(donoId)).withRel("empresa");
		return EntityModel.of(item, self, lista, dono);
	}

	@GetMapping("/{id}")
	public ResponseEntity<EntityModel<Telefone>> obterTelefone(@PathVariable long empresaId,
			@PathVariable long id) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		Telefone item = buscar(dono, id);
		if (item == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(empresaId, item));
	}

	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Telefone>>> obterTelefones(
			@PathVariable long empresaId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		List<EntityModel<Telefone>> modelos = dono.getTelefones().stream()
				.map(item -> montarModelo(empresaId, item)).toList();
		Link self = linkTo(methodOn(EmpresaTelefoneControle.class).obterTelefones(empresaId)).withSelfRel();
		Link donoLink = linkTo(methodOn(EmpresaControle.class).obterEmpresa(empresaId))
				.withRel("empresa");
		return ResponseEntity.ok(CollectionModel.of(modelos, self, donoLink));
	}

	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Telefone>> cadastrarTelefone(@PathVariable long empresaId,
			@RequestBody Telefone item) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		item.setId(null);
		dono.getTelefones().add(item);
		repositorio.saveAndFlush(dono);
		EntityModel<Telefone> modelo = montarModelo(empresaId, item);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Telefone>> atualizarTelefone(@PathVariable long empresaId,
			@RequestBody Telefone atualizacao) {
		if (atualizacao.getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o id do registro a atualizar");
		}
		Empresa dono = obterDonoOuFalhar(empresaId);
		Telefone item = buscar(dono, atualizacao.getId());
		if (item == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Telefone não encontrado para esta empresa");
		}
		atualizador.atualizar(item, atualizacao);
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarModelo(empresaId, item));
	}

	@DeleteMapping("/excluir/{id}")
	public ResponseEntity<Void> excluirTelefone(@PathVariable long empresaId, @PathVariable long id) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		boolean removido = dono.getTelefones().removeIf(item -> item.getId() != null && item.getId() == id);
		if (!removido) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Telefone não encontrado para esta empresa");
		}
		repositorio.saveAndFlush(dono);
		return ResponseEntity.noContent().build();
	}
}
