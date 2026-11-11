package com.autobots.automanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.autobots.automanager.adaptadores.UserDetailsImpl;
import com.autobots.automanager.adaptadores.UserDetailsServiceImpl;
import com.autobots.automanager.entidades.CredencialUsuarioSenha;
import com.autobots.automanager.jwt.ProvedorJwt;
import com.autobots.automanager.repositorios.CredencialUsuarioSenhaRepositorio;
import com.autobots.automanager.repositorios.MercadoriaRepositorio;

/**
 * Exercita a segurança de ponta a ponta: requisições HTTP simuladas passando
 * pelo filtro JWT, pelo filtro de autorização e pelas regras da Tabela 1.
 *
 * Como ler os códigos: 403 = o perfil não tem autorização; 400 ou 404 = a
 * autorização passou e foi o controle que recusou o pedido (corpo vazio ou id
 * inexistente). Assim dá para testar a permissão sem montar cada corpo.
 */
@SpringBootTest
class SegurancaTests {
	private static final long ID_INEXISTENTE = Long.MAX_VALUE;

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private FilterChainProxy filtrosDeSeguranca;
	@Autowired
	private ProvedorJwt provedorJwt;
	@Autowired
	private UserDetailsServiceImpl servicoUsuarios;
	@Autowired
	private CredencialUsuarioSenhaRepositorio credenciais;
	@Autowired
	private MercadoriaRepositorio mercadoriaRepositorio;

	private MockMvc mvc;

