package com.autobots.automanager.entidades;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

import lombok.Data;

/**
 * Credencial de acesso de um usuario. Nos JSONs o subtipo e informado na
 * propriedade "tipo": USUARIO_SENHA ou CODIGO_BARRA.
 */
@Data
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "tipo")
@JsonSubTypes({
		@JsonSubTypes.Type(value = CredencialUsuarioSenha.class, name = "USUARIO_SENHA"),
		@JsonSubTypes.Type(value = CredencialCodigoBarra.class, name = "CODIGO_BARRA") })
public abstract class Credencial {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private Date criacao;
	@Column
	private Date ultimoAcesso;
	@Column(nullable = false)
	private Boolean inativo;

	/** Discriminador exposto no JSON (USUARIO_SENHA ou CODIGO_BARRA); nao e persistido. */
	@JsonProperty("tipo")
	public abstract String getTipo();
}
