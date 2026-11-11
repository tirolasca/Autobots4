package com.autobots.automanager.modelo;

import com.autobots.automanager.entidades.Mercadoria;

public class MercadoriaAtualizador {
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public void atualizar(Mercadoria mercadoria, Mercadoria atualizacao) {
		if (mercadoria == null || atualizacao == null) {
			return;
		}
		if (!verificador.verificar(atualizacao.getNome())) {
			mercadoria.setNome(atualizacao.getNome());
		}
		if (!verificador.verificar(atualizacao.getDescricao())) {
			mercadoria.setDescricao(atualizacao.getDescricao());
		}
		if (atualizacao.getValidade() != null) {
			mercadoria.setValidade(atualizacao.getValidade());
		}
		if (atualizacao.getFabricao() != null) {
			mercadoria.setFabricao(atualizacao.getFabricao());
		}
		if (atualizacao.getCadastro() != null) {
			mercadoria.setCadastro(atualizacao.getCadastro());
		}
		if (atualizacao.getQuantidade() != null) {
			mercadoria.setQuantidade(atualizacao.getQuantidade());
		}
		if (atualizacao.getValor() != null) {
			mercadoria.setValor(atualizacao.getValor());
		}
	}
}
