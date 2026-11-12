# AutoBots — automanager

Micro-serviço para gestão de **redes de manutenção veicular e lojas de autopeças**. Cadastra empresas, usuários (clientes, funcionários e fornecedores), veículos, mercadorias, serviços e vendas, expondo uma API REST hipermídia (HATEOAS) pronta para integração.

## Sobre o projeto

O **AutoBots** resolve o dia a dia de uma unidade que presta serviços de manutenção e vende mercadorias: quem é o cliente, qual é o veículo dele, o que foi vendido, qual funcionário atendeu e de qual fornecedor veio cada peça. A API segue o **modelo de maturidade de Richardson, nível 3**: cada resposta traz links (`_links`) para os recursos relacionados, e o cliente navega pela API sem precisar conhecer as rotas de antemão.

### Funcionalidades

- **Empresas** — unidade comercial com telefones, endereço, usuários, mercadorias, serviços e vendas associados
- **Usuários** — perfis `ADMINISTRADOR`, `GERENTE`, `VENDEDOR`, `CLIENTE` e `FORNECEDOR`, com documentos, e-mails, telefones, endereço e credenciais de acesso
- **Credenciais** — por usuário e senha (`USUARIO_SENHA`) ou por código de barras (`CODIGO_BARRA`); a senha nunca é devolvida nas respostas
- **Veículos** — tipo, modelo, placa e proprietário
- **Mercadorias e serviços** — catálogo com valores, estoque e fornecedor
- **Vendas** — cliente, funcionário, veículo atendido, mercadorias e serviços, com validação de perfis e de propriedade do veículo
- **Associações** — usuários, mercadorias, serviços e vendas são vinculados a uma empresa por endpoints próprios

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 27 |
| Framework | Spring Boot 4.1 |
| API hipermídia | Spring HATEOAS (`application/hal+json`) |
| Persistência | Spring Data JPA / Hibernate |
| Banco de dados | H2 (em memória) |
| Build | Maven |

## Arquitetura

```
com.autobots.automanager
├── entidades/      → Entidades JPA (Empresa, Usuario, Veiculo, Venda, Mercadoria, Servico, Credencial…)
├── enumeracoes/    → PerfilUsuario, TipoDocumento, TipoVeiculo
├── repositorios/   → Interfaces Spring Data JPA
├── modelo/         → Regras de negócio (Atualizadores campo a campo, Desassociador)
└── controles/      → Controllers REST com HATEOAS e tratamento global de erros
```

Modelo de dados (resumo):

```
Empresa ──< usuarios (Usuario) ──< documentos, emails, telefones, credenciais
   │                 │  └─ endereco
   │                 ├──< veiculos (Veiculo)
   │                 └──< vendas (como cliente)
   ├──< mercadorias, servicos
   └──< vendas (Venda) ── cliente, funcionario, veiculo, mercadorias, servicos
```

## Como rodar o projeto

### Pré-requisitos

- JDK 27
- Nenhuma instalação adicional: o Maven Wrapper (`mvnw`) acompanha o projeto

### Execução

```bash
cd automanager
./mvnw spring-boot:run
```

No Windows (PowerShell), se o `JAVA_HOME` não estiver configurado:

```powershell
cd automanager
$env:JAVA_HOME = "C:\Program Files\Java\jdk-27.0.1.0.3"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080` já com dados de exemplo (uma empresa, cinco usuários, um veículo, estoque e duas vendas). O console do H2 vem desligado (`spring.h2.console.enabled=false`) porque não passa pela autenticação JWT; ligue-o só em desenvolvimento.

### Testes

```bash
./mvnw test
```

## API

Todos os recursos seguem o mesmo padrão de rotas. Exemplo com `usuario`:

| Verbo | Rota | Descrição |
|---|---|---|
| `GET` | `/usuario/lista` | Lista todos |
| `GET` | `/usuario/{id}` | Consulta um |
| `POST` | `/usuario/cadastro` | Cadastra (`201` + cabeçalho `Location`) |
| `PUT` | `/usuario/atualizar` | Atualiza só o que for enviado (o `id` vai no corpo) |
| `DELETE` | `/usuario/excluir/{id}` | Exclui (`204`) |

### Recursos principais

| Recurso | Rota base |
|---|---|
| Empresas | `/empresa` |
| Usuários | `/usuario` |
| Veículos | `/veiculo` |
| Mercadorias | `/mercadoria` |
| Serviços | `/servico` |
| Vendas | `/venda` |

### Sub-recursos

Pertencem a um dono e usam o mesmo padrão (`/lista`, `/{id}`, `/cadastro`, `/atualizar`, `/excluir/{id}`):

| Rota | Conteúdo |
|---|---|
| `/usuario/{usuarioId}/documento` | Documentos (CPF, CNPJ, RG, CNH, PASSAPORTE) |
| `/usuario/{usuarioId}/email` | E-mails |
| `/usuario/{usuarioId}/telefone` | Telefones |
| `/usuario/{usuarioId}/credencial` | Credenciais de acesso |
| `/empresa/{empresaId}/telefone` | Telefones da empresa |

