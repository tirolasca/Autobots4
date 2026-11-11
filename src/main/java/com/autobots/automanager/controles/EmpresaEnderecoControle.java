package com.autobots.automanager.controles;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

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

import com.autobots.automanager.entidades.Endereco;
import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.modelo.EnderecoAtualizador;
import com.autobots.automanager.repositorios.EmpresaRepositorio;

/**
 * O endereço tem relação OneToOne com empresa: o CRUD é feito sobre o
 * endereço único, sem lista nem seleção por id.
 */
@RestController
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Transactional
@RequestMapping("/empresa/{empresaId}/endereco")
public class EmpresaEnderecoControle {
	private final EmpresaRepositorio repositorio;
	private final EnderecoAtualizador atualizador = new EnderecoAtualizador();

	public EmpresaEnderecoControle(EmpresaRepositorio repositorio) {
		this.repositorio = repositorio;
	}

	private Empresa obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
	}

	private EntityModel<Endereco> montarModelo(long donoId, Endereco endereco) {
		Link self = linkTo(methodOn(EmpresaEnderecoControle.class).obterEndereco(donoId)).withSelfRel();
		Link dono = linkTo(methodOn(EmpresaControle.class).obterEmpresa(donoId)).withRel("empresa");
		return EntityModel.of(endereco, self, dono);
	}

	@GetMapping
	public ResponseEntity<EntityModel<Endereco>> obterEndereco(@PathVariable long empresaId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		if (dono.getEndereco() == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(empresaId, dono.getEndereco()));
	}

	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Endereco>> cadastrarEndereco(@PathVariable long empresaId,
			@RequestBody Endereco endereco) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		if (dono.getEndereco() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Empresa já possui endereço cadastrado; use /atualizar");
		}
		endereco.setId(null);
		dono.setEndereco(endereco);
		repositorio.saveAndFlush(dono);
		EntityModel<Endereco> modelo = montarModelo(empresaId, endereco);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Endereco>> atualizarEndereco(@PathVariable long empresaId,
			@RequestBody Endereco atualizacao) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		if (dono.getEndereco() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não possui endereço cadastrado");
		}
		atualizador.atualizar(dono.getEndereco(), atualizacao);
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarModelo(empresaId, dono.getEndereco()));
	}

	@DeleteMapping("/excluir")
	public ResponseEntity<Void> excluirEndereco(@PathVariable long empresaId) {
		Empresa dono = obterDonoOuFalhar(empresaId);
		if (dono.getEndereco() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não possui endereço cadastrado");
		}
		dono.setEndereco(null);
		repositorio.saveAndFlush(dono);
		return ResponseEntity.noContent().build();
	}
}
