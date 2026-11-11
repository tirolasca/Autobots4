package com.autobots.automanager.entidades;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
public class Venda {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private Date cadastro;
	@Column(nullable = false, unique = true)
	private String identificacao;
	@ManyToOne(fetch = FetchType.EAGER)
	@JsonIgnoreProperties({ "vendas", "veiculos", "mercadorias", "credenciais", "documentos", "emails", "telefones",
			"endereco" })
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Usuario cliente;
	@ManyToOne(fetch = FetchType.EAGER)
	@JsonIgnoreProperties({ "vendas", "veiculos", "mercadorias", "credenciais", "documentos", "emails", "telefones",
			"endereco" })
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Usuario funcionario;
	/** Uma mercadoria do catalogo pode aparecer em varias vendas. */
	@ManyToMany(fetch = FetchType.EAGER)
	private Set<Mercadoria> mercadorias = new HashSet<>();
	/** Um servico do catalogo pode aparecer em varias vendas. */
	@ManyToMany(fetch = FetchType.EAGER)
	private Set<Servico> servicos = new HashSet<>();
	@ManyToOne(fetch = FetchType.EAGER)
	@JsonIgnoreProperties({ "proprietario", "vendas" })
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Veiculo veiculo;
}
