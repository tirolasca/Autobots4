package com.autobots.automanager.controles;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Date;
import java.util.List;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.autobots.automanager.entidades.Credencial;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.modelo.CodificadorCredencial;
import com.autobots.automanager.modelo.Desassociador;
import com.autobots.automanager.modelo.PoliticaAcesso;
import com.autobots.automanager.modelo.StringVerificadorNulo;
import com.autobots.automanager.modelo.UsuarioAtualizador;
import com.autobots.automanager.repositorios.UsuarioRepositorio;
import com.autobots.automanager.repositorios.VeiculoRepositorio;
import com.autobots.automanager.repositorios.VendaRepositorio;

@RestController
@Transactional
@RequestMapping("/usuario")
public class UsuarioControle {
	private final UsuarioRepositorio repositorio;
	private final VendaRepositorio vendaRepositorio;
	private final VeiculoRepositorio veiculoRepositorio;
	private final Desassociador desassociador;
	private final PoliticaAcesso acesso;
	private final CodificadorCredencial codificador;
	private final UsuarioAtualizador atualizador = new UsuarioAtualizador();
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public UsuarioControle(UsuarioRepositorio repositorio, VendaRepositorio vendaRepositorio,
			VeiculoRepositorio veiculoRepositorio, Desassociador desassociador, PoliticaAcesso acesso,
			CodificadorCredencial codificador) {
		this.repositorio = repositorio;
		this.vendaRepositorio = vendaRepositorio;
		this.veiculoRepositorio = veiculoRepositorio;
		this.desassociador = desassociador;
		this.acesso = acesso;
		this.codificador = codificador;
	}

	private EntityModel<Usuario> montarModelo(Usuario usuario) {
		long id = usuario.getId();
		Link self = linkTo(methodOn(UsuarioControle.class).obterUsuario(id)).withSelfRel();
		Link lista = linkTo(methodOn(UsuarioControle.class).obterUsuarios()).withRel("usuarios");
		Link documentos = linkTo(methodOn(UsuarioDocumentoControle.class).obterDocumentos(id))
				.withRel("documentos");
		Link emails = linkTo(methodOn(UsuarioEmailControle.class).obterEmails(id)).withRel("emails");
		Link telefones = linkTo(methodOn(UsuarioTelefoneControle.class).obterTelefones(id)).withRel("telefones");
		Link endereco = linkTo(methodOn(UsuarioEnderecoControle.class).obterEndereco(id)).withRel("endereco");
		Link credenciais = linkTo(methodOn(UsuarioCredencialControle.class).obterCredenciais(id))
				.withRel("credenciais");
		Link mercadorias = linkTo(methodOn(UsuarioMercadoriaControle.class).obterMercadorias(id))
				.withRel("mercadorias");
		return EntityModel.of(usuario, self, lista, documentos, emails, telefones, endereco, credenciais,
				mercadorias);
	}

	@PreAuthorize("@acesso.podeLerUsuario(#id)")
	@GetMapping("/{id}")
	public ResponseEntity<EntityModel<Usuario>> obterUsuario(@PathVariable long id) {
		Usuario usuario = repositorio.findById(id).orElse(null);
		if (usuario == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(usuario));
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Usuario>>> obterUsuarios() {
		List<EntityModel<Usuario>> modelos = repositorio.findAll().stream().filter(acesso::usuarioVisivel)
				.map(this::montarModelo).toList();
		Link self = linkTo(methodOn(UsuarioControle.class).obterUsuarios()).withSelfRel();
		return ResponseEntity.ok(CollectionModel.of(modelos, self));
	}

	@PreAuthorize("@acesso.podeCadastrarUsuario(#usuario)")
	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Usuario>> cadastrarUsuario(@RequestBody Usuario usuario) {
		if (verificador.verificar(usuario.getNome())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome do usuário");
		}
		if (usuario.getPerfis() == null || usuario.getPerfis().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Informe ao menos um perfil: ADMINISTRADOR, GERENTE, VENDEDOR, CLIENTE ou FORNECEDOR");
		}
		usuario.setId(null);
		if (usuario.getEndereco() != null) {
			usuario.getEndereco().setId(null);
		}
		usuario.getTelefones().forEach(telefone -> telefone.setId(null));
		usuario.getDocumentos().forEach(documento -> documento.setId(null));
		usuario.getEmails().forEach(email -> email.setId(null));
		Date agora = new Date();
		for (Credencial credencial : usuario.getCredenciais()) {
			credencial.setId(null);
			codificador.codificar(credencial);
			if (credencial.getCriacao() == null) {
				credencial.setCriacao(agora);
			}
			if (credencial.getInativo() == null) {
				credencial.setInativo(false);
			}
		}
		Usuario salvo = repositorio.saveAndFlush(usuario);
		EntityModel<Usuario> modelo = montarModelo(salvo);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PreAuthorize("@acesso.podeAtualizarUsuario(#atualizacao)")
	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Usuario>> atualizarUsuario(@RequestBody Usuario atualizacao) {
		if (atualizacao.getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o id do usuário a atualizar");
		}
		Usuario usuario = repositorio.findById(atualizacao.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
		atualizador.atualizar(usuario, atualizacao);
		Usuario salvo = repositorio.saveAndFlush(usuario);
		return ResponseEntity.ok(montarModelo(salvo));
	}

	/**
	 * Exclui o usuário com seus telefones, endereço, documentos, e-mails e
	 * credenciais. Usuários com vendas ou veículos não podem ser excluídos.
	 */
	@PreAuthorize("@acesso.podeGerenciarUsuario(#id)")
	@DeleteMapping("/excluir/{id}")
	public ResponseEntity<Void> excluirUsuario(@PathVariable long id) {
		Usuario usuario = repositorio.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
		if (vendaRepositorio.existsByClienteId(id) || vendaRepositorio.existsByFuncionarioId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Usuário participa de vendas e não pode ser excluído");
		}
		if (veiculoRepositorio.existsByProprietarioId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Usuário possui veículos cadastrados e não pode ser excluído");
		}
		desassociador.desassociarUsuario(id);
		repositorio.delete(usuario);
		return ResponseEntity.noContent().build();
	}
}
