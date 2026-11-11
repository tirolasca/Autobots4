package com.autobots.automanager.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autobots.automanager.entidades.Usuario;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

	/** Usuarios (fornecedores) que fornecem a mercadoria informada. */
	@Query("select u from Usuario u join u.mercadorias m where m.id = :id")
	List<Usuario> buscarPorMercadoria(@Param("id") Long id);

	/** Dono de uma credencial (a credencial nao guarda referencia para o usuario). */
	@Query("select u from Usuario u join u.credenciais c where c.id = :id")
	List<Usuario> buscarPorCredencialId(@Param("id") Long id);
}
