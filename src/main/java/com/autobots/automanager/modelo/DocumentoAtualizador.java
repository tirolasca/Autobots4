package com.autobots.automanager.modelo;

import java.util.Set;

import com.autobots.automanager.entidades.Documento;

public class DocumentoAtualizador {
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public void atualizar(Documento documento, Documento atualizacao) {
		if (documento == null || atualizacao == null) {
			return;
		}
		if (atualizacao.getTipo() != null) {
			documento.setTipo(atualizacao.getTipo());
		}
		if (atualizacao.getDataEmissao() != null) {
			documento.setDataEmissao(atualizacao.getDataEmissao());
		}
		if (!verificador.verificar(atualizacao.getNumero())) {
			documento.setNumero(atualizacao.getNumero());
		}
	}

	/** Atualiza apenas os documentos ja existentes, casando pelo id. */
	public void atualizar(Set<Documento> documentos, Set<Documento> atualizacoes) {
		if (documentos == null || atualizacoes == null) {
			return;
		}
		for (Documento atualizacao : atualizacoes) {
			if (atualizacao.getId() == null) {
				continue;
			}
			for (Documento documento : documentos) {
				if (atualizacao.getId().equals(documento.getId())) {
					atualizar(documento, atualizacao);
				}
			}
		}
	}
}
