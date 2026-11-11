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

import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.entidades.Veiculo;
import com.autobots.automanager.modelo.PoliticaAcesso;
import com.autobots.automanager.modelo.StringVerificadorNulo;
import com.autobots.automanager.modelo.VeiculoAtualizador;
import com.autobots.automanager.repositorios.UsuarioRepositorio;
import com.autobots.automanager.repositorios.VeiculoRepositorio;
import com.autobots.automanager.repositorios.VendaRepositorio;

/**
 * Veículos pertencem a um usuário (proprietário). No corpo das requisições o
 * proprietário é informado apenas pelo id: {"proprietario": {"id": 3}}.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR')")
@Transactional
@RequestMapping("/veiculo")
public class VeiculoControle {
	private final VeiculoRepositorio repositorio;
	private final UsuarioRepositorio usuarioRepositorio;
	private final VendaRepositorio vendaRepositorio;
	private final PoliticaAcesso acesso;
	private final VeiculoAtualizador atualizador = new VeiculoAtualizador();
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public VeiculoControle(VeiculoRepositorio repositorio, UsuarioRepositorio usuarioRepositorio,
			VendaRepositorio vendaRepositorio, PoliticaAcesso acesso) {
		this.repositorio = repositorio;
		this.usuarioRepositorio = usuarioRepositorio;
		this.vendaRepositorio = vendaRepositorio;
		this.acesso = acesso;
	}

	private Usuario resolverProprietario(Usuario referencia) {
		if (referencia == null || referencia.getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Informe o proprietário do veículo (proprietario.id)");
		}
		return usuarioRepositorio.findById(referencia.getId()).orElseThrow(
				() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proprietário informado não existe"));
	}

	private EntityModel<Veiculo> montarModelo(Veiculo veiculo) {
		long id = veiculo.getId();
		Link self = linkTo(methodOn(VeiculoControle.class).obterVeiculo(id)).withSelfRel();
		Link lista = linkTo(methodOn(VeiculoControle.class).obterVeiculos()).withRel("veiculos");
		EntityModel<Veiculo> modelo = EntityModel.of(veiculo, self, lista);
		if (veiculo.getProprietario() != null) {
			long proprietarioId = veiculo.getProprietario().getId();
			modelo.add(linkTo(methodOn(UsuarioControle.class).obterUsuario(proprietarioId))
					.withRel("proprietario"));
		}
		return modelo;
	}

	@PreAuthorize("@acesso.podeLerVeiculo(#id)")
	@GetMapping("/{id}")
	public ResponseEntity<EntityModel<Veiculo>> obterVeiculo(@PathVariable long id) {
		Veiculo veiculo = repositorio.findById(id).orElse(null);
		if (veiculo == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(veiculo));
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Veiculo>>> obterVeiculos() {
		List<EntityModel<Veiculo>> modelos = repositorio.findAll().stream().filter(acesso::veiculoVisivel)
				.map(this::montarModelo).toList();
		Link self = linkTo(methodOn(VeiculoControle.class).obterVeiculos()).withSelfRel();
		return ResponseEntity.ok(CollectionModel.of(modelos, self));
	}

	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Veiculo>> cadastrarVeiculo(@RequestBody Veiculo veiculo) {
		if (veiculo.getTipo() == null || verificador.verificar(veiculo.getModelo())
				|| verificador.verificar(veiculo.getPlaca())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe tipo, modelo e placa do veículo");
		}
		veiculo.setId(null);
		veiculo.setProprietario(resolverProprietario(veiculo.getProprietario()));
		Veiculo salvo = repositorio.saveAndFlush(veiculo);
		EntityModel<Veiculo> modelo = montarModelo(salvo);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Veiculo>> atualizarVeiculo(@RequestBody Veiculo atualizacao) {
		if (atualizacao.getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o id do veículo a atualizar");
		}
		Veiculo veiculo = repositorio.findById(atualizacao.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Veículo não encontrado"));
		atualizador.atualizar(veiculo, atualizacao);
		if (atualizacao.getProprietario() != null && atualizacao.getProprietario().getId() != null) {
			veiculo.setProprietario(resolverProprietario(atualizacao.getProprietario()));
		}
		Veiculo salvo = repositorio.saveAndFlush(veiculo);
		return ResponseEntity.ok(montarModelo(salvo));
	}

	/** Veículos que constam em vendas não podem ser excluídos. */
	@DeleteMapping("/excluir/{id}")
	public ResponseEntity<Void> excluirVeiculo(@PathVariable long id) {
		Veiculo veiculo = repositorio.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Veículo não encontrado"));
		if (vendaRepositorio.existsByVeiculoId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Veículo consta em vendas e não pode ser excluído");
		}
		repositorio.delete(veiculo);
		return ResponseEntity.noContent().build();
	}
}
