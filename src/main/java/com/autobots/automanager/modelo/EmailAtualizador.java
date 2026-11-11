package com.autobots.automanager.modelo;

import java.util.Set;

import com.autobots.automanager.entidades.Email;

public class EmailAtualizador {
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public void atualizar(Email email, Email atualizacao) {
		if (email == null || atualizacao == null) {
			return;
		}
		if (!verificador.verificar(atualizacao.getEndereco())) {
			email.setEndereco(atualizacao.getEndereco());
		}
	}

	/** Atualiza apenas os e-mails ja existentes, casando pelo id. */
	public void atualizar(Set<Email> emails, Set<Email> atualizacoes) {
		if (emails == null || atualizacoes == null) {
			return;
		}
		for (Email atualizacao : atualizacoes) {
			if (atualizacao.getId() == null) {
				continue;
			}
			for (Email email : emails) {
				if (atualizacao.getId().equals(email.getId())) {
					atualizar(email, atualizacao);
				}
			}
		}
	}
}
