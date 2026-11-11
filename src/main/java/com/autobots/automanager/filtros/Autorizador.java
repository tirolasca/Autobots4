package com.autobots.automanager.filtros;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.autobots.automanager.jwt.ProvedorJwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro executado uma vez por requisição. Se houver o cabeçalho
 * "Authorization: Bearer &lt;token&gt;" com um token válido, carrega o usuário
 * (e seus perfis atuais) e o registra como autenticado. Sem token, ou com
 * token inválido/expirado, a requisição segue anônima e a configuração de
 * segurança responde 401 nas rotas protegidas.
 */
public class Autorizador extends OncePerRequestFilter {
	private static final String PREFIXO = "Bearer ";

	private final ProvedorJwt provedorJwt;
	private final UserDetailsService servico;

	public Autorizador(ProvedorJwt provedorJwt, UserDetailsService servico) {
		this.provedorJwt = provedorJwt;
		this.servico = servico;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		String cabecalho = requisicao.getHeader("Authorization");
		if (cabecalho != null && cabecalho.startsWith(PREFIXO)) {
			String token = cabecalho.substring(PREFIXO.length()).trim();
			String nomeUsuario = provedorJwt.validarEObterNomeUsuario(token);
			if (nomeUsuario != null) {
				autenticar(nomeUsuario);
			}
		}
		cadeia.doFilter(requisicao, resposta);
	}

	private void autenticar(String nomeUsuario) {
		try {
			UserDetails usuario = servico.loadUserByUsername(nomeUsuario);
			if (usuario.isEnabled()) {
				SecurityContext contexto = SecurityContextHolder.createEmptyContext();
				contexto.setAuthentication(
						UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities()));
				SecurityContextHolder.setContext(contexto);
			}
		} catch (UsernameNotFoundException e) {
			// usuário removido depois da emissão do token: segue sem autenticação
		}
	}
}
