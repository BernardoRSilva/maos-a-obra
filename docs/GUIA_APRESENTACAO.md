# GUIA DE APRESENTAÇÃO — SHIFT TO DIGITAL / CASO A

**Associação Mãos à Obra · Gestão de voluntários e turnos**  
**Objetivo:** apresentar em cerca de 10 minutos, demonstrar 7 testes e explicar a arquitetura.  
**Fonte:** Dossier do Cliente — Caso A, Dados A (Excel e PDF), Critérios de Avaliação e Desafio Shift to Digital.

## 1. O que mostrar ao júri (ordem e duração)

| Tempo | Mostrar | Mensagem oral sugerida |
|---|---|---|
| 0:00–1:00 | Problema | «A associação gere cerca de 140 voluntários num grupo de mensagens, folhas Excel e papel. As vagas não estão atualizadas, há desistências, conflitos de horários e horas somadas manualmente.» |
| 1:00–2:00 | Solução | «Criámos uma aplicação web simples, usável no telemóvel. Cada voluntário vê as vagas e gere inscrições. A coordenação cria ações, regista presenças e vê o resumo semanal.» |
| 2:00–3:00 | Arquitetura | «O frontend é HTML, CSS e JavaScript; comunica por API REST com Java Spring Boot; os dados persistem no PostgreSQL. Permissões e regras são verificadas no servidor.» |
| 3:00–8:30 | 7 testes funcionais | Seguir o roteiro abaixo, mostrar mensagens de sucesso e bloqueio. |
| 8:30–9:30 | Segurança, dados e implementação | «BCrypt para passwords, sessões com proteção CSRF, regras de concorrência transacionais, dados iniciais do Excel, Docker e README.» |
| 9:30–10:00 | Valor criado | «A Rosa passa a saber quem vem e quantas vagas faltam. Os voluntários têm autonomia e as horas ficam automaticamente atualizadas.» |

**Antes de entrar na sala:** abrir http://localhost:8080, confirmar Docker `docker compose ps` e logs `docker compose logs --tail=30 app`. Ter o README à mão. Para uma demonstração completamente limpa, usar `docker compose down -v` e `docker compose up --build -d`. ATENÇÃO: `down -v` apaga dados alterados.

## 2. Os sete testes obrigatórios, ao vivo

**Contas:** coordenadora `rosa`; voluntários `marta`, `tiago`, `ines`, `rui`. Todas com password `Demo2026!`.  
**Regra operacional:** terminar sessão no botão **Sair** entre perfis. Não usar janelas anónimas para contornar a sessão.

### Teste 1 — A coordenadora cria turno de sábado com 3 vagas

1. Entrar com `rosa`.
2. **Criar turno**. O formulário vem preenchido com **o próximo sábado**, «Recolha de alimentos», 10h–13h, «Supermercado da Avenida» e **3 vagas**.
3. Confirmar os valores e clicar em **Criar turno**.
4. Em **Todos os turnos**, localizar o turno criado — identificá-lo pelo **ID mostrado** e pelas 3 vagas. Há também um turno original do Excel, com **4 vagas**, por isso não os confundir.

**Resultado:** turno persistido e visível aos voluntários. A página **Painel** deve incluir o próximo sábado no intervalo de sete dias.

### Teste 2 — Três inscritos e um quarto bloqueado

1. Terminar sessão e entrar com `marta`; inscrever no **turno novo de 3 vagas**.
2. Repetir com `tiago` e `ines` (um de cada vez, fazendo logout).
3. Entrar com `rui` e abrir o mesmo turno.

**Resultado:** contador **3 de 3** e botão **Sem vagas**, desativado. **Demonstração de bloqueio efetivo no backend:** chamar diretamente `POST /api/shifts/{id}/enroll` como Rui (via cliente HTTP autenticado com CSRF) também devolve **409**. O teste visual não substitui a regra de servidor.

### Teste 3 — Marta tenta um segundo turno sobreposto

1. Voltar ao login `rosa`, criar outro turno também para **o próximo sábado, 11h–12h**, 5 vagas, título «Apoio logístico — teste de sobreposição» (outro local à escolha).
2. Entrar como `marta`; tentar inscrever-se neste segundo turno.

