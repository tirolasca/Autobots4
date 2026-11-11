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

import com.autobots.automanager.entidades.Servico;
import com.autobots.automanager.modelo.Desassociador;
import com.autobots.automanager.modelo.ServicoAtualizador;
import com.autobots.automanager.modelo.StringVerificadorNulo;
import com.autobots.automanager.repositorios.ServicoRepositorio;
import com.autobots.automanager.repositorios.VendaRepositorio;

@RestController
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
@Transactional
@RequestMapping("/servico")
public class ServicoControle {
	private final ServicoRepositorio repositorio;
	private final VendaRepositorio vendaRepositorio;
	private final Desassociador desassociador;
	private final ServicoAtualizador atualizador = new ServicoAtualizador();
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public ServicoControle(ServicoRepositorio repositorio, VendaRepositorio vendaRepositorio,
			Desassociador desassociador) {
		this.repositorio = repositorio;
		this.vendaRepositorio = vendaRepositorio;
		this.desassociador = desassociador;
	}

	private EntityModel<Servico> montarModelo(Servico servico) {
		long id = servico.getId();
		Link self = linkTo(methodOn(ServicoControle.class).obterServico(id)).withSelfRel();
		Link lista = linkTo(methodOn(ServicoControle.class).obterServicos()).withRel("servicos");
		return EntityModel.of(servico, self, lista);
	}

	@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR')")
	@GetMapping("/{id}")
	public ResponseEntity<EntityModel<Servico>> obterServico(@PathVariable long id) {
		Servico servico = repositorio.findById(id).orElse(null);
		if (servico == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(servico));
	}

	@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR')")
	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Servico>>> obterServicos() {
		List<EntityModel<Servico>> modelos = repositorio.findAll().stream().map(this::montarModelo).toList();
		Link self = linkTo(methodOn(ServicoControle.class).obterServicos()).withSelfRel();
		return ResponseEntity.ok(CollectionModel.of(modelos, self));
	}

	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Servico>> cadastrarServico(@RequestBody Servico servico) {
		if (verificador.verificar(servico.getNome()) || servico.getValor() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe nome e valor do serviço");
		}
		servico.setId(null);
		Servico salvo = repositorio.saveAndFlush(servico);
		EntityModel<Servico> modelo = montarModelo(salvo);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Servico>> atualizarServico(@RequestBody Servico atualizacao) {
		if (atualizacao.getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o id do serviço a atualizar");
		}
		Servico servico = repositorio.findById(atualizacao.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
		atualizador.atualizar(servico, atualizacao);
		Servico salvo = repositorio.saveAndFlush(servico);
		return ResponseEntity.ok(montarModelo(salvo));
	}

	/** Serviços que já constam em vendas não podem ser excluídos. */
	@DeleteMapping("/excluir/{id}")
	public ResponseEntity<Void> excluirServico(@PathVariable long id) {
		Servico servico = repositorio.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
		if (vendaRepositorio.contarPorServico(id) > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Serviço consta em vendas e não pode ser excluído");
		}
		desassociador.desassociarServico(id);
		repositorio.delete(servico);
		return ResponseEntity.noContent().build();
	}
}
