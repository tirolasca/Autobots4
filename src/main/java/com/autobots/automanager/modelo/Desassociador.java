package com.autobots.automanager.modelo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.repositorios.EmpresaRepositorio;
import com.autobots.automanager.repositorios.UsuarioRepositorio;

/**
 * Remove as associacoes de um registro (empresas e fornecedores) antes de ele
 * ser excluido, evitando violacao de chave estrangeira nas tabelas de juncao.
 */
@Component
@Transactional
public class Desassociador {
	private final EmpresaRepositorio empresaRepositorio;
	private final UsuarioRepositorio usuarioRepositorio;

	public Desassociador(EmpresaRepositorio empresaRepositorio, UsuarioRepositorio usuarioRepositorio) {
		this.empresaRepositorio = empresaRepositorio;
		this.usuarioRepositorio = usuarioRepositorio;
	}

	public void desassociarUsuario(Long id) {
		for (Empresa empresa : empresaRepositorio.buscarPorUsuario(id)) {
			empresa.getUsuarios().removeIf(item -> id.equals(item.getId()));
			empresaRepositorio.saveAndFlush(empresa);
		}
	}

	public void desassociarMercadoria(Long id) {
		for (Empresa empresa : empresaRepositorio.buscarPorMercadoria(id)) {
			empresa.getMercadorias().removeIf(item -> id.equals(item.getId()));
			empresaRepositorio.saveAndFlush(empresa);
		}
		for (Usuario fornecedor : usuarioRepositorio.buscarPorMercadoria(id)) {
			fornecedor.getMercadorias().removeIf(item -> id.equals(item.getId()));
			usuarioRepositorio.saveAndFlush(fornecedor);
		}
	}

	public void desassociarServico(Long id) {
		for (Empresa empresa : empresaRepositorio.buscarPorServico(id)) {
			empresa.getServicos().removeIf(item -> id.equals(item.getId()));
			empresaRepositorio.saveAndFlush(empresa);
		}
	}

	public void desassociarVenda(Long id) {
		for (Empresa empresa : empresaRepositorio.buscarPorVenda(id)) {
			empresa.getVendas().removeIf(item -> id.equals(item.getId()));
			empresaRepositorio.saveAndFlush(empresa);
		}
	}
}