O endereço é único por usuário e por empresa: `GET /usuario/{usuarioId}/endereco`, `POST …/endereco/cadastro`, `PUT …/endereco/atualizar`, `DELETE …/endereco/excluir` (idem para `/empresa/{empresaId}/endereco`).

### Associações

Vinculam registros já cadastrados. `PUT` associa (idempotente), `DELETE` desassocia e `GET …/lista` consulta; um registro só pode pertencer a uma empresa (`409` caso contrário).

| Rota | Efeito |
|---|---|
| `/empresa/{empresaId}/usuario/{id}` | Usuário passa a fazer parte da empresa |
| `/empresa/{empresaId}/mercadoria/{id}` | Mercadoria no estoque da empresa |
| `/empresa/{empresaId}/servico/{id}` | Serviço oferecido pela empresa |
| `/empresa/{empresaId}/venda/{id}` | Venda registrada pela empresa |
| `/usuario/{usuarioId}/mercadoria/{id}` | Mercadoria fornecida por um fornecedor |

### Exemplos

Cadastrar um usuário com credencial:

```json
POST /usuario/cadastro
{
  "nome": "Maria Souza",
  "perfis": ["CLIENTE"],
  "emails": [{ "endereco": "maria@exemplo.com" }],
  "credenciais": [{ "tipo": "USUARIO_SENHA", "nomeUsuario": "maria", "senha": "segredo" }]
}
```

Registrar uma venda (cliente, funcionário, veículo, mercadorias e serviços vão apenas pelo `id`):

```json
POST /venda/cadastro
{
  "identificacao": "V-0001",
  "cliente": { "id": 3 },
  "funcionario": { "id": 1 },
  "veiculo": { "id": 1 },
  "mercadorias": [{ "id": 1 }],
  "servicos": [{ "id": 1 }, { "id": 2 }]
}
```

Resposta (resumida), já com os links para navegar:

```json
{
  "id": 3,
  "identificacao": "V-0001",
  "_links": {
    "self":        { "href": "http://localhost:8080/venda/3" },
    "vendas":      { "href": "http://localhost:8080/venda/lista" },
    "cliente":     { "href": "http://localhost:8080/usuario/3" },
    "funcionario": { "href": "http://localhost:8080/usuario/1" },
    "veiculo":     { "href": "http://localhost:8080/veiculo/1" }
  }
}
```

### Regras e códigos de resposta

| Código | Quando |
|---|---|
| `200` / `201` / `204` | Sucesso (consulta e atualização / cadastro / exclusão) |
| `400` | Dados inválidos: perfil incompatível, veículo de outro cliente, venda sem itens, campo obrigatório ausente |
| `404` | Registro inexistente |
| `409` | Valor repetido em campo único (documento, identificação de venda, nome de usuário) ou exclusão de registro que está em uso |

Regras de venda: o cliente precisa ter o perfil `CLIENTE`, o funcionário um dos perfis `ADMINISTRADOR`, `GERENTE` ou `VENDEDOR`, o veículo deve pertencer ao cliente, e uma venda com serviços exige o veículo atendido.

## Segurança (JWT)

Toda a API exige token JWT, exceto `POST /login`. Sem token, ou com token inválido/expirado, a resposta é `401`; com token mas sem permissão, `403`.

```http
POST /login
{ "nomeUsuario": "admin", "senha": "admin123" }
```

O token volta no cabeçalho `Authorization: Bearer ...` e também no corpo (`token`). Envie-o do mesmo modo nas demais requisições. Senhas são guardadas como hash BCrypt e nunca são devolvidas. Configuração em `application.properties`: `jwt.secret` (mínimo 32 caracteres; use a variável de ambiente `JWT_SECRET`) e `jwt.expiration` (ms).

| Perfil | Pode |
|---|---|
| `ADMINISTRADOR` | Tudo, inclusive empresas e criar/remover administradores |
| `GERENTE` | CRUD de usuários `GERENTE`, `VENDEDOR` e `CLIENTE`; CRUD de serviços, mercadorias e vendas |
| `VENDEDOR` | CRUD de usuários `CLIENTE`; ler serviços e mercadorias; criar vendas em seu nome e ler as suas |
| `CLIENTE` | Ler o próprio cadastro, as vendas em que comprou e os seus veículos |
| `FORNECEDOR` | Autentica, mas não tem nenhuma autorização |

Usuários de exemplo (senha entre parênteses): `admin` (`admin123`), `gerente` (`gerente123`), `dompedrovendedor` (`vendedor123`), `dompedrocliente` (`cliente123`), `dompedrofornecedor` (`fornecedor123`).

## Integração contínua

O workflow em `.github/workflows/test.yml` compila e executa os testes (`./mvnw verify`) a cada *push* e *pull request* na `main`, com JDK 27.
