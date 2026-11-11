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
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.modelo.EnderecoAtualizador;
import com.autobots.automanager.repositorios.UsuarioRepositorio;

/**
 * O endereço tem relação OneToOne com usuário: o CRUD é feito sobre o
 * endereço único, sem lista nem seleção por id.
 */
@RestController
@PreAuthorize("@acesso.podeGerenciarUsuario(#usuarioId)")
@Transactional
@RequestMapping("/usuario/{usuarioId}/endereco")
public class UsuarioEnderecoControle {
	private final UsuarioRepositorio repositorio;
	private final EnderecoAtualizador atualizador = new EnderecoAtualizador();

	public UsuarioEnderecoControle(UsuarioRepositorio repositorio) {
		this.repositorio = repositorio;
	}

	private Usuario obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
	}

	private EntityModel<Endereco> montarModelo(long donoId, Endereco endereco) {
		Link self = linkTo(methodOn(UsuarioEnderecoControle.class).obterEndereco(donoId)).withSelfRel();
		Link dono = linkTo(methodOn(UsuarioControle.class).obterUsuario(donoId)).withRel("usuario");
		return EntityModel.of(endereco, self, dono);
	}

	@PreAuthorize("@acesso.podeLerUsuario(#usuarioId)")
	@GetMapping
	public ResponseEntity<EntityModel<Endereco>> obterEndereco(@PathVariable long usuarioId) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		if (dono.getEndereco() == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(usuarioId, dono.getEndereco()));
	}

	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Endereco>> cadastrarEndereco(@PathVariable long usuarioId,
			@RequestBody Endereco endereco) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		if (dono.getEndereco() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Usuário já possui endereço cadastrado; use /atualizar");
		}
		endereco.setId(null);
		dono.setEndereco(endereco);
		repositorio.saveAndFlush(dono);
		EntityModel<Endereco> modelo = montarModelo(usuarioId, endereco);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Endereco>> atualizarEndereco(@PathVariable long usuarioId,
			@RequestBody Endereco atualizacao) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		if (dono.getEndereco() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não possui endereço cadastrado");
		}
		atualizador.atualizar(dono.getEndereco(), atualizacao);
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarModelo(usuarioId, dono.getEndereco()));
	}

	@DeleteMapping("/excluir")
	public ResponseEntity<Void> excluirEndereco(@PathVariable long usuarioId) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		if (dono.getEndereco() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não possui endereço cadastrado");
		}
		dono.setEndereco(null);
		repositorio.saveAndFlush(dono);
		return ResponseEntity.noContent().build();
	}
}
