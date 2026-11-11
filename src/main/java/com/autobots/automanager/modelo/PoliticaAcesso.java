package com.autobots.automanager.modelo;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.autobots.automanager.adaptadores.UserDetailsImpl;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.entidades.Veiculo;
import com.autobots.automanager.entidades.Venda;
import com.autobots.automanager.enumeracoes.PerfilUsuario;
import com.autobots.automanager.repositorios.UsuarioRepositorio;
import com.autobots.automanager.repositorios.VeiculoRepositorio;
import com.autobots.automanager.repositorios.VendaRepositorio;

/**
 * Regras de autorização que dependem dos dados (de quem é o registro, quais
 * perfis ele tem). Os controles as chamam nas expressões de @PreAuthorize pelo
 * nome do bean: {@code @PreAuthorize("@acesso.podeLerUsuario(#id)")}.
 *
 * Tabela 1 (perfis dos usuários e o que cada um pode fazer):
 * <ul>
 * <li>ADMINISTRADOR: tudo, inclusive criar e remover administradores.</li>
 * <li>GERENTE: CRUD de usuários GERENTE, VENDEDOR e CLIENTE; CRUD de serviços,
 * vendas e mercadorias.</li>
 * <li>VENDEDOR: CRUD de usuários CLIENTE; lê serviços e mercadorias; cria
 * vendas feitas por si mesmo e lê as suas.</li>
 * <li>CLIENTE: lê o próprio cadastro e as vendas em que foi o consumidor.</li>
 * </ul>
 * Para um registro que não existe, só o ADMINISTRADOR (e o GERENTE, no caso de
 * vendas) passa pela autorização, e então o controle responde 404. Os demais
 * recebem 403, para não revelar quais ids existem.
 */
@Component("acesso")
public class PoliticaAcesso {
	private static final Set<PerfilUsuario> GERENCIADOS_PELO_GERENTE = EnumSet.of(PerfilUsuario.GERENTE,
			PerfilUsuario.VENDEDOR, PerfilUsuario.CLIENTE);
	private static final Set<PerfilUsuario> GERENCIADOS_PELO_VENDEDOR = EnumSet.of(PerfilUsuario.CLIENTE);

	private final UsuarioRepositorio usuarioRepositorio;
	private final VendaRepositorio vendaRepositorio;
	private final VeiculoRepositorio veiculoRepositorio;

	public PoliticaAcesso(UsuarioRepositorio usuarioRepositorio, VendaRepositorio vendaRepositorio,
			VeiculoRepositorio veiculoRepositorio) {
		this.usuarioRepositorio = usuarioRepositorio;
		this.vendaRepositorio = vendaRepositorio;
		this.veiculoRepositorio = veiculoRepositorio;
	}

	// ---------------------------------------------------------------- quem está logado

