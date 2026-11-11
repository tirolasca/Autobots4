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

import com.autobots.automanager.entidades.Mercadoria;
import com.autobots.automanager.modelo.Desassociador;
import com.autobots.automanager.modelo.MercadoriaAtualizador;
import com.autobots.automanager.modelo.StringVerificadorNulo;
import com.autobots.automanager.repositorios.MercadoriaRepositorio;
import com.autobots.automanager.repositorios.VendaRepositorio;

@RestController
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
@Transactional
@RequestMapping("/mercadoria")
public class MercadoriaControle {
	private final MercadoriaRepositorio repositorio;
	private final VendaRepositorio vendaRepositorio;
	private final Desassociador desassociador;
	private final MercadoriaAtualizador atualizador = new MercadoriaAtualizador();
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public MercadoriaControle(MercadoriaRepositorio repositorio, VendaRepositorio vendaRepositorio,
			Desassociador desassociador) {
		this.repositorio = repositorio;
		this.vendaRepositorio = vendaRepositorio;
		this.desassociador = desassociador;
	}

	private EntityModel<Mercadoria> montarModelo(Mercadoria mercadoria) {
		long id = mercadoria.getId();
		Link self = linkTo(methodOn(MercadoriaControle.class).obterMercadoria(id)).withSelfRel();
		Link lista = linkTo(methodOn(MercadoriaControle.class).obterMercadorias()).withRel("mercadorias");
		return EntityModel.of(mercadoria, self, lista);
	}

	@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR')")
	@GetMapping("/{id}")
	public ResponseEntity<EntityModel<Mercadoria>> obterMercadoria(@PathVariable long id) {
		Mercadoria mercadoria = repositorio.findById(id).orElse(null);
		if (mercadoria == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(mercadoria));
	}

	@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR')")
	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Mercadoria>>> obterMercadorias() {
		List<EntityModel<Mercadoria>> modelos = repositorio.findAll().stream().map(this::montarModelo).toList();
		Link self = linkTo(methodOn(MercadoriaControle.class).obterMercadorias()).withSelfRel();
		return ResponseEntity.ok(CollectionModel.of(modelos, self));
	}

	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Mercadoria>> cadastrarMercadoria(@RequestBody Mercadoria mercadoria) {
		if (verificador.verificar(mercadoria.getNome()) || mercadoria.getValidade() == null
				|| mercadoria.getFabricao() == null || mercadoria.getQuantidade() == null
				|| mercadoria.getValor() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Informe nome, validade, fabricao, quantidade e valor da mercadoria");
		}
		mercadoria.setId(null);
		if (mercadoria.getCadastro() == null) {
			mercadoria.setCadastro(new Date());
		}
		Mercadoria salva = repositorio.saveAndFlush(mercadoria);
		EntityModel<Mercadoria> modelo = montarModelo(salva);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Mercadoria>> atualizarMercadoria(@RequestBody Mercadoria atualizacao) {
		if (atualizacao.getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o id da mercadoria a atualizar");
		}
		Mercadoria mercadoria = repositorio.findById(atualizacao.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mercadoria não encontrada"));
		atualizador.atualizar(mercadoria, atualizacao);
		Mercadoria salva = repositorio.saveAndFlush(mercadoria);
		return ResponseEntity.ok(montarModelo(salva));
	}

	/** Mercadorias que já constam em vendas não podem ser excluídas. */
	@DeleteMapping("/excluir/{id}")
	public ResponseEntity<Void> excluirMercadoria(@PathVariable long id) {
		Mercadoria mercadoria = repositorio.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mercadoria não encontrada"));
		if (vendaRepositorio.contarPorMercadoria(id) > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Mercadoria consta em vendas e não pode ser excluída");
		}
		desassociador.desassociarMercadoria(id);
		repositorio.delete(mercadoria);
		return ResponseEntity.noContent().build();
	}
}
