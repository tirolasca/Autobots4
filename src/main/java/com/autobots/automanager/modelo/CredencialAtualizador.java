package com.autobots.automanager.modelo;

import com.autobots.automanager.entidades.Credencial;
import com.autobots.automanager.entidades.CredencialCodigoBarra;
import com.autobots.automanager.entidades.CredencialUsuarioSenha;

public class CredencialAtualizador {
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	/** Indica se as duas credenciais sao do mesmo subtipo (so assim podem ser atualizadas). */
	public boolean mesmoTipo(Credencial credencial, Credencial atualizacao) {
		return (credencial instanceof CredencialUsuarioSenha && atualizacao instanceof CredencialUsuarioSenha)
				|| (credencial instanceof CredencialCodigoBarra && atualizacao instanceof CredencialCodigoBarra);
	}

	public void atualizar(Credencial credencial, Credencial atualizacao) {
		if (credencial == null || atualizacao == null || !mesmoTipo(credencial, atualizacao)) {
			return;
		}
		if (atualizacao.getInativo() != null) {
			credencial.setInativo(atualizacao.getInativo());
		}
		if (atualizacao.getUltimoAcesso() != null) {
			credencial.setUltimoAcesso(atualizacao.getUltimoAcesso());
		}
		if (credencial instanceof CredencialUsuarioSenha atual
				&& atualizacao instanceof CredencialUsuarioSenha nova) {
			if (!verificador.verificar(nova.getNomeUsuario())) {
				atual.setNomeUsuario(nova.getNomeUsuario());
			}
			if (!verificador.verificar(nova.getSenha())) {
				atual.setSenha(nova.getSenha());
			}
		}
		if (credencial instanceof CredencialCodigoBarra atual
				&& atualizacao instanceof CredencialCodigoBarra nova) {
			if (nova.getCodigo() != null) {
				atual.setCodigo(nova.getCodigo());
			}
		}
	}
}