	/** Usuário autenticado na requisição corrente, ou null. */
	public UserDetailsImpl usuarioAtual() {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao != null && autenticacao.getPrincipal() instanceof UserDetailsImpl usuario) {
			return usuario;
		}
		return null;
	}

	public boolean temPerfil(PerfilUsuario perfil) {
		UserDetailsImpl atual = usuarioAtual();
		return atual != null && atual.getPerfis().contains(perfil);
	}

	/** ADMINISTRADOR, GERENTE ou VENDEDOR. */
	public boolean ehEquipe() {
		return temPerfil(PerfilUsuario.ADMINISTRADOR) || temPerfil(PerfilUsuario.GERENTE)
				|| temPerfil(PerfilUsuario.VENDEDOR);
	}

	private boolean ehAdministradorOuGerente() {
		return temPerfil(PerfilUsuario.ADMINISTRADOR) || temPerfil(PerfilUsuario.GERENTE);
	}

	private boolean ehOProprio(Usuario usuario) {
		UserDetailsImpl atual = usuarioAtual();
		return atual != null && usuario != null && usuario.getId() != null
				&& usuario.getId().equals(atual.getUsuarioId());
	}

	// ---------------------------------------------------------------- usuários

	/** Perfis de usuário que o usuário logado pode criar, alterar e excluir. */
	private Set<PerfilUsuario> perfisGerenciaveis() {
		Set<PerfilUsuario> perfis = EnumSet.noneOf(PerfilUsuario.class);
		if (temPerfil(PerfilUsuario.ADMINISTRADOR)) {
			perfis.addAll(EnumSet.allOf(PerfilUsuario.class));
		}
		if (temPerfil(PerfilUsuario.GERENTE)) {
			perfis.addAll(GERENCIADOS_PELO_GERENTE);
		}
		if (temPerfil(PerfilUsuario.VENDEDOR)) {
			perfis.addAll(GERENCIADOS_PELO_VENDEDOR);
		}
		return perfis;
	}

	/** Só pode gerenciar quem tem TODOS os perfis dentro do que lhe é permitido. */
	public boolean podeGerenciarPerfis(Collection<PerfilUsuario> perfisDoAlvo) {
		return perfisDoAlvo != null && !perfisDoAlvo.isEmpty() && perfisGerenciaveis().containsAll(perfisDoAlvo);
	}

	public boolean podeGerenciarUsuario(long id) {
		Usuario alvo = usuarioRepositorio.findById(id).orElse(null);
		if (alvo == null) {
			return temPerfil(PerfilUsuario.ADMINISTRADOR);
		}
		return podeGerenciarPerfis(alvo.getPerfis());
	}

	public boolean podeLerUsuario(long id) {
		Usuario alvo = usuarioRepositorio.findById(id).orElse(null);
		if (alvo == null) {
			return temPerfil(PerfilUsuario.ADMINISTRADOR);
		}
		return usuarioVisivel(alvo);
	}

	/** Pode ler quem pode gerenciar e o próprio cadastro. */
	public boolean usuarioVisivel(Usuario alvo) {
		return alvo != null && (ehOProprio(alvo) || podeGerenciarPerfis(alvo.getPerfis()));
	}

	public boolean podeCadastrarUsuario(Usuario novo) {
		if (novo == null || novo.getPerfis() == null || novo.getPerfis().isEmpty()) {
			// o controle responde 400 (perfil obrigatório); cliente e fornecedor nem chegam lá
			return ehEquipe();
		}
		return podeGerenciarPerfis(novo.getPerfis());
	}

	/** Exige poder gerenciar o usuário atual E os perfis novos (evita escalar privilégio). */
	public boolean podeAtualizarUsuario(Usuario atualizacao) {
		if (atualizacao == null || atualizacao.getId() == null) {
			return ehEquipe();
		}
		Usuario alvo = usuarioRepositorio.findById(atualizacao.getId()).orElse(null);
		if (alvo == null) {
			return temPerfil(PerfilUsuario.ADMINISTRADOR);
		}
		if (!podeGerenciarPerfis(alvo.getPerfis())) {
			return false;
		}
		Set<PerfilUsuario> novos = atualizacao.getPerfis();
		return novos == null || novos.isEmpty() || podeGerenciarPerfis(novos);
	}

	// ---------------------------------------------------------------- vendas

	/** ADMINISTRADOR e GERENTE veem todas; VENDEDOR as suas; CLIENTE as que comprou. */
	public boolean vendaVisivel(Venda venda) {
		if (venda == null) {
			return false;
		}
		if (ehAdministradorOuGerente()) {
			return true;
		}
		if (temPerfil(PerfilUsuario.VENDEDOR) && ehOProprio(venda.getFuncionario())) {
			return true;
		}
		return temPerfil(PerfilUsuario.CLIENTE) && ehOProprio(venda.getCliente());
	}

	public boolean podeLerVenda(long id) {
		Venda venda = vendaRepositorio.findById(id).orElse(null);
		if (venda == null) {
			return ehAdministradorOuGerente();
		}
		return vendaVisivel(venda);
	}

	/** VENDEDOR só registra venda em seu próprio nome (funcionario ausente ou igual a ele). */
	public boolean podeCadastrarVenda(Venda corpo) {
		if (ehAdministradorOuGerente()) {
			return true;
		}
		if (!temPerfil(PerfilUsuario.VENDEDOR)) {
			return false;
		}
		if (corpo == null || corpo.getFuncionario() == null || corpo.getFuncionario().getId() == null) {
			return true;
		}
		return ehOProprio(corpo.getFuncionario());
	}

	// ---------------------------------------------------------------- veículos

	/** Equipe vê todos os veículos; o cliente vê os seus. */
	public boolean veiculoVisivel(Veiculo veiculo) {
		if (veiculo == null) {
			return false;
		}
		return ehEquipe() || ehOProprio(veiculo.getProprietario());
	}

	public boolean podeLerVeiculo(long id) {
		Veiculo veiculo = veiculoRepositorio.findById(id).orElse(null);
		if (veiculo == null) {
			return ehEquipe();
		}
		return veiculoVisivel(veiculo);
	}
}
