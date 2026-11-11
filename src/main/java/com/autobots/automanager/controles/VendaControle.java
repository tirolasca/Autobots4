package com.autobots.automanager.controles;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.autobots.automanager.entidades.Servico;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.entidades.Veiculo;
import com.autobots.automanager.entidades.Venda;
import com.autobots.automanager.enumeracoes.PerfilUsuario;
import com.autobots.automanager.modelo.Desassociador;
import com.autobots.automanager.modelo.PoliticaAcesso;
import com.autobots.automanager.modelo.StringVerificadorNulo;
import com.autobots.automanager.repositorios.MercadoriaRepositorio;
import com.autobots.automanager.repositorios.ServicoRepositorio;
import com.autobots.automanager.repositorios.UsuarioRepositorio;
import com.autobots.automanager.repositorios.VeiculoRepositorio;
import com.autobots.automanager.repositorios.VendaRepositorio;

/**
 * Vendas de mercadorias e serviços. Cliente, funcionário, veículo,
 * mercadorias e serviços são informados apenas pelo id, por exemplo:
 * {"identificacao": "V-1", "cliente": {"id": 3}, "funcionario": {"id": 1},
 * "veiculo": {"id": 1}, "mercadorias": [{"id": 1}], "servicos": [{"id": 2}]}.
 */
@RestController
@Transactional
@RequestMapping("/venda")
public class VendaControle {
	private final VendaRepositorio repositorio;
	private final UsuarioRepositorio usuarioRepositorio;
	private final VeiculoRepositorio veiculoRepositorio;
	private final MercadoriaRepositorio mercadoriaRepositorio;
	private final ServicoRepositorio servicoRepositorio;
	private final Desassociador desassociador;
	private final PoliticaAcesso acesso;
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public VendaControle(VendaRepositorio repositorio, UsuarioRepositorio usuarioRepositorio,
			VeiculoRepositorio veiculoRepositorio, MercadoriaRepositorio mercadoriaRepositorio,
			ServicoRepositorio servicoRepositorio, Desassociador desassociador, PoliticaAcesso acesso) {
		this.repositorio = repositorio;
		this.usuarioRepositorio = usuarioRepositorio;
		this.veiculoRepositorio = veiculoRepositorio;
		this.mercadoriaRepositorio = mercadoriaRepositorio;
		this.servicoRepositorio = servicoRepositorio;
		this.desassociador = desassociador;
		this.acesso = acesso;
	}

