package com.autobots.automanager.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autobots.automanager.entidades.Empresa;

public interface EmpresaRepositorio extends JpaRepository<Empresa, Long> {

	@Query("select e from Empresa e join e.usuarios u where u.id = :id")
	List<Empresa> buscarPorUsuario(@Param("id") Long id);

	@Query("select e from Empresa e join e.mercadorias m where m.id = :id")
	List<Empresa> buscarPorMercadoria(@Param("id") Long id);

	@Query("select e from Empresa e join e.servicos s where s.id = :id")
	List<Empresa> buscarPorServico(@Param("id") Long id);

	@Query("select e from Empresa e join e.vendas v where v.id = :id")
	List<Empresa> buscarPorVenda(@Param("id") Long id);
}
