# Mãos à Obra — gestão de voluntários e turnos

Aplicação **web fullstack**, para o caso A do desafio **Shift to Digital**. Desenvolvida com **Java 21, Spring Boot, Spring Security, HTML/CSS/JavaScript e PostgreSQL 16**. O JavaFX é para aplicações desktop: aqui o frontend corre no navegador do telemóvel e do computador, como exigido.

## Arranque recomendado — Docker (Windows, macOS ou Linux)

1. Instalar **Docker Desktop** (Windows/macOS) ou Docker Engine + Compose (Linux) e **Git**.
2. Descarregar/clonar o repositório: `git clone URL_DO_REPOSITORIO` e `cd maos-a-obra`.
3. Duplicar `.env.example` para `.env`, alterando `POSTGRES_PASSWORD` para uma password local, por exemplo no PowerShell: `Copy-Item .env.example .env` (macOS/Linux: `cp .env.example .env`).
4. Na pasta que contém `compose.yaml`, executar `docker compose up --build -d`.
5. Abrir **http://localhost:8080**. Na primeira execução, a imagem precisa ser descarregada e o backend compilado. Consultar logs: `docker compose logs -f app`.
6. Parar: `docker compose down` (mantém os dados). Para repor completamente a demonstração: `docker compose down -v` e `docker compose up --build -d` (**apaga** dados introduzidos).

**Acesso pelo telemóvel:** computador e telemóvel na mesma rede, abrir `http://IP_LOCAL_DO_COMPUTADOR:8080`; permitir a porta 8080 na firewall, se necessário. Não expor a aplicação pública à Internet sem HTTPS e configuração de produção.

## Alternativa sem Docker

Requer **Java 21, Maven 3.9+, PostgreSQL 16**. Criar utilizador `maos` e base `maos`, configurar variáveis de ambiente `DB_URL`, `DB_USER`, `DB_PASSWORD` e executar `mvn spring-boot:run`. As tabelas e os dados iniciais são criados automaticamente pelos scripts `schema.sql` e `data.sql`.

## Contas de demonstração (apenas dados fictícios!)

| Perfil | Utilizador | Password |
|---|---|---|
| Coordenadora | `rosa` | `Demo2026!` |
| Voluntário | `tiago` | `Demo2026!` |
| Voluntário | `marta` | `Demo2026!` |
| Voluntário | `ines` | `Demo2026!` |
| Voluntário | `rui` | `Demo2026!` |
| Voluntário | `fernanda` | `Demo2026!` |
| Voluntário | `beatriz` (menor) | `Demo2026!` |

Todos os **12 voluntários do Excel** têm conta com o username indicado em `data.sql` e password de demonstração idêntica. As passwords ficam armazenadas **apenas como hash BCrypt**. Antes de um uso real, substituir as contas e passwords de demonstração; não são apropriadas para produção.

## Requisitos implementados

1. **Registo e login** com perfil (nome, contacto, disponibilidade, competências) e edição pelo próprio.
2. **Criação de turnos** pelo coordenador: ação, data, hora, local, vagas e competência.
3. **Inscrição e cancelamento** pelo voluntário, com bloqueio de vagas excedidas e sobreposição horária; concorrência protegida por `SELECT FOR UPDATE` e transação.
4. **Presenças/faltas e horas automáticas**: só o estado `PRESENT` soma horas de duração do turno. Soma-se também o histórico confirmado de outubro.
5. **Painel dos próximos sete dias**, com vagas livres e acesso rápido às presenças.

**Regras adicionais:** cancelamento apenas mais de 24h antes; competência verificada no servidor; menores excluídos de turnos iniciados às 20:00 ou depois; voluntários não têm acesso a contactos alheios, listas de voluntários, painel, criação ou registo de presenças. A distribuição noturna do Excel assinala *«Primeiros socorros (1 pessoa)»*: a política `ONE` permite participantes sem essa competência **apenas quando já existe pelo menos uma pessoa inscrita com a competência**; impede que essa pessoa cancele deixando outras inscritas sem cobertura. Nos outros turnos com competência, todos devem possuí-la.

**Segurança:** sessões Spring Security, cookie HttpOnly SameSite=Lax, proteção CSRF, hashes BCrypt e `@PreAuthorize` nos endpoints. Não há APIs externas nem divulgação de contactos na interface de voluntário.

## Dados e decisões de interpretação

