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

import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.modelo.EmpresaAtualizador;
import com.autobots.automanager.modelo.StringVerificadorNulo;
import com.autobots.automanager.repositorios.EmpresaRepositorio;

@RestController
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Transactional
@RequestMapping("/empresa")
public class EmpresaControle {
	private final EmpresaRepositorio repositorio;
	private final EmpresaAtualizador atualizador = new EmpresaAtualizador();
	private final StringVerificadorNulo verificador = new StringVerificadorNulo();

	public EmpresaControle(EmpresaRepositorio repositorio) {
		this.repositorio = repositorio;
	}

	private EntityModel<Empresa> montarModelo(Empresa empresa) {
		long id = empresa.getId();
		Link self = linkTo(methodOn(EmpresaControle.class).obterEmpresa(id)).withSelfRel();
		Link lista = linkTo(methodOn(EmpresaControle.class).obterEmpresas()).withRel("empresas");
		Link telefones = linkTo(methodOn(EmpresaTelefoneControle.class).obterTelefones(id)).withRel("telefones");
		Link endereco = linkTo(methodOn(EmpresaEnderecoControle.class).obterEndereco(id)).withRel("endereco");
		Link usuarios = linkTo(methodOn(EmpresaUsuarioControle.class).obterUsuarios(id)).withRel("usuarios");
		Link mercadorias = linkTo(methodOn(EmpresaMercadoriaControle.class).obterMercadorias(id))
				.withRel("mercadorias");
		Link servicos = linkTo(methodOn(EmpresaServicoControle.class).obterServicos(id)).withRel("servicos");
		Link vendas = linkTo(methodOn(EmpresaVendaControle.class).obterVendas(id)).withRel("vendas");
		return EntityModel.of(empresa, self, lista, telefones, endereco, usuarios, mercadorias, servicos, vendas);
	}

	@GetMapping("/{id}")
	public ResponseEntity<EntityModel<Empresa>> obterEmpresa(@PathVariable long id) {
		Empresa empresa = repositorio.findById(id).orElse(null);
		if (empresa == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(empresa));
	}

	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Empresa>>> obterEmpresas() {
		List<EntityModel<Empresa>> modelos = repositorio.findAll().stream().map(this::montarModelo).toList();
		Link self = linkTo(methodOn(EmpresaControle.class).obterEmpresas()).withSelfRel();
		return ResponseEntity.ok(CollectionModel.of(modelos, self));
	}

	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Empresa>> cadastrarEmpresa(@RequestBody Empresa empresa) {
		if (verificador.verificar(empresa.getRazaoSocial())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a razão social da empresa");
		}
		empresa.setId(null);
		if (empresa.getCadastro() == null) {
			empresa.setCadastro(new Date());
		}
		if (empresa.getEndereco() != null) {
			empresa.getEndereco().setId(null);
		}
		empresa.getTelefones().forEach(telefone -> telefone.setId(null));
		Empresa salva = repositorio.saveAndFlush(empresa);
		EntityModel<Empresa> modelo = montarModelo(salva);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Empresa>> atualizarEmpresa(@RequestBody Empresa atualizacao) {
		if (atualizacao.getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o id da empresa a atualizar");
		}
		Empresa empresa = repositorio.findById(atualizacao.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
		atualizador.atualizar(empresa, atualizacao);
		Empresa salva = repositorio.saveAndFlush(empresa);
		return ResponseEntity.ok(montarModelo(salva));
	}

	/**
	 * Exclui a empresa, seus telefones e seu endereço. Usuários, mercadorias,
	 * serviços e vendas apenas deixam de estar associados a ela.
	 */
	@DeleteMapping("/excluir/{id}")
	public ResponseEntity<Void> excluirEmpresa(@PathVariable long id) {
		Empresa empresa = repositorio.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
		repositorio.delete(empresa);
		return ResponseEntity.noContent().build();
	}
}
