package com.autobots.automanager.modelo;

import com.autobots.automanager.entidades.Veiculo;

/** Atualiza os dados proprios do veiculo; o proprietario e tratado pelo controle. */
public class VeiculoAtualizador {
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public void atualizar(Veiculo veiculo, Veiculo atualizacao) {
		if (veiculo == null || atualizacao == null) {
			return;
		}
		if (atualizacao.getTipo() != null) {
			veiculo.setTipo(atualizacao.getTipo());
		}
		if (!verificador.verificar(atualizacao.getModelo())) {
			veiculo.setModelo(atualizacao.getModelo());
		}
		if (!verificador.verificar(atualizacao.getPlaca())) {
			veiculo.setPlaca(atualizacao.getPlaca());
		}
	}
}