- `data.sql` contém os **12 voluntários, 7 ações/turnos e 7 presenças históricas** do Excel original; `schema.sql` define o modelo relacional. Os dados originais não foram alterados.
- O Excel indica dias como «sábado (semana 1)», sem datas absolutas. Para permitir demonstrar eventos futuros em qualquer dia, a semana 1 começa **no próximo sábado estritamente futuro à data do primeiro arranque**. As restantes datas seguem o padrão de semanas 2 e 3 indicado na tabela. Uma vez criadas, **as datas são persistidas**, não mudam ao reiniciar.
- A amostra «presenças de outubro» não indica o ano; para armazenamento escolheu-se **outubro de 2026**, documentando a suposição. Os valores e a presença/falta mantêm-se fiéis ao ficheiro. Não se infere que o dia da semana corresponda ao ano escolhido.
- O dossier pede, na demonstração, confirmar presenças num turno criado para o próximo sábado (ainda futuro). Por isso, a marcação de presença **é permitida antes do início**, exclusivamente para compatibilizar o cenário. Em produção, aconselha-se permitir registos apenas após o turno.
- A disponibilidade habitual é informativa, não bloqueia inscrição: o dossier não a define como regra obrigatória.
- A autenticação é por nome de utilizador; o contacto é telefone, como no Excel.
- A lógica noturna restringe turnos que **começam às 20h ou depois**; se forem criados turnos que atravessam as 20h, seria prudente alargar a verificação.

## API REST

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/api/csrf` | pública | token antifalsificação |
| POST | `/api/register` | pública | criar voluntário |
| POST | `/api/login` | pública | iniciar sessão |
| POST | `/api/logout` | autenticado | terminar sessão |
| GET, PUT | `/api/me` | autenticado | consultar/editar perfil próprio |
| GET | `/api/shifts` | autenticado | ações, ocupação e inscrição própria |
| POST | `/api/shifts` | coordenador | criar turno |
| PUT, DELETE | `/api/shifts/{id}` | coordenador | alterar/apagar turno sem inscritos |
| POST, DELETE | `/api/shifts/{id}/enroll` | voluntário | inscrever/cancelar |
| GET | `/api/shifts/{id}/participants` | coordenador | inscritos (sem divulgar telefones) |
| PUT | `/api/shifts/{id}/presence` | coordenador | marcar presente/falta/pendente |
| GET | `/api/hours` | autenticado | horas próprias |
| GET | `/api/dashboard` | coordenador | próximos sete dias |
| GET | `/api/volunteers` | coordenador | diretório com contactos |
| GET | `/api/volunteers/{id}/hours` | coordenador | horas de um voluntário |

Por defeito: sucesso `200`, criação `201`, pedido inválido ou regra de negócio `409`, `401` se não autenticado, `403` sem permissão. O servidor guarda a sessão numa cookie, não é necessário guardar tokens no browser.

## Demonstração e avaliação

Consultar **`docs/GUIA_APRESENTACAO.md`**, que explica a arquitetura, as funcionalidades e o roteiro dos **sete testes** com passos precisos. Para ensaio, reiniciar a base com `docker compose down -v` e executar novamente o Compose.

## Estrutura

```text
src/main/java/pt/maosaobra/
  Application.java      # inicialização Spring
  SecurityConfig.java   # login, sessões, papéis e CSRF
  SeedPasswords.java    # BCrypt das contas fictícias
  Api.java              # controladores REST, transações, regras de negócio
  ApiErrors.java        # erros HTTP previsíveis
src/main/resources/
  schema.sql            # tabelas, chaves e índices
  data.sql              # dados completos do Excel
  application.properties
  static/index.html     # interface
  static/style.css      # design responsivo
  static/app.js         # navegação e chamadas fetch à API
Dockerfile              # compilação e imagem Java
compose.yaml            # app + PostgreSQL
.env.example            # exemplo de configuração, sem segredos reais
```

## GitHub público

1. Criar um repositório **público vazio** na conta do GitHub, por exemplo `maos-a-obra`.
2. Nesta pasta executar: `git init`, `git add .`, `git commit -m "Aplicação web de voluntariado"`, `git branch -M main`, `git remote add origin https://github.com/UTILIZADOR/maos-a-obra.git`, `git push -u origin main`.
3. Verificar com `git status` e na página pública que **não** estão publicados `.env`, passwords reais, `target/` ou `node_modules`.
4. O júri clona o projeto, cria o `.env` e executa `docker compose up --build -d`.

## Limitações/estado de verificação

A aplicação foi montada a partir dos documentos fornecidos. **Não foi possível executar o Docker/PostgreSQL neste ambiente de preparação**; o funcionamento ponta a ponta deve ser validado numa máquina com Docker antes da apresentação. A lista de espera automática e a declaração PDF são extras desejáveis **não incluídos nesta versão**. As notificações são opcionais e também não estão incluídas. Não apresentar esses extras como funcionalidades concluídas.

### Eliminar turnos (coordenador)

Na página **Todos os turnos**, o coordenador pode selecionar **Eliminar turno** e confirmar a operação. O servidor valida o perfil `COORDINATOR` e impede eliminar turnos com inscrições ativas ou presenças registadas. Nesses casos, o botão aparece desativado; a restrição também é aplicada diretamente na API `DELETE /api/shifts/{id}`. A eliminação é definitiva.
