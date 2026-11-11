package com.autobots.automanager.adaptadores;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.autobots.automanager.entidades.CredencialUsuarioSenha;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.enumeracoes.PerfilUsuario;

/**
 * Usuário autenticado, como o Spring Security o enxerga. Guarda só os dados
 * necessários (id, nome de usuário, hash da senha e perfis), sem referência às
 * entidades JPA. Cada perfil vira a autoridade ROLE_&lt;PERFIL&gt;, por exemplo
 * ROLE_GERENTE.
 */
public class UserDetailsImpl implements UserDetails {
	private static final long serialVersionUID = 1L;

	private final Long usuarioId;
	private final String nomeUsuario;
	private final String senha;
	private final boolean ativo;
	private final Set<PerfilUsuario> perfis;

	public UserDetailsImpl(Usuario usuario, CredencialUsuarioSenha credencial) {
		this.usuarioId = usuario.getId();
		this.nomeUsuario = credencial.getNomeUsuario();
		this.senha = credencial.getSenha();
		this.ativo = !Boolean.TRUE.equals(credencial.getInativo());
		this.perfis = Collections.unmodifiableSet(new HashSet<>(usuario.getPerfis()));
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Set<PerfilUsuario> getPerfis() {
		return perfis;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return perfis.stream().map(perfil -> new SimpleGrantedAuthority("ROLE_" + perfil.name())).toList();
	}

	@Override
	public String getPassword() {
		return senha;
	}

	@Override
	public String getUsername() {
		return nomeUsuario;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	/** Credenciais marcadas como inativas não conseguem autenticar. */
	@Override
	public boolean isEnabled() {
		return ativo;
	}
}
