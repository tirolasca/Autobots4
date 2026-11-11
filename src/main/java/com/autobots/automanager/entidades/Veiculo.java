package com.autobots.automanager.entidades;

import java.util.HashSet;
import java.util.Set;

import com.autobots.automanager.enumeracoes.TipoVeiculo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
public class Veiculo {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoVeiculo tipo;
	@Column(nullable = false)
	private String modelo;
	@Column(nullable = false)
	private String placa;
	@ManyToOne(fetch = FetchType.EAGER)
	@JsonIgnoreProperties({ "veiculos", "vendas", "mercadorias", "credenciais", "documentos", "emails", "telefones",
			"endereco" })
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Usuario proprietario;
	/** Lado inverso de Venda.veiculo: somente leitura. */
	@OneToMany(mappedBy = "veiculo", fetch = FetchType.EAGER)
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	@JsonIgnoreProperties({ "veiculo", "cliente", "funcionario" })
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Set<Venda> vendas = new HashSet<>();
}