**Resultado:** mensagem **«Já tem uma inscrição num turno com horário sobreposto»**. A regra usa a condição de intervalos: `inicio_existente < fim_novo` E `fim_existente > inicio_novo`.

### Teste 4 — Cancelamento com menos de 24 horas

1. Entrar com `rosa` e criar «Turno urgente — teste 24h», com início **daqui a aproximadamente 2 horas**, fim uma hora depois, e 2 vagas. Por exemplo, se forem 15h, usar 17h–18h do mesmo dia. Se já for noite, escolher madrugada ou manhã seguinte **dentro de 24 horas**.
2. Entrar com `rui`; inscrever-se nesse turno (e confirmar inscrição).
3. Clicar **Cancelar inscrição** e confirmar.

**Resultado:** pedido recusado com mensagem **«Cancelamentos só até 24 horas antes. Contacte a coordenação.»**. A inscrição permanece ativa.

### Teste 5 — Voluntário sem carta é bloqueado

1. Entrar com `tiago` (não tem competências no Excel).
2. Procurar o turno original **«Entrega de cabazes ao domicílio»**, que exige **Carta de condução**.
3. Clicar **Inscrever-me**.

**Resultado:** mensagem **«É necessária a competência: Carta de condução»**. É uma validação do backend. Para demonstrar o caminho válido, `marta` possui carta de condução e poderia inscrever-se, se não houver sobreposição.

### Teste 6 — Presença e atualização automática das horas

1. Com `tiago`, abrir **As minhas horas** e observar as **6 horas históricas** (Recolha 3h + Triagem 3h).
2. Entrar como `rosa`, **Todos os turnos**, localizar o turno novo de 3 vagas, clicar **Ver inscritos**.
3. Na linha `Tiago Lopes`, clicar **Presente**. Marcar também presença ou falta para os outros dois, se desejado.
4. Terminar sessão, entrar como `tiago` e abrir **As minhas horas**.

**Resultado:** Tiago passa de **6 horas** para **9 horas**. Cálculo: `3h + 3h` históricas confirmadas + `3h` do turno novo. Marcar «Falta» não soma horas.

**Nota para o júri:** como o dossier pede marcar presenças num turno futuro criado na própria demo, a versão demonstrativa permite marcar presença antecipadamente. É uma decisão documentada; numa aplicação real esta ação deve ficar disponível depois do turno.

### Teste 7 — Privacidade e permissões

1. Estando com `tiago`, verificar que o menu tem apenas **Turnos disponíveis**, **As minhas horas** e **O meu perfil**.
2. Não existe lista de telefones alheios. A API **GET `/api/volunteers`** e **GET `/api/dashboard`** estão protegidas e, com sessão de voluntário, devolvem **403 Forbidden** (pode demonstrar na aba Network/Ferramentas de Programador ou num cliente HTTP com sessão).
3. Mostrar o painel de `rosa` num segundo login para contrastar permissões.

**Resultado:** segregação de permissões aplicada no servidor; não é apenas ocultação de botões.

## 3. Arquitetura: como explicar cada parte

```text
Telemóvel / navegador
        |
        | HTML, CSS, JavaScript (fetch JSON)
        v
Java Spring Boot (API REST na porta 8080)
        |
        | Segurança de sessão / autenticação BCrypt / CSRF
        | Regras de negócio / @PreAuthorize / @Transactional
        v
PostgreSQL (persistência relacional)
        |
        +-- volunteers (id, username, password_hash, role, ...)
        +-- shifts (id, title, starts_at, ends_at, capacity, ...)
        +-- enrollments (shift_id, volunteer_id, status)
        +-- historical_attendance (volunteer_id, hours, present)
```

**Frontend:** `index.html` estrutura os ecrãs; `style.css` define tipografia, botões, grelhas e breakpoints para telemóveis; `app.js` gera a interface, trata as ações e envia pedidos à API. Não fala diretamente com a base de dados.

**Backend:** `Application.java` arranca o servidor Spring; `Api.java` implementa as rotas e validações; `SecurityConfig.java` gere sessões/autorização; `ApiErrors.java` fornece mensagens previsíveis. A coordenação tem perfil `ROLE_COORDINATOR`; o voluntário tem `ROLE_VOLUNTEER`.

