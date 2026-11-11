package com.autobots.automanager.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Gera e valida os tokens JWT (assinatura HMAC-SHA256 com segredo simétrico).
 * O nome de usuário vai no campo padrão "sub". Os perfis vão na reivindicação
 * "perfis" apenas para consulta do cliente: a API nunca confia nela e, a cada
 * requisição, relê os perfis atuais do usuário no banco.
 */
@Component
public class ProvedorJwt {
	private static final int TAMANHO_MINIMO_SEGREDO = 32;

	private final JwtEncoder codificador;
	private final JwtDecoder decodificador;
	private final Duration duracao;

	public ProvedorJwt(@Value("${jwt.secret}") String segredo, @Value("${jwt.expiration}") long duracaoEmMilissegundos) {
		byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
			throw new IllegalStateException(
					"A propriedade jwt.secret deve ter ao menos " + TAMANHO_MINIMO_SEGREDO + " caracteres");
		}
		SecretKey chave = new SecretKeySpec(bytes, "HmacSHA256");
		this.codificador = new NimbusJwtEncoder(new ImmutableSecret<>(chave));
		this.decodificador = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
		this.duracao = Duration.ofMillis(duracaoEmMilissegundos);
	}

	/** Duração padrão dos tokens emitidos, em segundos. */
	public long getDuracaoEmSegundos() {
		return duracao.toSeconds();
	}

	public String gerar(String nomeUsuario, Collection<String> perfis) {
		return gerar(nomeUsuario, perfis, duracao);
	}

	/** Permite informar a validade; duração negativa gera um token já expirado (útil em testes). */
	public String gerar(String nomeUsuario, Collection<String> perfis, Duration validade) {
		Instant agora = Instant.now();
		JwtClaimsSet reivindicacoes = JwtClaimsSet.builder().subject(nomeUsuario).issuedAt(agora)
				.expiresAt(agora.plus(validade)).claim("perfis", List.copyOf(perfis)).build();
		JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
		return codificador.encode(JwtEncoderParameters.from(cabecalho, reivindicacoes)).getTokenValue();
	}

	/**
	 * Valida assinatura e expiração do token. Devolve o nome de usuário (campo
	 * "sub") ou null quando o token é inválido, adulterado ou expirou.
	 */
	public String validarEObterNomeUsuario(String token) {
		try {
			Jwt jwt = decodificador.decode(token);
			String nomeUsuario = jwt.getSubject();
			return (nomeUsuario == null || nomeUsuario.isBlank()) ? null : nomeUsuario;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