	@BeforeEach
	void montarMvc() {
		SecurityContextHolder.clearContext();
		mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtrosDeSeguranca).build();
	}

	@AfterEach
	void limpar() {
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ utilidades

	private MockHttpServletResponse enviar(HttpMethod metodo, String url, String autorizacao, String corpo)
			throws Exception {
		var requisicao = MockMvcRequestBuilders.request(metodo, url);
		if (autorizacao != null) {
			requisicao.header(HttpHeaders.AUTHORIZATION, autorizacao);
		}
		if (corpo != null) {
			requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
		}
		return mvc.perform(requisicao).andReturn().getResponse();
	}

	private int status(HttpMethod metodo, String url, String autorizacao, String corpo) throws Exception {
		return enviar(metodo, url, autorizacao, corpo).getStatus();
	}

	private int get(String url, String autorizacao) throws Exception {
		return status(HttpMethod.GET, url, autorizacao, null);
	}

	private String corpoDe(String url, String autorizacao) throws Exception {
		return enviar(HttpMethod.GET, url, autorizacao, null).getContentAsString();
	}

	private static String json(String nomeUsuario, String senha) {
		return "{\"nomeUsuario\":\"" + nomeUsuario + "\",\"senha\":\"" + senha + "\"}";
	}

	private MockHttpServletResponse login(String nomeUsuario, String senha) throws Exception {
		return enviar(HttpMethod.POST, "/login", null, json(nomeUsuario, senha));
	}

	/** Faz login e devolve o valor do cabeçalho Authorization ("Bearer ..."). */
	private String entrar(String nomeUsuario, String senha) throws Exception {
		MockHttpServletResponse resposta = login(nomeUsuario, senha);
		assertEquals(200, resposta.getStatus(), "login de " + nomeUsuario);
		String cabecalho = resposta.getHeader(HttpHeaders.AUTHORIZATION);
		assertNotNull(cabecalho);
		assertTrue(cabecalho.startsWith("Bearer "));
		return cabecalho;
	}

	private String admin() throws Exception {
		return entrar("admin", "admin123");
	}

	private String gerente() throws Exception {
		return entrar("gerente", "gerente123");
	}

	private String vendedor() throws Exception {
		return entrar("dompedrovendedor", "vendedor123");
	}

	private String cliente() throws Exception {
		return entrar("dompedrocliente", "cliente123");
	}

	private long idDe(String nomeUsuario) {
		return ((UserDetailsImpl) servicoUsuarios.loadUserByUsername(nomeUsuario)).getUsuarioId();
	}

	/** Troca um caractere do meio da assinatura (os últimos têm bits de preenchimento e não servem). */
	private static String adulterar(String token) {
		int posicao = token.length() - 10;
		char novo = token.charAt(posicao) == 'A' ? 'B' : 'A';
		return token.substring(0, posicao) + novo + token.substring(posicao + 1);
	}

	private static String sufixo() {
		return String.valueOf(System.nanoTime());
	}

	private static String usuarioJson(String nome, String perfis, String nomeUsuario, String senha, String extra) {
		return "{\"nome\":\"" + nome + "\",\"perfis\":[" + perfis + "],\"credenciais\":[{\"tipo\":\"USUARIO_SENHA\","
				+ "\"nomeUsuario\":\"" + nomeUsuario + "\",\"senha\":\"" + senha + "\"" + extra + "}]}";
	}

	/** Cadastra um usuário com o token informado e devolve o id (lido do cabeçalho Location). */
	private long cadastrarUsuario(String token, String perfis, String nomeUsuario, String senha) throws Exception {
		MockHttpServletResponse resposta = enviar(HttpMethod.POST, "/usuario/cadastro", token,
				usuarioJson("Teste " + nomeUsuario, perfis, nomeUsuario, senha, ""));
		assertEquals(201, resposta.getStatus(), "cadastro de " + nomeUsuario);
		String local = resposta.getHeader(HttpHeaders.LOCATION);
		assertNotNull(local);
		return Long.parseLong(local.substring(local.lastIndexOf('/') + 1));
	}

	// ------------------------------------------------------------------ autenticação

	@Test
	void rotasProtegidasSemTokenRetornam401() throws Exception {
		for (String rota : List.of("/usuario/lista", "/mercadoria/lista", "/servico/lista", "/venda/lista",
				"/veiculo/lista", "/empresa/lista")) {
			assertEquals(401, get(rota, null), rota);
		}
	}

	@Test
	void loginEPublicoEDevolveTokenNoCabecalhoENoCorpo() throws Exception {
		MockHttpServletResponse resposta = login("admin", "admin123");
		assertEquals(200, resposta.getStatus());
		String cabecalho = resposta.getHeader(HttpHeaders.AUTHORIZATION);
		assertNotNull(cabecalho);
		assertTrue(resposta.getContentAsString().contains("\"token\""));
		assertTrue(resposta.getContentAsString().contains(cabecalho.substring("Bearer ".length())));
	}

	@Test
	void loginComSenhaErradaOuUsuarioInexistenteRetorna401SemToken() throws Exception {
		MockHttpServletResponse senhaErrada = login("admin", "senha-errada");
		assertEquals(401, senhaErrada.getStatus());
		assertNull(senhaErrada.getHeader(HttpHeaders.AUTHORIZATION));

		MockHttpServletResponse inexistente = login("ninguem" + sufixo(), "qualquer");
		assertEquals(401, inexistente.getStatus());
		assertNull(inexistente.getHeader(HttpHeaders.AUTHORIZATION));
	}

	@Test
	void loginSemDadosRetorna400() throws Exception {
		assertEquals(400, status(HttpMethod.POST, "/login", null, "{}"));
	}

	@Test
	void tokenValidoPermiteAcessar() throws Exception {
		assertEquals(200, get("/usuario/lista", admin()));
	}

	@Test
	void tokenInvalidoAdulteradoOuSemPrefixoRetorna401() throws Exception {
		assertEquals(401, get("/usuario/lista", "Bearer isto.nao.e-um-jwt"));

		String valido = admin();
		String adulterado = adulterar(valido);
		assertNotEquals(valido, adulterado);
		assertEquals(401, get("/usuario/lista", adulterado));

		// token correto, mas sem o prefixo "Bearer "
		assertEquals(401, get("/usuario/lista", valido.substring("Bearer ".length())));
	}

	@Test
	void tokenExpiradoRetorna401() throws Exception {
		String expirado = provedorJwt.gerar("admin", List.of("ADMINISTRADOR"), Duration.ofMinutes(-5));
		assertEquals(401, get("/usuario/lista", "Bearer " + expirado));
	}

	@Test
	void tokenAssinadoComOutroSegredoRetorna401() throws Exception {
		ProvedorJwt falsificador = new ProvedorJwt("segredo-do-falsificador-com-mais-de-32-caracteres", 60_000);
		String falso = falsificador.gerar("admin", List.of("ADMINISTRADOR"));
		assertEquals(401, get("/usuario/lista", "Bearer " + falso));
	}

	@Test
	void credencialInativaNaoAutentica() throws Exception {
		String nomeUsuario = "inativo" + sufixo();
		MockHttpServletResponse resposta = enviar(HttpMethod.POST, "/usuario/cadastro", admin(),
				usuarioJson("Inativo", "\"CLIENTE\"", nomeUsuario, "segredo123", ",\"inativo\":true"));
		assertEquals(201, resposta.getStatus());
		assertEquals(401, login(nomeUsuario, "segredo123").getStatus());
	}

	@Test
	void tokenDeUsuarioExcluidoDeixaDeFuncionar() throws Exception {
		String nomeUsuario = "efemero" + sufixo();
		long id = cadastrarUsuario(admin(), "\"CLIENTE\"", nomeUsuario, "segredo123");
		String token = entrar(nomeUsuario, "segredo123");
		assertEquals(200, get("/usuario/" + id, token));

		assertEquals(204, status(HttpMethod.DELETE, "/usuario/excluir/" + id, admin(), null));
		assertEquals(401, get("/usuario/" + id, token));
	}

	// ------------------------------------------------------------------ senhas

	@Test
	void senhasSaoGravadasComoHashBcryptEUsuarioNovoConsegueEntrar() throws Exception {
		CredencialUsuarioSenha daCarga = credenciais.findByNomeUsuario("admin").orElseThrow();
		assertTrue(daCarga.getSenha().startsWith("$2"), "senha da carga inicial deve ser hash BCrypt");

		String nomeUsuario = "novo" + sufixo();
		cadastrarUsuario(admin(), "\"CLIENTE\"", nomeUsuario, "minha-senha");
		CredencialUsuarioSenha gravada = credenciais.findByNomeUsuario(nomeUsuario).orElseThrow();
		assertNotEquals("minha-senha", gravada.getSenha());
		assertTrue(gravada.getSenha().startsWith("$2"));
		assertEquals(200, login(nomeUsuario, "minha-senha").getStatus());
	}

	@Test
	void trocarSenhaPelaCredencialGravaNovoHash() throws Exception {
		String nomeUsuario = "troca" + sufixo();
		long id = cadastrarUsuario(admin(), "\"CLIENTE\"", nomeUsuario, "senha-antiga");
		CredencialUsuarioSenha atual = credenciais.findByNomeUsuario(nomeUsuario).orElseThrow();

		String corpo = "{\"tipo\":\"USUARIO_SENHA\",\"id\":" + atual.getId() + ",\"senha\":\"senha-nova\"}";
		assertEquals(200, status(HttpMethod.PUT, "/usuario/" + id + "/credencial/atualizar", admin(), corpo));

		assertTrue(credenciais.findByNomeUsuario(nomeUsuario).orElseThrow().getSenha().startsWith("$2"));
		assertEquals(401, login(nomeUsuario, "senha-antiga").getStatus());
		assertEquals(200, login(nomeUsuario, "senha-nova").getStatus());
	}

	@Test
	void senhaENuncaDevolvidaNasRespostas() throws Exception {
		String lista = corpoDe("/usuario/lista", admin());
		assertFalse(lista.contains("\"senha\""));
		assertFalse(lista.contains("$2a$"));
		assertFalse(lista.contains("$2b$"));
	}

	// ------------------------------------------------------------------ ADMINISTRADOR

	@Test
	void administradorFazTudoInclusiveGerenciarAdministradores() throws Exception {
		String token = admin();
		assertEquals(200, get("/usuario/lista", token));
		assertEquals(200, get("/empresa/lista", token));
		assertEquals(200, get("/mercadoria/lista", token));
		assertEquals(200, get("/venda/lista", token));
		assertEquals(400, status(HttpMethod.POST, "/mercadoria/cadastro", token, "{}"));
		assertEquals(404, status(HttpMethod.DELETE, "/servico/excluir/" + ID_INEXISTENTE, token, null));

		String nomeUsuario = "outroadmin" + sufixo();
		long id = cadastrarUsuario(token, "\"ADMINISTRADOR\"", nomeUsuario, "segredo123");
		assertEquals(200, get("/usuario/" + id, entrar(nomeUsuario, "segredo123")));
		assertEquals(204, status(HttpMethod.DELETE, "/usuario/excluir/" + id, token, null));
	}

	// ------------------------------------------------------------------ GERENTE

	@Test
	void gerenteGerenciaEquipeEClientesMasNaoAdministradoresNemEmpresas() throws Exception {
		String token = gerente();

		long vendedorNovo = cadastrarUsuario(token, "\"VENDEDOR\"", "vend" + sufixo(), "segredo123");
		long gerenteNovo = cadastrarUsuario(token, "\"GERENTE\"", "ger" + sufixo(), "segredo123");
		long clienteNovo = cadastrarUsuario(token, "\"CLIENTE\"", "cli" + sufixo(), "segredo123");
		assertEquals(204, status(HttpMethod.DELETE, "/usuario/excluir/" + vendedorNovo, token, null));
		assertEquals(204, status(HttpMethod.DELETE, "/usuario/excluir/" + gerenteNovo, token, null));
		assertEquals(204, status(HttpMethod.DELETE, "/usuario/excluir/" + clienteNovo, token, null));

		// não cria administrador, nem usuário que acumule um perfil acima do seu alcance
		String nome = "x" + sufixo();
		assertEquals(403, status(HttpMethod.POST, "/usuario/cadastro", token,
				usuarioJson("Admin", "\"ADMINISTRADOR\"", nome, "segredo123", "")));
		assertEquals(403, status(HttpMethod.POST, "/usuario/cadastro", token,
				usuarioJson("Misto", "\"CLIENTE\",\"ADMINISTRADOR\"", nome, "segredo123", "")));
		assertEquals(403, status(HttpMethod.POST, "/usuario/cadastro", token,
				usuarioJson("Fornecedor", "\"FORNECEDOR\"", nome, "segredo123", "")));

		// não mexe em administrador existente
		long idAdmin = idDe("admin");
		assertEquals(403, get("/usuario/" + idAdmin, token));
		assertEquals(403, status(HttpMethod.DELETE, "/usuario/excluir/" + idAdmin, token, null));
		assertEquals(403, status(HttpMethod.PUT, "/usuario/atualizar", token,
				"{\"id\":" + idAdmin + ",\"nome\":\"Invadido\"}"));

		// não promove ninguém a administrador
		long clienteParaPromover = cadastrarUsuario(admin(), "\"CLIENTE\"", "promo" + sufixo(), "segredo123");
		assertEquals(403, status(HttpMethod.PUT, "/usuario/atualizar", token,
				"{\"id\":" + clienteParaPromover + ",\"perfis\":[\"ADMINISTRADOR\"]}"));

		// empresas são só do administrador
		assertEquals(403, get("/empresa/lista", token));
		assertEquals(403, status(HttpMethod.POST, "/empresa/cadastro", token, "{}"));
	}

	@Test
	void gerenteFazCrudDeMercadoriasServicosEVendas() throws Exception {
		String token = gerente();
		assertEquals(200, get("/mercadoria/lista", token));
		assertEquals(200, get("/servico/lista", token));
		assertEquals(200, get("/venda/lista", token));
		assertEquals(400, status(HttpMethod.POST, "/mercadoria/cadastro", token, "{}"));
		assertEquals(400, status(HttpMethod.POST, "/servico/cadastro", token, "{}"));
		assertEquals(404, status(HttpMethod.DELETE, "/mercadoria/excluir/" + ID_INEXISTENTE, token, null));
		assertEquals(404, status(HttpMethod.DELETE, "/servico/excluir/" + ID_INEXISTENTE, token, null));
		assertEquals(400, status(HttpMethod.PUT, "/venda/atualizar", token, "{}"));
		assertEquals(404, status(HttpMethod.DELETE, "/venda/excluir/" + ID_INEXISTENTE, token, null));
	}

	// ------------------------------------------------------------------ VENDEDOR

	@Test
	void vendedorSoLeCatalogoEGerenciaApenasClientes() throws Exception {
		String token = vendedor();

		// lê serviços e mercadorias, mas não altera
		assertEquals(200, get("/mercadoria/lista", token));
		assertEquals(200, get("/servico/lista", token));
		assertEquals(403, status(HttpMethod.POST, "/mercadoria/cadastro", token, "{}"));
		assertEquals(403, status(HttpMethod.PUT, "/mercadoria/atualizar", token, "{}"));
		assertEquals(403, status(HttpMethod.DELETE, "/servico/excluir/" + ID_INEXISTENTE, token, null));
		assertEquals(403, get("/empresa/lista", token));

		// CRUD de clientes
		long cliente = cadastrarUsuario(token, "\"CLIENTE\"", "cli" + sufixo(), "segredo123");
		assertEquals(200, get("/usuario/" + cliente, token));
		assertEquals(200, status(HttpMethod.PUT, "/usuario/atualizar", token,
				"{\"id\":" + cliente + ",\"nome\":\"Nome novo\"}"));
		assertEquals(200, get("/usuario/" + cliente + "/email/lista", token));
		assertEquals(204, status(HttpMethod.DELETE, "/usuario/excluir/" + cliente, token, null));

		// não cria nem mexe em quem não é cliente
		String nome = "x" + sufixo();
		for (String perfil : List.of("\"ADMINISTRADOR\"", "\"GERENTE\"", "\"VENDEDOR\"", "\"FORNECEDOR\"")) {
			assertEquals(403, status(HttpMethod.POST, "/usuario/cadastro", token,
					usuarioJson("Indevido", perfil, nome, "segredo123", "")), perfil);
		}
		assertEquals(403, get("/usuario/" + idDe("admin"), token));
		assertEquals(403, get("/usuario/" + idDe("gerente"), token));
		assertEquals(403, status(HttpMethod.DELETE, "/usuario/excluir/" + idDe("gerente"), token, null));

		// não escala o privilégio de um cliente
		long clienteAlvo = cadastrarUsuario(token, "\"CLIENTE\"", "alvo" + sufixo(), "segredo123");
		assertEquals(403, status(HttpMethod.PUT, "/usuario/atualizar", token,
				"{\"id\":" + clienteAlvo + ",\"perfis\":[\"ADMINISTRADOR\"]}"));
	}

	@Test
	void vendedorCriaVendasEmSeuProprioNomeELeApenasAsSuas() throws Exception {
		String token = vendedor();
		long vendedor = idDe("dompedrovendedor");
		long clienteDaVenda = idDe("dompedrocliente");
		long mercadoria = mercadoriaRepositorio.findAll().get(0).getId();

		// sem informar o funcionário, a venda é registrada em nome do próprio vendedor
		String identificacao = "VND-" + sufixo();
		MockHttpServletResponse criada = enviar(HttpMethod.POST, "/venda/cadastro", token,
				"{\"identificacao\":\"" + identificacao + "\",\"cliente\":{\"id\":" + clienteDaVenda
						+ "},\"mercadorias\":[{\"id\":" + mercadoria + "}]}");
		assertEquals(201, criada.getStatus());
		String local = criada.getHeader(HttpHeaders.LOCATION);
		assertNotNull(local);
		assertTrue(criada.getContentAsString().contains("/usuario/" + vendedor),
				"link do funcionário deve apontar para o vendedor");
		assertEquals(200, get(local.substring(local.indexOf("/venda/")), token));
		assertTrue(corpoDe("/venda/lista", token).contains(identificacao));

		// não registra venda em nome de outro funcionário
		assertEquals(403, status(HttpMethod.POST, "/venda/cadastro", token,
				"{\"identificacao\":\"X-" + sufixo() + "\",\"cliente\":{\"id\":" + clienteDaVenda
						+ "},\"funcionario\":{\"id\":" + idDe("gerente") + "},\"mercadorias\":[{\"id\":" + mercadoria
						+ "}]}"));

		// não altera nem exclui vendas
		assertEquals(403, status(HttpMethod.PUT, "/venda/atualizar", token, "{}"));
		assertEquals(403, status(HttpMethod.DELETE, "/venda/excluir/" + ID_INEXISTENTE, token, null));

		// não enxerga venda feita por outro vendedor
		String doGerente = "GER-" + sufixo();
		MockHttpServletResponse deOutro = enviar(HttpMethod.POST, "/venda/cadastro", gerente(),
				"{\"identificacao\":\"" + doGerente + "\",\"cliente\":{\"id\":" + clienteDaVenda
						+ "},\"funcionario\":{\"id\":" + idDe("gerente") + "},\"mercadorias\":[{\"id\":" + mercadoria
						+ "}]}");
		assertEquals(201, deOutro.getStatus());
		String localDeOutro = deOutro.getHeader(HttpHeaders.LOCATION);
		assertEquals(403, get(localDeOutro.substring(localDeOutro.indexOf("/venda/")), token));
		assertFalse(corpoDe("/venda/lista", token).contains(doGerente));
	}

	// ------------------------------------------------------------------ CLIENTE

	@Test
	void clienteSoLeOProprioCadastro() throws Exception {
		String token = cliente();
		long eu = idDe("dompedrocliente");

		assertEquals(200, get("/usuario/" + eu, token));
		assertEquals(200, get("/usuario/" + eu + "/email/lista", token));
		assertEquals(200, get("/usuario/" + eu + "/documento/lista", token));

		// não lê o cadastro de ninguém mais, nem pela lista
		assertEquals(403, get("/usuario/" + idDe("admin"), token));
		assertEquals(403, get("/usuario/" + idDe("dompedrovendedor"), token));
		assertEquals(403, get("/usuario/" + idDe("admin") + "/email/lista", token));
		String lista = corpoDe("/usuario/lista", token);
		assertTrue(lista.contains("cliente@autobots.com"));
		assertFalse(lista.contains("admin@autobots.com"));
		assertFalse(lista.contains("gerente@autobots.com"));

		// somente leitura: não altera nem o próprio cadastro
		assertEquals(403, status(HttpMethod.PUT, "/usuario/atualizar", token,
				"{\"id\":" + eu + ",\"perfis\":[\"ADMINISTRADOR\"]}"));
		assertEquals(403, status(HttpMethod.PUT, "/usuario/atualizar", token, "{\"id\":" + eu + ",\"nome\":\"X\"}"));
		assertEquals(403, status(HttpMethod.POST, "/usuario/" + eu + "/email/cadastro", token,
				"{\"endereco\":\"novo@exemplo.com\"}"));
		assertEquals(403, status(HttpMethod.DELETE, "/usuario/excluir/" + eu, token, null));
		assertEquals(403, status(HttpMethod.POST, "/usuario/cadastro", token,
				usuarioJson("Novo", "\"CLIENTE\"", "x" + sufixo(), "segredo123", "")));

		// sem acesso ao catálogo, empresas e demais operações
		assertEquals(403, get("/mercadoria/lista", token));
		assertEquals(403, get("/servico/lista", token));
		assertEquals(403, get("/empresa/lista", token));
		assertEquals(403, status(HttpMethod.POST, "/mercadoria/cadastro", token, "{}"));
		assertEquals(403, status(HttpMethod.POST, "/venda/cadastro", token, "{}"));
	}

	@Test
	void clienteSoVeAsVendasEmQueFoiOConsumidor() throws Exception {
		String token = cliente();
		long mercadoria = mercadoriaRepositorio.findAll().get(0).getId();

		// as vendas da carga inicial são dele
		assertEquals(200, get("/venda/lista", token));
		assertTrue(corpoDe("/venda/lista", token).contains("1234698745"));

		// venda de outro cliente: nem pela lista, nem pelo id
		long outroCliente = cadastrarUsuario(admin(), "\"CLIENTE\"", "outro" + sufixo(), "segredo123");
		String identificacao = "ALHEIA-" + sufixo();
		MockHttpServletResponse alheia = enviar(HttpMethod.POST, "/venda/cadastro", admin(),
				"{\"identificacao\":\"" + identificacao + "\",\"cliente\":{\"id\":" + outroCliente
						+ "},\"funcionario\":{\"id\":" + idDe("dompedrovendedor") + "},\"mercadorias\":[{\"id\":"
						+ mercadoria + "}]}");
		assertEquals(201, alheia.getStatus());
		String local = alheia.getHeader(HttpHeaders.LOCATION);
		assertEquals(403, get(local.substring(local.indexOf("/venda/")), token));
		assertFalse(corpoDe("/venda/lista", token).contains(identificacao));

		// veículos: só os seus
		assertTrue(corpoDe("/veiculo/lista", token).contains("ABC-0000"));
		assertEquals(403, status(HttpMethod.POST, "/veiculo/cadastro", token, "{}"));
	}

	// ------------------------------------------------------------------ FORNECEDOR

	@Test
	void fornecedorAutenticaMasNaoTemAutorizacaoNenhuma() throws Exception {
		String token = entrar("dompedrofornecedor", "fornecedor123");
		assertEquals(403, get("/mercadoria/lista", token));
		assertEquals(403, get("/servico/lista", token));
		assertEquals(403, get("/empresa/lista", token));
		assertEquals(403, status(HttpMethod.POST, "/usuario/cadastro", token,
				usuarioJson("Novo", "\"CLIENTE\"", "x" + sufixo(), "segredo123", "")));
		assertEquals(403, get("/usuario/" + idDe("admin"), token));
	}
}