**SQL:** `schema.sql` cria tabelas, relações, índices e `CHECK`; `data.sql` popula os dados do cliente. `SeedPasswords.java` converte a password temporária de seed em hash BCrypt no arranque. `enrollments` liga voluntários a turnos: é uma relação muitos-para-muitos, com estado da inscrição.

**Docker:** `Dockerfile` constrói o JAR usando Maven/Java 21; `compose.yaml` inicia PostgreSQL e servidor, aguarda o estado healthy da base de dados e mantém os dados num volume persistente. O júri não precisa de instalar diretamente Java ou PostgreSQL se tiver Docker.

## 4. Perguntas prováveis do júri

**Porque não usar JavaFX?** JavaFX cria programas desktop. O enunciado pede web fullstack responsiva, executada no browser. O Java mantém-se no backend Spring Boot, uma escolha coerente com o que já conhecemos de POO.

**O que é API REST?** Interface HTTP com recursos: `GET /api/shifts` consulta, `POST /api/shifts` cria, `POST /api/shifts/{id}/enroll` inscreve, `DELETE` cancela. O frontend recebe JSON.

**Como impedem mais inscritos do que vagas?** A operação é uma transação. Faz `SELECT ... FOR UPDATE` da linha do turno, bloqueando inscrições concorrentes durante a contagem e inserção; se não houver vagas, devolve 409.

**Como verificam sobreposição?** Os intervalos [início, fim) sobrepõem-se quando `inicio_existente < fim_novo` e `fim_existente > inicio_novo`. Turnos encostados (um termina às 13h e outro começa às 13h) não se sobrepõem.

**Como são calculadas as horas?** SQL soma a duração `ends_at - starts_at` dos turnos com inscrição `PRESENT`, adicionando as presenças históricas confirmadas. Inscrições pendentes e faltas não contam.

**Como é protegida a informação?** `@PreAuthorize` restringe rotas no servidor, login por sessão, password BCrypt, CSRF, cookies HTTP-only; a vista dos turnos não transmite contactos de terceiros.

**Porquê PostgreSQL?** É uma base relacional gratuita e open source, conforme o regulamento. Restrições e relações garantem integridade de dados.

**Que limitações existem?** O modo de demonstração permite presenças antes do turno, não há lista de espera nem declaração PDF nem notificações; não há deploy público com HTTPS. Estas funcionalidades não devem ser afirmadas como concluídas.

## 5. Checklist final de entrega

- [ ] `docker compose up --build -d` termina sem erros e `http://localhost:8080` abre.
- [ ] `rosa`, `tiago`, `marta`, `ines`, `rui` conseguem entrar.
- [ ] Há 12 voluntários, 7 turnos iniciais, 7 presenças de outubro e os respetivos históricos.
- [ ] Executar os sete testes na ordem acima. Registar o ID dos dois turnos criados.
- [ ] Abrir no telemóvel ou redimensionar o browser (mobile) e confirmar botões legíveis.
- [ ] Criar repositório público com `README.md`, `.env.example`, `compose.yaml`, `Dockerfile`, `src/`, `docs/`.
- [ ] Confirmar que **`.env` não está no GitHub**. As passwords fictícias estão no README; nunca publicar credenciais reais.
- [ ] Preparar uma segunda máquina/rede se possível, ou ensaiar clone num diretório limpo.

## 6. Avaliação: o que é exigido e o que é extra

A grelha totaliza **200 pontos**: pitch 40, resposta ao cliente 50, frontend 40, backend 35, segurança 20, boas práticas 15. Há uma penalização de **−20** por publicar `.env`, credenciais reais ou `node_modules`. Docker ou aplicação publicada vale até 5 pontos nas boas práticas. O objetivo mínimo de prémio são **120 pontos**.

O dossier separa **5 requisitos obrigatórios** dos desejáveis e opcionais. Nesta entrega estão implementados os 5 obrigatórios, mas o resultado funcional real precisa de ser confirmado pela equipa com Docker/PostgreSQL antes da apresentação. Extras desejáveis ainda não desenvolvidos: lista de espera e declaração PDF; opcionais: notificações simuladas e gamificação/estatísticas avançadas.