	private ResponseStatusException invalida(String mensagem) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
	}

	/** Quem pode figurar como cliente da venda. */
	private static final Set<PerfilUsuario> PERFIS_CLIENTE = EnumSet.of(PerfilUsuario.CLIENTE);
	/** Quem pode figurar como funcionário (atendente) da venda. */
	private static final Set<PerfilUsuario> PERFIS_FUNCIONARIO = EnumSet.of(PerfilUsuario.ADMINISTRADOR,
			PerfilUsuario.GERENTE, PerfilUsuario.VENDEDOR);

	private Usuario resolverUsuario(Usuario referencia, Set<PerfilUsuario> perfisAceitos, String papel) {
		if (referencia == null || referencia.getId() == null) {
			throw invalida("Informe o " + papel + " da venda (" + papel + ".id)");
		}
		Usuario usuario = usuarioRepositorio.findById(referencia.getId())
				.orElseThrow(() -> invalida("O usuário informado como " + papel + " não existe"));
		if (usuario.getPerfis().stream().noneMatch(perfisAceitos::contains)) {
			throw invalida("O usuário informado como " + papel + " não possui um dos perfis exigidos: " + perfisAceitos);
		}
		return usuario;
	}

	private Veiculo resolverVeiculo(Veiculo referencia) {
		if (referencia == null || referencia.getId() == null) {
			return null;
		}
		return veiculoRepositorio.findById(referencia.getId())
				.orElseThrow(() -> invalida("O veículo informado não existe"));
	}

	private Set<Mercadoria> resolverMercadorias(Set<Mercadoria> referencias) {
		Set<Mercadoria> resolvidas = new HashSet<>();
		if (referencias != null) {
			for (Mercadoria referencia : referencias) {
				if (referencia.getId() == null) {
					throw invalida("Cada mercadoria da venda deve informar o id");
				}
				resolvidas.add(mercadoriaRepositorio.findById(referencia.getId())
						.orElseThrow(() -> invalida("Mercadoria " + referencia.getId() + " não existe")));
			}
		}
		return resolvidas;
	}

	private Set<Servico> resolverServicos(Set<Servico> referencias) {
		Set<Servico> resolvidos = new HashSet<>();
		if (referencias != null) {
			for (Servico referencia : referencias) {
				if (referencia.getId() == null) {
					throw invalida("Cada serviço da venda deve informar o id");
				}
				resolvidos.add(servicoRepositorio.findById(referencia.getId())
						.orElseThrow(() -> invalida("Serviço " + referencia.getId() + " não existe")));
			}
		}
		return resolvidos;
	}

	private void validarConsistencia(Venda venda) {
		if (venda.getMercadorias().isEmpty() && venda.getServicos().isEmpty()) {
			throw invalida("A venda deve ter ao menos uma mercadoria ou um serviço");
		}
		if (!venda.getServicos().isEmpty() && venda.getVeiculo() == null) {
			throw invalida("Venda com serviços exige o veículo atendido (veiculo.id)");
		}
		if (venda.getVeiculo() != null) {
			Usuario proprietario = venda.getVeiculo().getProprietario();
			if (proprietario == null || !proprietario.getId().equals(venda.getCliente().getId())) {
				throw invalida("O veículo informado não pertence ao cliente da venda");
			}
		}
	}

	private EntityModel<Venda> montarModelo(Venda venda) {
		long id = venda.getId();
		Link self = linkTo(methodOn(VendaControle.class).obterVenda(id)).withSelfRel();
		Link lista = linkTo(methodOn(VendaControle.class).obterVendas()).withRel("vendas");
		EntityModel<Venda> modelo = EntityModel.of(venda, self, lista);
		if (venda.getCliente() != null) {
			long clienteId = venda.getCliente().getId();
			modelo.add(linkTo(methodOn(UsuarioControle.class).obterUsuario(clienteId)).withRel("cliente"));
		}
		if (venda.getFuncionario() != null) {
			long funcionarioId = venda.getFuncionario().getId();
			modelo.add(linkTo(methodOn(UsuarioControle.class).obterUsuario(funcionarioId))
					.withRel("funcionario"));
		}
		if (venda.getVeiculo() != null) {
			long veiculoId = venda.getVeiculo().getId();
			modelo.add(linkTo(methodOn(VeiculoControle.class).obterVeiculo(veiculoId)).withRel("veiculo"));
		}
		return modelo;
	}

	@PreAuthorize("@acesso.podeLerVenda(#id)")
	@GetMapping("/{id}")
	public ResponseEntity<EntityModel<Venda>> obterVenda(@PathVariable long id) {
		Venda venda = repositorio.findById(id).orElse(null);
		if (venda == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(venda));
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Venda>>> obterVendas() {
		List<EntityModel<Venda>> modelos = repositorio.findAll().stream().filter(acesso::vendaVisivel)
				.map(this::montarModelo).toList();
		Link self = linkTo(methodOn(VendaControle.class).obterVendas()).withSelfRel();
		return ResponseEntity.ok(CollectionModel.of(modelos, self));
	}

	@PreAuthorize("@acesso.podeCadastrarVenda(#corpo)")
	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Venda>> cadastrarVenda(@RequestBody Venda corpo) {
		if (verificador.verificar(corpo.getIdentificacao())) {
			throw invalida("Informe a identificação da venda");
		}
		Venda venda = new Venda();
		venda.setIdentificacao(corpo.getIdentificacao());
		venda.setCadastro(corpo.getCadastro() != null ? corpo.getCadastro() : new Date());
		// vendedor registra a venda em seu próprio nome, mesmo que não informe o funcionário
		if ((corpo.getFuncionario() == null || corpo.getFuncionario().getId() == null)
				&& acesso.temPerfil(PerfilUsuario.VENDEDOR) && acesso.usuarioAtual() != null) {
			Usuario eu = new Usuario();
			eu.setId(acesso.usuarioAtual().getUsuarioId());
			corpo.setFuncionario(eu);
		}
		venda.setCliente(resolverUsuario(corpo.getCliente(), PERFIS_CLIENTE, "cliente"));
		venda.setFuncionario(resolverUsuario(corpo.getFuncionario(), PERFIS_FUNCIONARIO, "funcionário"));
		venda.setVeiculo(resolverVeiculo(corpo.getVeiculo()));
		venda.getMercadorias().addAll(resolverMercadorias(corpo.getMercadorias()));
		venda.getServicos().addAll(resolverServicos(corpo.getServicos()));
		validarConsistencia(venda);
		Venda salva = repositorio.saveAndFlush(venda);
		EntityModel<Venda> modelo = montarModelo(salva);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	/**
	 * Atualiza apenas o que for informado. Mercadorias e serviços, quando
	 * enviados (lista não vazia), substituem os atuais da venda.
	 */
	@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Venda>> atualizarVenda(@RequestBody Venda atualizacao) {
		if (atualizacao.getId() == null) {
			throw invalida("Informe o id da venda a atualizar");
		}
		Venda venda = repositorio.findById(atualizacao.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venda não encontrada"));
		if (!verificador.verificar(atualizacao.getIdentificacao())) {
			venda.setIdentificacao(atualizacao.getIdentificacao());
		}
		if (atualizacao.getCadastro() != null) {
			venda.setCadastro(atualizacao.getCadastro());
		}
		if (atualizacao.getCliente() != null && atualizacao.getCliente().getId() != null) {
			venda.setCliente(resolverUsuario(atualizacao.getCliente(), PERFIS_CLIENTE, "cliente"));
		}
		if (atualizacao.getFuncionario() != null && atualizacao.getFuncionario().getId() != null) {
			venda.setFuncionario(
					resolverUsuario(atualizacao.getFuncionario(), PERFIS_FUNCIONARIO, "funcionário"));
		}
		if (atualizacao.getVeiculo() != null && atualizacao.getVeiculo().getId() != null) {
			venda.setVeiculo(resolverVeiculo(atualizacao.getVeiculo()));
		}
		if (atualizacao.getMercadorias() != null && !atualizacao.getMercadorias().isEmpty()) {
			Set<Mercadoria> novas = resolverMercadorias(atualizacao.getMercadorias());
			venda.getMercadorias().clear();
			venda.getMercadorias().addAll(novas);
		}
		if (atualizacao.getServicos() != null && !atualizacao.getServicos().isEmpty()) {
			Set<Servico> novos = resolverServicos(atualizacao.getServicos());
			venda.getServicos().clear();
			venda.getServicos().addAll(novos);
		}
		validarConsistencia(venda);
		Venda salva = repositorio.saveAndFlush(venda);
		return ResponseEntity.ok(montarModelo(salva));
	}

	@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
	@DeleteMapping("/excluir/{id}")
	public ResponseEntity<Void> excluirVenda(@PathVariable long id) {
		Venda venda = repositorio.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venda não encontrada"));
		desassociador.desassociarVenda(id);
		repositorio.delete(venda);
		return ResponseEntity.noContent().build();
	}
}
