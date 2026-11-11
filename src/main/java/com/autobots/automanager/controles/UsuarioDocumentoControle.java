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

import com.autobots.automanager.entidades.Documento;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.modelo.DocumentoAtualizador;
import com.autobots.automanager.repositorios.UsuarioRepositorio;

@RestController
@PreAuthorize("@acesso.podeGerenciarUsuario(#usuarioId)")
@Transactional
@RequestMapping("/usuario/{usuarioId}/documento")
public class UsuarioDocumentoControle {
	private final UsuarioRepositorio repositorio;
	private final DocumentoAtualizador atualizador = new DocumentoAtualizador();

	public UsuarioDocumentoControle(UsuarioRepositorio repositorio) {
		this.repositorio = repositorio;
	}

	private Usuario obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
	}

	private Documento buscar(Usuario dono, long id) {
		for (Documento item : dono.getDocumentos()) {
			if (item.getId() != null && item.getId() == id) {
				return item;
			}
		}
		return null;
	}

	private EntityModel<Documento> montarModelo(long donoId, Documento item) {
		Link self = linkTo(methodOn(UsuarioDocumentoControle.class).obterDocumento(donoId, item.getId())).withSelfRel();
		Link lista = linkTo(methodOn(UsuarioDocumentoControle.class).obterDocumentos(donoId)).withRel("documentos");
		Link dono = linkTo(methodOn(UsuarioControle.class).obterUsuario(donoId)).withRel("usuario");
		return EntityModel.of(item, self, lista, dono);
	}

	@PreAuthorize("@acesso.podeLerUsuario(#usuarioId)")
	@GetMapping("/{id}")
	public ResponseEntity<EntityModel<Documento>> obterDocumento(@PathVariable long usuarioId,
			@PathVariable long id) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		Documento item = buscar(dono, id);
		if (item == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(usuarioId, item));
	}

	@PreAuthorize("@acesso.podeLerUsuario(#usuarioId)")
	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Documento>>> obterDocumentos(
			@PathVariable long usuarioId) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		List<EntityModel<Documento>> modelos = dono.getDocumentos().stream()
				.map(item -> montarModelo(usuarioId, item)).toList();
		Link self = linkTo(methodOn(UsuarioDocumentoControle.class).obterDocumentos(usuarioId)).withSelfRel();
		Link donoLink = linkTo(methodOn(UsuarioControle.class).obterUsuario(usuarioId))
				.withRel("usuario");
		return ResponseEntity.ok(CollectionModel.of(modelos, self, donoLink));
	}

	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Documento>> cadastrarDocumento(@PathVariable long usuarioId,
			@RequestBody Documento item) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		item.setId(null);
		dono.getDocumentos().add(item);
		repositorio.saveAndFlush(dono);
		EntityModel<Documento> modelo = montarModelo(usuarioId, item);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Documento>> atualizarDocumento(@PathVariable long usuarioId,
			@RequestBody Documento atualizacao) {
		if (atualizacao.getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o id do registro a atualizar");
		}
		Usuario dono = obterDonoOuFalhar(usuarioId);
		Documento item = buscar(dono, atualizacao.getId());
		if (item == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado para este usuário");
		}
		atualizador.atualizar(item, atualizacao);
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarModelo(usuarioId, item));
	}

	@DeleteMapping("/excluir/{id}")
	public ResponseEntity<Void> excluirDocumento(@PathVariable long usuarioId, @PathVariable long id) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		boolean removido = dono.getDocumentos().removeIf(item -> item.getId() != null && item.getId() == id);
		if (!removido) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado para este usuário");
		}
		repositorio.saveAndFlush(dono);
		return ResponseEntity.noContent().build();
	}
}
