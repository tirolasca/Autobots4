package com.autobots.automanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.autobots.automanager.jwt.ProvedorJwt;

/** Testes do gerador/validador de tokens, sem subir o Spring. */
class ProvedorJwtTests {
	private static final String SEGREDO = "segredo-de-teste-com-mais-de-trinta-e-dois-caracteres";

	private final ProvedorJwt provedor = new ProvedorJwt(SEGREDO, 60_000);

	@Test
	void tokenGeradoEValidoEDevolveONomeDeUsuario() {
		String token = provedor.gerar("maria", List.of("CLIENTE"));
		assertEquals("maria", provedor.validarEObterNomeUsuario(token));
	}

	@Test
	void tokenExpiradoENulo() {
		String token = provedor.gerar("maria", List.of("CLIENTE"), Duration.ofMinutes(-5));
		assertNull(provedor.validarEObterNomeUsuario(token));
	}

	@Test
	void tokenAssinadoComOutroSegredoENulo() {
		ProvedorJwt outro = new ProvedorJwt("outro-segredo-de-teste-tambem-bem-comprido-123", 60_000);
		String token = outro.gerar("maria", List.of("CLIENTE"));
		assertNull(provedor.validarEObterNomeUsuario(token));
	}

	@Test
	void tokenAdulteradoOuLixoENulo() {
		String token = provedor.gerar("maria", List.of("CLIENTE"));
		// troca um caractere do meio da assinatura (os últimos têm bits de preenchimento)
		int posicao = token.length() - 10;
		char novo = token.charAt(posicao) == 'A' ? 'B' : 'A';
		String adulterado = token.substring(0, posicao) + novo + token.substring(posicao + 1);
		assertNotEquals(token, adulterado);
		assertNull(provedor.validarEObterNomeUsuario(adulterado));
		assertNull(provedor.validarEObterNomeUsuario("isto.nao.e-um-jwt"));
		assertNull(provedor.validarEObterNomeUsuario(""));
	}

	@Test
	void segredoCurtoEhRecusado() {
		assertThrows(IllegalStateException.class, () -> new ProvedorJwt("curto", 60_000));
	}

	@Test
	void duracaoEmSegundosVemDaConfiguracao() {
		assertEquals(60L, provedor.getDuracaoEmSegundos());
	}
}
