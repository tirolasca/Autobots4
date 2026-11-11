package com.autobots.automanager.modelo;

import com.autobots.automanager.entidades.Empresa;

/**
 * Atualiza dados da empresa e os itens ja existentes de endereco e telefones.
 * Associacoes (usuarios, mercadorias, servicos, vendas) tem endpoints proprios.
 */
public class EmpresaAtualizador {
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();
	private final EnderecoAtualizador enderecoAtualizador = new EnderecoAtualizador();
	private final TelefoneAtualizador telefoneAtualizador = new TelefoneAtualizador();

	public void atualizar(Empresa empresa, Empresa atualizacao) {
		if (!verificador.verificar(atualizacao.getRazaoSocial())) {
			empresa.setRazaoSocial(atualizacao.getRazaoSocial());
		}
		if (!verificador.verificar(atualizacao.getNomeFantasia())) {
			empresa.setNomeFantasia(atualizacao.getNomeFantasia());
		}
		if (atualizacao.getCadastro() != null) {
			empresa.setCadastro(atualizacao.getCadastro());
		}
		if (atualizacao.getEndereco() != null) {
			if (empresa.getEndereco() == null) {
				atualizacao.getEndereco().setId(null);
				empresa.setEndereco(atualizacao.getEndereco());
			} else {
				enderecoAtualizador.atualizar(empresa.getEndereco(), atualizacao.getEndereco());
			}
		}
		telefoneAtualizador.atualizar(empresa.getTelefones(), atualizacao.getTelefones());
	}
}
