package com.autobots.automanager.controles;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz violacoes de integridade do banco (campo obrigatorio ausente, valor
 * duplicado em coluna unica) em 409 Conflict, em vez de um 500 generico.
 */
@RestControllerAdvice
public class ControleExcecoes {

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> tratarIntegridade(DataIntegrityViolationException excecao) {
		Map<String, Object> corpo = new LinkedHashMap<>();
		corpo.put("status", HttpStatus.CONFLICT.value());
		corpo.put("erro", "Violação de integridade dos dados");
		corpo.put("detalhe", "Há campo obrigatório ausente ou valor repetido em campo único "
				+ "(por exemplo número de documento, identificação de venda, nome de usuário).");
		return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo);
	}
}
