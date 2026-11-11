package com.autobots.automanager.modelo;

import com.autobots.automanager.entidades.Servico;

public class ServicoAtualizador {
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public void atualizar(Servico servico, Servico atualizacao) {
		if (servico == null || atualizacao == null) {
			return;
		}
		if (!verificador.verificar(atualizacao.getNome())) {
			servico.setNome(atualizacao.getNome());
		}
		if (!verificador.verificar(atualizacao.getDescricao())) {
			servico.setDescricao(atualizacao.getDescricao());
		}
		if (atualizacao.getValor() != null) {
			servico.setValor(atualizacao.getValor());
		}
	}
}
