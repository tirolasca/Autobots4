package com.autobots.automanager.modelo;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.autobots.automanager.entidades.Credencial;
import com.autobots.automanager.entidades.CredencialUsuarioSenha;

/**
 * Troca a senha informada em texto puro pelo seu hash BCrypt antes de a
 * credencial ser gravada. O texto puro nunca chega ao banco.
 */
@Component
public class CodificadorCredencial {
	/** Limite do BCrypt: bytes além do 72º seriam ignorados. */
	private static final int TAMANHO_MAXIMO_SENHA = 72;

	private final PasswordEncoder codificador;

	public CodificadorCredencial(PasswordEncoder codificador) {
		this.codificador = codificador;
	}

	/** Só age em credenciais USUARIO_SENHA com senha preenchida; as demais ficam como estão. */
	public void codificar(Credencial credencial) {
		if (credencial instanceof CredencialUsuarioSenha usuarioSenha && usuarioSenha.getSenha() != null
				&& !usuarioSenha.getSenha().isBlank()) {
			if (usuarioSenha.getSenha().getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_SENHA) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"A senha deve ter no máximo " + TAMANHO_MAXIMO_SENHA + " bytes");
			}
			usuarioSenha.setSenha(codificador.encode(usuarioSenha.getSenha()));
		}
	}
}
