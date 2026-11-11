package com.autobots.automanager.modelo;

import com.autobots.automanager.entidades.Usuario;

/**
 * Atualiza dados do usuario e os itens ja existentes de endereco, documentos,
 * telefones e e-mails. Credenciais e associacoes tem endpoints proprios.
 */
public class UsuarioAtualizador {
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();
	private final EnderecoAtualizador enderecoAtualizador = new EnderecoAtualizador();
	private final DocumentoAtualizador documentoAtualizador = new DocumentoAtualizador();
	private final TelefoneAtualizador telefoneAtualizador = new TelefoneAtualizador();
	private final EmailAtualizador emailAtualizador = new EmailAtualizador();

	public void atualizar(Usuario usuario, Usuario atualizacao) {
		if (!verificador.verificar(atualizacao.getNome())) {
			usuario.setNome(atualizacao.getNome());
		}
		if (!verificador.verificar(atualizacao.getNomeSocial())) {
			usuario.setNomeSocial(atualizacao.getNomeSocial());
		}
		if (atualizacao.getPerfis() != null && !atualizacao.getPerfis().isEmpty()) {
			usuario.getPerfis().clear();
			usuario.getPerfis().addAll(atualizacao.getPerfis());
		}
		if (atualizacao.getEndereco() != null) {
			if (usuario.getEndereco() == null) {
				atualizacao.getEndereco().setId(null);
				usuario.setEndereco(atualizacao.getEndereco());
			} else {
				enderecoAtualizador.atualizar(usuario.getEndereco(), atualizacao.getEndereco());
			}
		}
		documentoAtualizador.atualizar(usuario.getDocumentos(), atualizacao.getDocumentos());
		telefoneAtualizador.atualizar(usuario.getTelefones(), atualizacao.getTelefones());
		emailAtualizador.atualizar(usuario.getEmails(), atualizacao.getEmails());
	}
}
