package com.autobots.automanager.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autobots.automanager.entidades.Venda;

public interface VendaRepositorio extends JpaRepository<Venda, Long> {

	boolean existsByClienteId(Long usuarioId);

	boolean existsByFuncionarioId(Long usuarioId);

	boolean existsByVeiculoId(Long veiculoId);

	@Query("select count(v) from Venda v join v.mercadorias m where m.id = :id")
	long contarPorMercadoria(@Param("id") Long id);

	@Query("select count(v) from Venda v join v.servicos s where s.id = :id")
	long contarPorServico(@Param("id") Long id);
}
