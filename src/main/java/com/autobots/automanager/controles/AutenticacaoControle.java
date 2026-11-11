package com.autobots.automanager.controles;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autobots.automanager.adaptadores.UserDetailsServiceImpl;
import com.autobots.automanager.jwt.ProvedorJwt;
import com.autobots.automanager.repositorios.CredencialUsuarioSenhaRepositorio;

/**
 * Autenticação: troca nome de usuário e senha por um token JWT. O token vai no
 * cabeçalho "Authorization: Bearer ..." da resposta (e também no corpo) e deve
 * ser enviado do mesmo modo nas demais requisições.
 *
 * POST /login {"nomeUsuario": "admin", "senha": "..."}
 */
@RestController
@RequestMapping("/login")
public class AutenticacaoControle {
	private final UserDetailsServiceImpl servico;
	private final PasswordEncoder codificador;
	private final ProvedorJwt provedorJwt;
	private final CredencialUsuarioSenhaRepositorio credenciais;
	/** Usado quando o usuário não existe, para que a resposta demore o mesmo que uma senha errada. */
	private final String hashFalso;

	public AutenticacaoControle(UserDetailsServiceImpl servico, PasswordEncoder codificador, ProvedorJwt provedorJwt,
			CredencialUsuarioSenhaRepositorio credenciais) {
		this.servico = servico;
		this.codificador = codificador;
		this.provedorJwt = provedorJwt;
		this.credenciais = credenciais;
		this.hashFalso = codificador.encode("senha-inexistente");
	}

	public record DadosLogin(String nomeUsuario, String senha) {
	}

	private ResponseEntity<Map<String, Object>> recusar(HttpStatus status, String mensagem) {
		Map<String, Object> corpo = new LinkedHashMap<>();
		corpo.put("status", status.value());
		corpo.put("erro", mensagem);
		return ResponseEntity.status(status).body(corpo);
	}

	@PostMapping
	@Transactional
	public ResponseEntity<Map<String, Object>> entrar(@RequestBody DadosLogin dados) {
		if (dados == null || dados.nomeUsuario() == null || dados.nomeUsuario().isBlank() || dados.senha() == null
				|| dados.senha().isEmpty()) {
			return recusar(HttpStatus.BAD_REQUEST, "Informe nomeUsuario e senha");
		}
		UserDetails usuario;
		try {
			usuario = servico.loadUserByUsername(dados.nomeUsuario());
		} catch (UsernameNotFoundException e) {
			codificador.matches(dados.senha(), hashFalso);
			return recusar(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
		}
		if (!usuario.isEnabled() || !codificador.matches(dados.senha(), usuario.getPassword())) {
			return recusar(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
		}

		credenciais.findByNomeUsuario(usuario.getUsername()).ifPresent(credencial -> {
			credencial.setUltimoAcesso(new Date());
			credenciais.save(credencial);
		});

		String token = provedorJwt.gerar(usuario.getUsername(),
				usuario.getAuthorities().stream().map(GrantedAuthority::getAuthority)
						.map(autoridade -> autoridade.substring("ROLE_".length())).toList());
		Map<String, Object> corpo = new LinkedHashMap<>();
		corpo.put("token", token);
		corpo.put("tipo", "Bearer");
		corpo.put("expiraEmSegundos", provedorJwt.getDuracaoEmSegundos());
		return ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, "Bearer " + token).body(corpo);
	}
}
