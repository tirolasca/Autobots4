package com.autobots.automanager;

import java.util.Date;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.autobots.automanager.entidades.CredencialUsuarioSenha;
import com.autobots.automanager.entidades.Documento;
import com.autobots.automanager.entidades.Email;
import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.entidades.Endereco;
import com.autobots.automanager.entidades.Mercadoria;
import com.autobots.automanager.entidades.Servico;
import com.autobots.automanager.entidades.Telefone;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.entidades.Veiculo;
import com.autobots.automanager.entidades.Venda;
import com.autobots.automanager.enumeracoes.PerfilUsuario;
import com.autobots.automanager.enumeracoes.TipoDocumento;
import com.autobots.automanager.enumeracoes.TipoVeiculo;
import com.autobots.automanager.repositorios.EmpresaRepositorio;
import com.autobots.automanager.repositorios.MercadoriaRepositorio;
import com.autobots.automanager.repositorios.ServicoRepositorio;
import com.autobots.automanager.repositorios.UsuarioRepositorio;
import com.autobots.automanager.repositorios.VeiculoRepositorio;
import com.autobots.automanager.repositorios.VendaRepositorio;

@SpringBootApplication
public class AutomanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutomanagerApplication.class, args);
	}

	/** Carga inicial de dados de exemplo: uma empresa completa, com usuários, estoque e vendas. */
	@Component
	public static class Runner implements ApplicationRunner {
		private final EmpresaRepositorio empresaRepositorio;
		private final UsuarioRepositorio usuarioRepositorio;
		private final MercadoriaRepositorio mercadoriaRepositorio;
		private final ServicoRepositorio servicoRepositorio;
		private final VeiculoRepositorio veiculoRepositorio;
		private final VendaRepositorio vendaRepositorio;
		private final PasswordEncoder codificador;

		public Runner(EmpresaRepositorio empresaRepositorio, UsuarioRepositorio usuarioRepositorio,
				MercadoriaRepositorio mercadoriaRepositorio, ServicoRepositorio servicoRepositorio,
				VeiculoRepositorio veiculoRepositorio, VendaRepositorio vendaRepositorio,
				PasswordEncoder codificador) {
			this.empresaRepositorio = empresaRepositorio;
			this.usuarioRepositorio = usuarioRepositorio;
			this.mercadoriaRepositorio = mercadoriaRepositorio;
			this.servicoRepositorio = servicoRepositorio;
			this.veiculoRepositorio = veiculoRepositorio;
			this.vendaRepositorio = vendaRepositorio;
			this.codificador = codificador;
		}

		private static Endereco endereco(String estado, String cidade, String bairro, String rua, String numero,
				String codigoPostal) {
			Endereco endereco = new Endereco();
			endereco.setEstado(estado);
			endereco.setCidade(cidade);
			endereco.setBairro(bairro);
			endereco.setRua(rua);
			endereco.setNumero(numero);
			endereco.setCodigoPostal(codigoPostal);
			return endereco;
		}

		private static Telefone telefone(String ddd, String numero) {
			Telefone telefone = new Telefone();
			telefone.setDdd(ddd);
			telefone.setNumero(numero);
			return telefone;
		}

		private static Email email(String enderecoEmail) {
			Email email = new Email();
			email.setEndereco(enderecoEmail);
			return email;
		}

		private static Documento documento(TipoDocumento tipo, String numero) {
			Documento documento = new Documento();
			documento.setTipo(tipo);
			documento.setNumero(numero);
			documento.setDataEmissao(new Date());
			return documento;
		}

		/** A senha é gravada como hash BCrypt, nunca em texto puro. */
		private CredencialUsuarioSenha credencial(String nomeUsuario, String senha) {
			CredencialUsuarioSenha credencial = new CredencialUsuarioSenha();
			credencial.setNomeUsuario(nomeUsuario);
			credencial.setSenha(codificador.encode(senha));
			credencial.setCriacao(new Date());
			credencial.setUltimoAcesso(new Date());
			credencial.setInativo(false);
			return credencial;
		}

		private static Servico servico(String nome, double valor, String descricao) {
			Servico servico = new Servico();
			servico.setNome(nome);
			servico.setValor(valor);
			servico.setDescricao(descricao);
			return servico;
		}

		@Override
		@Transactional
		public void run(ApplicationArguments args) throws Exception {
			if (empresaRepositorio.count() > 0) {
				return;
			}
			Date agora = new Date();
			Date validade = new Date(agora.getTime() + 365L * 24 * 60 * 60 * 1000);

			// Empresa
			Empresa empresa = new Empresa();
			empresa.setRazaoSocial("Car service toyota ltda");
			empresa.setNomeFantasia("Car service manutenção veicular");
			empresa.setCadastro(agora);
			empresa.setEndereco(endereco("São Paulo", "São Paulo", "Centro", "Av. São João", "00", "01035-000"));
			empresa.getTelefones().add(telefone("011", "986454527"));
			empresa = empresaRepositorio.save(empresa);

			// Administrador
			Usuario administrador = new Usuario();
			administrador.setNome("Administrador do sistema");
			administrador.setNomeSocial("Admin");
			administrador.getPerfis().add(PerfilUsuario.ADMINISTRADOR);
			administrador.getEmails().add(email("admin@autobots.com"));
			administrador.getCredenciais().add(credencial("admin", "admin123"));
			administrador = usuarioRepositorio.save(administrador);

			// Gerente
			Usuario gerente = new Usuario();
			gerente.setNome("Gerente da loja");
			gerente.setNomeSocial("Gerente");
			gerente.getPerfis().add(PerfilUsuario.GERENTE);
			gerente.getEmails().add(email("gerente@autobots.com"));
			gerente.getCredenciais().add(credencial("gerente", "gerente123"));
			gerente = usuarioRepositorio.save(gerente);

			// Vendedor
			Usuario funcionario = new Usuario();
			funcionario.setNome("Pedro Alcântara de Bragança e Bourbon");
			funcionario.setNomeSocial("Dom Pedro");
			funcionario.getPerfis().add(PerfilUsuario.VENDEDOR);
			funcionario.getEmails().add(email("vendedor@autobots.com"));
			funcionario.setEndereco(endereco("São Paulo", "São Paulo", "Jardins", "Av. São Gabriel", "00", "01435-001"));
			funcionario.getTelefones().add(telefone("011", "9854633728"));
			funcionario.getDocumentos().add(documento(TipoDocumento.CPF, "85647381922"));
			funcionario.getCredenciais().add(credencial("dompedrovendedor", "vendedor123"));
			funcionario = usuarioRepositorio.save(funcionario);

			// Fornecedor
			Usuario fornecedor = new Usuario();
			fornecedor.setNome("Componentes varejo de partes automotivas ltda");
			fornecedor.setNomeSocial("Loja do carro, vendas de componentes automotivos");
			fornecedor.getPerfis().add(PerfilUsuario.FORNECEDOR);
			fornecedor.getEmails().add(email("fornecedor@autobots.com"));
			fornecedor.setEndereco(endereco("Rio de Janeiro", "Rio de Janeiro", "Centro", "Av. República do Chile",
					"00", "20031-170"));
			fornecedor.getDocumentos().add(documento(TipoDocumento.CNPJ, "00014556000100"));
			fornecedor.getCredenciais().add(credencial("dompedrofornecedor", "fornecedor123"));
			fornecedor = usuarioRepositorio.save(fornecedor);

			// Cliente
			Usuario cliente = new Usuario();
			cliente.setNome("Pedro Alcântara de Bragança e Bourbon");
			cliente.setNomeSocial("Dom Pedro cliente");
			cliente.getPerfis().add(PerfilUsuario.CLIENTE);
			cliente.getEmails().add(email("cliente@autobots.com"));
			cliente.setEndereco(endereco("São Paulo", "São José dos Campos", "Centro", "Av. Dr. Nelson D'Ávila",
					"00", "12245-070"));
			cliente.getDocumentos().add(documento(TipoDocumento.CPF, "12584698533"));
			cliente.getCredenciais().add(credencial("dompedrocliente", "cliente123"));
			cliente = usuarioRepositorio.save(cliente);

			// Estoque e catálogo de serviços
			Mercadoria rodaLigaLeve = new Mercadoria();
			rodaLigaLeve.setNome("Roda de liga leve modelo toyota etios");
			rodaLigaLeve.setDescricao("Roda de liga leve original de fábrica da Toyota para modelos do tipo hatch");
			rodaLigaLeve.setCadastro(agora);
			rodaLigaLeve.setFabricao(agora);
			rodaLigaLeve.setValidade(validade);
			rodaLigaLeve.setQuantidade(30L);
			rodaLigaLeve.setValor(300.0);
			rodaLigaLeve = mercadoriaRepositorio.save(rodaLigaLeve);
			fornecedor.getMercadorias().add(rodaLigaLeve);

			Servico trocaRodas = servicoRepositorio
					.save(servico("Troca de rodas", 50.0, "Troca das rodas do carro por novas"));
			Servico alinhamento = servicoRepositorio
					.save(servico("Alinhamento de rodas", 50.0, "Alinhamento das rodas do carro"));
			Servico balanceamento = servicoRepositorio
					.save(servico("Balanceamento de rodas", 30.0, "Balanceamento das rodas do carro"));

			// Veículo do cliente
			Veiculo veiculo = new Veiculo();
			veiculo.setTipo(TipoVeiculo.SUV);
			veiculo.setModelo("corolla-cross");
			veiculo.setPlaca("ABC-0000");
			veiculo.setProprietario(cliente);
			veiculo = veiculoRepositorio.save(veiculo);

			// Vendas
			Venda primeiraVenda = new Venda();
			primeiraVenda.setIdentificacao("1234698745");
			primeiraVenda.setCadastro(agora);
			primeiraVenda.setCliente(cliente);
			primeiraVenda.setFuncionario(funcionario);
			primeiraVenda.setVeiculo(veiculo);
			primeiraVenda.getMercadorias().add(rodaLigaLeve);
			primeiraVenda.getServicos().add(trocaRodas);
			primeiraVenda.getServicos().add(alinhamento);
			primeiraVenda = vendaRepositorio.save(primeiraVenda);

			Venda segundaVenda = new Venda();
			segundaVenda.setIdentificacao("1234698749");
			segundaVenda.setCadastro(agora);
			segundaVenda.setCliente(cliente);
			segundaVenda.setFuncionario(funcionario);
			segundaVenda.setVeiculo(veiculo);
			segundaVenda.getMercadorias().add(rodaLigaLeve);
			segundaVenda.getServicos().add(balanceamento);
			segundaVenda.getServicos().add(alinhamento);
			segundaVenda = vendaRepositorio.save(segundaVenda);

			// Associações com a empresa
			empresa.getUsuarios().add(administrador);
			empresa.getUsuarios().add(gerente);
			empresa.getUsuarios().add(funcionario);
			empresa.getUsuarios().add(fornecedor);
			empresa.getUsuarios().add(cliente);
			empresa.getMercadorias().add(rodaLigaLeve);
			empresa.getServicos().add(trocaRodas);
			empresa.getServicos().add(alinhamento);
			empresa.getServicos().add(balanceamento);
			empresa.getVendas().add(primeiraVenda);
			empresa.getVendas().add(segundaVenda);
			empresaRepositorio.save(empresa);
		}
	}

}
