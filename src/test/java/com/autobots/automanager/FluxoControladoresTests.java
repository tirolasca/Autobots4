package com.autobots.automanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.autobots.automanager.adaptadores.UserDetailsServiceImpl;
import com.autobots.automanager.controles.EmpresaControle;
import com.autobots.automanager.controles.EmpresaUsuarioControle;
import com.autobots.automanager.controles.MercadoriaControle;
import com.autobots.automanager.controles.UsuarioControle;
import com.autobots.automanager.controles.UsuarioCredencialControle;
import com.autobots.automanager.controles.VeiculoControle;
import com.autobots.automanager.controles.VendaControle;
import com.autobots.automanager.entidades.Credencial;
import com.autobots.automanager.entidades.CredencialCodigoBarra;
import com.autobots.automanager.entidades.CredencialUsuarioSenha;
import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.entidades.Mercadoria;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.entidades.Veiculo;
import com.autobots.automanager.entidades.Venda;
import com.autobots.automanager.enumeracoes.PerfilUsuario;
import com.autobots.automanager.enumeracoes.TipoVeiculo;
import com.autobots.automanager.repositorios.EmpresaRepositorio;
import com.autobots.automanager.repositorios.UsuarioRepositorio;
import com.autobots.automanager.repositorios.VendaRepositorio;

/**
 * Exercita os controllers diretamente (sem servidor HTTP) sobre o banco H2 em
 * memória, já com a carga inicial do Runner.
 */
@SpringBootTest
class FluxoControladoresTests {

	@Autowired
	private EmpresaControle empresaControle;
	@Autowired
	private EmpresaUsuarioControle empresaUsuarioControle;
	@Autowired
	private UsuarioControle usuarioControle;
	@Autowired
	private UsuarioCredencialControle credencialControle;
	@Autowired
	private MercadoriaControle mercadoriaControle;
	@Autowired
	private VeiculoControle veiculoControle;
	@Autowired
	private VendaControle vendaControle;
	@Autowired
	private EmpresaRepositorio empresaRepositorio;
	@Autowired
	private UsuarioRepositorio usuarioRepositorio;
	@Autowired
	private VendaRepositorio vendaRepositorio;
	@Autowired
	private UserDetailsServiceImpl servicoUsuarios;

