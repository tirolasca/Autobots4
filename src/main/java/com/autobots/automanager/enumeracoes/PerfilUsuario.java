package com.autobots.automanager.enumeracoes;

/**
 * Perfis de usuário. ADMINISTRADOR, GERENTE, VENDEDOR e CLIENTE seguem o padrão
 * de autorizações sugerido pelos investidores (Tabela 1 da ATVIV). FORNECEDOR
 * é um papel de negócio (quem fornece mercadorias) e não recebe nenhuma
 * autorização de acesso à API.
 */
public enum PerfilUsuario {
	ADMINISTRADOR, GERENTE, VENDEDOR, CLIENTE, FORNECEDOR
}
