package com.autobots.automanager.adaptadores;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autobots.automanager.entidades.CredencialUsuarioSenha;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.repositorios.CredencialUsuarioSenhaRepositorio;
import com.autobots.automanager.repositorios.UsuarioRepositorio;

/**
 * Localiza o usuário pelo nome de usuário da credencial USUARIO_SENHA. Só esse
 * tipo de credencial autentica; credenciais por código de barras não.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
	private final CredencialUsuarioSenhaRepositorio credenciais;
	private final UsuarioRepositorio usuarios;

	public UserDetailsServiceImpl(CredencialUsuarioSenhaRepositorio credenciais, UsuarioRepositorio usuarios) {
		this.credenciais = credenciais;
		this.usuarios = usuarios;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String nomeUsuario) throws UsernameNotFoundException {
		CredencialUsuarioSenha credencial = credenciais.findByNomeUsuario(nomeUsuario)
				.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
		List<Usuario> donos = usuarios.buscarPorCredencialId(credencial.getId());
		if (donos.isEmpty()) {
			throw new UsernameNotFoundException("Usuário não encontrado");
		}
		return new UserDetailsImpl(donos.get(0), credencial);
	}
}