	@BeforeEach
	void prepararContextoDeRequisicao() {
		// Os links HATEOAS são montados a partir da requisição corrente.
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
		// Os controles são chamados direto (sem passar pelo filtro JWT), então o teste
		// se autentica como o administrador da carga inicial; a autorização por perfil
		// é exercitada em SegurancaTests.
		UserDetails administrador = servicoUsuarios.loadUserByUsername("admin");
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(administrador, null, administrador.getAuthorities()));
	}

	@AfterEach
	void limparContextoDeRequisicao() {
		RequestContextHolder.resetRequestAttributes();
		SecurityContextHolder.clearContext();
	}

	private static String sufixo() {
		return String.valueOf(System.nanoTime());
	}

	private long criarUsuario(String nome, PerfilUsuario perfil) {
		Usuario usuario = new Usuario();
		usuario.setNome(nome);
		usuario.getPerfis().add(perfil);
		return usuarioControle.cadastrarUsuario(usuario).getBody().getContent().getId();
	}

	private long criarEmpresa(String razaoSocial) {
		Empresa empresa = new Empresa();
		empresa.setRazaoSocial(razaoSocial);
		return empresaControle.cadastrarEmpresa(empresa).getBody().getContent().getId();
	}

	private long criarMercadoria(String nome) {
		Mercadoria mercadoria = new Mercadoria();
		mercadoria.setNome(nome);
		mercadoria.setValidade(new Date());
		mercadoria.setFabricao(new Date());
		mercadoria.setQuantidade(10L);
		mercadoria.setValor(35.9);
		return mercadoriaControle.cadastrarMercadoria(mercadoria).getBody().getContent().getId();
	}

	private static Usuario referencia(long id) {
		Usuario usuario = new Usuario();
		usuario.setId(id);
		return usuario;
	}

	private static Mercadoria referenciaMercadoria(long id) {
		Mercadoria mercadoria = new Mercadoria();
		mercadoria.setId(id);
		return mercadoria;
	}

	private static Veiculo referenciaVeiculo(long id) {
		Veiculo veiculo = new Veiculo();
		veiculo.setId(id);
		return veiculo;
	}

	@Test
	void cargaInicialCriaEmpresaUsuariosEVendas() {
		assertTrue(empresaRepositorio.count() >= 1);
		assertTrue(usuarioRepositorio.count() >= 3);
		assertTrue(vendaRepositorio.count() >= 2);
	}

	@Test
	void empresaExpoeLinksHateoas() {
		Empresa empresa = empresaRepositorio.findAll().get(0);
		ResponseEntity<EntityModel<Empresa>> resposta = empresaControle.obterEmpresa(empresa.getId());
		assertEquals(200, resposta.getStatusCode().value());
		EntityModel<Empresa> modelo = resposta.getBody();
		assertNotNull(modelo);
		for (String rel : new String[] { "self", "empresas", "telefones", "endereco", "usuarios", "mercadorias",
				"servicos", "vendas" }) {
			assertTrue(modelo.hasLink(rel), "link ausente: " + rel);
		}
	}

	@Test
	void empresaInexistenteRetorna404() {
		assertEquals(404, empresaControle.obterEmpresa(Long.MAX_VALUE).getStatusCode().value());
	}

	@Test
	void mercadoriaTemCrudCompleto() {
		long id = criarMercadoria("Filtro de óleo " + sufixo());

		Mercadoria atualizacao = new Mercadoria();
		atualizacao.setId(id);
		atualizacao.setQuantidade(0L);
		ResponseEntity<EntityModel<Mercadoria>> atualizada = mercadoriaControle.atualizarMercadoria(atualizacao);
		assertEquals(200, atualizada.getStatusCode().value());
		assertEquals(0L, atualizada.getBody().getContent().getQuantidade());
		assertEquals(35.9, atualizada.getBody().getContent().getValor());

		assertEquals(204, mercadoriaControle.excluirMercadoria(id).getStatusCode().value());
		assertEquals(404, mercadoriaControle.obterMercadoria(id).getStatusCode().value());
	}

	@Test
	void usuarioAssociadoAUmaEmpresaNaoPodeIrParaOutra() {
		long empresaA = criarEmpresa("Empresa A " + sufixo());
		long empresaB = criarEmpresa("Empresa B " + sufixo());
		long usuario = criarUsuario("Usuário " + sufixo(), PerfilUsuario.CLIENTE);

		ResponseEntity<CollectionModel<EntityModel<Usuario>>> associado = empresaUsuarioControle
				.associarUsuario(empresaA, usuario);
		assertEquals(1, associado.getBody().getContent().size());

		// associar de novo é idempotente
		assertEquals(1, empresaUsuarioControle.associarUsuario(empresaA, usuario).getBody().getContent().size());

		ResponseStatusException conflito = assertThrows(ResponseStatusException.class,
				() -> empresaUsuarioControle.associarUsuario(empresaB, usuario));
		assertEquals(409, conflito.getStatusCode().value());

		assertEquals(0, empresaUsuarioControle.desassociarUsuario(empresaA, usuario).getBody().getContent().size());
		assertEquals(1, empresaUsuarioControle.associarUsuario(empresaB, usuario).getBody().getContent().size());
	}

	@Test
	void vendaValidaPerfisPropriedadeDoVeiculoEBloqueiaExclusoes() {
		long cliente = criarUsuario("Cliente " + sufixo(), PerfilUsuario.CLIENTE);
		long outroCliente = criarUsuario("Outro cliente " + sufixo(), PerfilUsuario.CLIENTE);
		long funcionario = criarUsuario("Funcionário " + sufixo(), PerfilUsuario.VENDEDOR);
		long mercadoria = criarMercadoria("Pastilha de freio " + sufixo());

		Veiculo novoVeiculo = new Veiculo();
		novoVeiculo.setTipo(TipoVeiculo.HATCH);
		novoVeiculo.setModelo("etios");
		novoVeiculo.setPlaca("TST-" + (System.nanoTime() % 10000));
		novoVeiculo.setProprietario(referencia(cliente));
		long veiculo = veiculoControle.cadastrarVeiculo(novoVeiculo).getBody().getContent().getId();

		// funcionário não pode figurar como cliente
		Venda perfilErrado = new Venda();
		perfilErrado.setIdentificacao("PE-" + sufixo());
		perfilErrado.setCliente(referencia(funcionario));
		perfilErrado.setFuncionario(referencia(funcionario));
		perfilErrado.getMercadorias().add(referenciaMercadoria(mercadoria));
		assertEquals(400, assertThrows(ResponseStatusException.class,
				() -> vendaControle.cadastrarVenda(perfilErrado)).getStatusCode().value());

		// veículo precisa pertencer ao cliente da venda
		Venda veiculoDeOutro = new Venda();
		veiculoDeOutro.setIdentificacao("VO-" + sufixo());
		veiculoDeOutro.setCliente(referencia(outroCliente));
		veiculoDeOutro.setFuncionario(referencia(funcionario));
		veiculoDeOutro.setVeiculo(referenciaVeiculo(veiculo));
		veiculoDeOutro.getMercadorias().add(referenciaMercadoria(mercadoria));
		assertEquals(400, assertThrows(ResponseStatusException.class,
				() -> vendaControle.cadastrarVenda(veiculoDeOutro)).getStatusCode().value());

		// venda válida
		Venda valida = new Venda();
		valida.setIdentificacao("OK-" + sufixo());
		valida.setCliente(referencia(cliente));
		valida.setFuncionario(referencia(funcionario));
		valida.setVeiculo(referenciaVeiculo(veiculo));
		valida.getMercadorias().add(referenciaMercadoria(mercadoria));
		ResponseEntity<EntityModel<Venda>> criada = vendaControle.cadastrarVenda(valida);
		assertEquals(201, criada.getStatusCode().value());
		assertNotNull(criada.getHeaders().getLocation());
		EntityModel<Venda> modelo = criada.getBody();
		for (String rel : new String[] { "self", "vendas", "cliente", "funcionario", "veiculo" }) {
			assertTrue(modelo.hasLink(rel), "link ausente: " + rel);
		}

		// registros que constam em vendas não podem ser excluídos
		assertEquals(409, assertThrows(ResponseStatusException.class,
				() -> usuarioControle.excluirUsuario(cliente)).getStatusCode().value());
		assertEquals(409, assertThrows(ResponseStatusException.class,
				() -> mercadoriaControle.excluirMercadoria(mercadoria)).getStatusCode().value());
		assertEquals(409, assertThrows(ResponseStatusException.class,
				() -> veiculoControle.excluirVeiculo(veiculo)).getStatusCode().value());

		// excluindo a venda, tudo é liberado
		long vendaId = modelo.getContent().getId();
		assertEquals(204, vendaControle.excluirVenda(vendaId).getStatusCode().value());
		assertEquals(204, veiculoControle.excluirVeiculo(veiculo).getStatusCode().value());
		assertEquals(204, usuarioControle.excluirUsuario(cliente).getStatusCode().value());
	}

	@Test
	void credenciaisSuportamOsDoisTiposENaoMudamDeTipo() {
		long usuario = criarUsuario("Com credencial " + sufixo(), PerfilUsuario.VENDEDOR);

		CredencialUsuarioSenha senha = new CredencialUsuarioSenha();
		senha.setNomeUsuario("user" + sufixo());
		senha.setSenha("segredo");
		ResponseEntity<EntityModel<Credencial>> criada = credencialControle.cadastrarCredencial(usuario, senha);
		assertEquals(201, criada.getStatusCode().value());
		long credencialId = criada.getBody().getContent().getId();
		assertEquals(Boolean.FALSE, criada.getBody().getContent().getInativo());

		CredencialCodigoBarra codigo = new CredencialCodigoBarra();
		codigo.setCodigo(System.nanoTime());
		assertEquals(201, credencialControle.cadastrarCredencial(usuario, codigo).getStatusCode().value());
		assertEquals(2, credencialControle.obterCredenciais(usuario).getBody().getContent().size());

		CredencialCodigoBarra trocaDeTipo = new CredencialCodigoBarra();
		trocaDeTipo.setId(credencialId);
		trocaDeTipo.setCodigo(1L);
		assertEquals(400, assertThrows(ResponseStatusException.class,
				() -> credencialControle.atualizarCredencial(usuario, trocaDeTipo)).getStatusCode().value());

		assertEquals(204, credencialControle.excluirCredencial(usuario, credencialId).getStatusCode().value());
		assertEquals(1, credencialControle.obterCredenciais(usuario).getBody().getContent().size());
	}
}
