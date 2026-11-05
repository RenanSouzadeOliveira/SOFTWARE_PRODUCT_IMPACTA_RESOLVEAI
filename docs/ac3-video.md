# AC3 — roteiro de vídeo e narração

Estado: **gravações técnicas locais disponíveis; VÍDEO ACADÊMICO NARRADO/PÚBLICO PENDENTE**. Os seis segmentos WebM foram capturados pelo Playwright durante a validação real. Não há URL pública de vídeo confirmada.

## Sequência sugerida, 4–6 minutos

| Etapa | Tela e ação | Narração em português |
| --- | --- | --- |
| 1, 20s | Revisão Git e arquitetura | “Esta é a entrega AC3 do ResolveAí. Angular acessa a API Spring Boot, que aplica autorização e persiste no PostgreSQL. A revisão funcional validada é facf63d.” |
| 2, 40s | Login do solicitante, listagem 20/20/5 | “Esta conta fictícia vê seus próprios chamados. São 45 registros em três páginas, com ordenação estável e total calculado no servidor.” |
| 3, 60s | Protocolo, texto parcial, filtros AND, sort | “A pesquisa aceita protocolo, título e descrição. Combinamos status, prioridade, categoria, responsável e datas UTC. Os filtros respeitam a visibilidade da conta e a ordenação mantém o desempate por ID.” |
| 4, 40s | Página 2, reload, voltar/avançar, limpar e vazio | “Os parâmetros ficam na URL. Recarregar e navegar no histórico do navegador restaura a consulta. Limpar filtros retorna à listagem original. Uma busca sem resultado apresenta estado vazio e bloqueia a paginação.” |
| 5, 60s | Atendente assume; muda prioridade e resolve; timeline | “Cada alteração autorizada gera seu evento na mesma transação. A timeline mostra ação, autor, data e valores anteriores e novos. Após atualizar e recarregar, o histórico permanece sem duplicação.” |
| 6, 40s | Outro solicitante, atendente, ADMIN, autor desativado | “Outro solicitante não consegue consultar esse chamado. O atendente vê sua carteira; o administrador consulta globalmente. Desativar uma conta impede seu acesso, mas preserva sua identificação nos eventos anteriores.” |
| 7, 30s | Tela 360px, Tab e retry | “A interface foi exercitada a 360 pixels e por teclado. O histórico apresenta carregamento, erro e nova tentativa. Esta verificação é funcional e não substitui uma auditoria completa de acessibilidade.” |
| 8, 30s | Testes, Flyway e links efetivamente publicados | “A revisão passou em 93 testes backend padrão, 93 no PostgreSQL e 105 Angular. Foram verificadas migrations V1 a V9, upgrade com legado e proteções de imutabilidade. Os links apresentados devem corresponder às evidências já publicadas.” |

## Preparação e gravação manual

1. Execute a infraestrutura isolada e os builds descritos em [ac3-release.md](ac3-release.md). Use banco novo para a gravação e mantenha `.env`, senhas, JWT e cabeçalhos fora da captura.
2. Para obter os 45 chamados com login conhecido, exporte `AC3_API_URL`, um `AC3_DEMO_EMAIL` novo em `example.invalid` e `AC3_DEMO_PASSWORD` gerada localmente; execute `node scripts/ac3-filtros-demonstracao.mjs`. Guarde a senha apenas localmente. Cada execução exige email novo.
3. Para demonstrar alteração manual, execute `scripts/ac3-historico-demonstracao.mjs` **sem** `--verify`, com `HISTORY_API_URL`, `HISTORY_DEMO_DATABASE`, `HISTORY_DEMO_PASSWORD`, `PGPORT` e `PGUSER` definidos. Use os emails/caminhos impressos e a senha escolhida. O modo `--verify` já altera e desativa o atendente, portanto use outro conjunto para a gravação manual.
4. Abra o OBS disponível no ambiente, selecione captura da janela do navegador, resolução 1280×900 ou 1920×1080, e microfone. Faça teste de 10 segundos, confirme áudio legível e grave a sequência acima. Mostre a timeline inteira por rolagem, incluindo atribuição, prioridade e status.
5. Para datas distintas e os três perfis, o harness `scripts/ac3-release-browser.mjs` fornece os segmentos técnicos e fixtures reproduzíveis. Ele promove apenas contas novas no banco isolado, cria um registro de dois dias antes com evento atômico e desativa somente seu autor fictício. Para uma nova tomada narrada, execute o fluxo manual em contas novas; não reutilize credenciais aleatórias não impressas do harness.
6. Revise o arquivo: login sem senha visível, nenhuma ferramenta com token aberto, dados fictícios e texto legível. Adicione narração aos segmentos ou grave uma tomada contínua. Os segmentos automáticos são silenciosos e rápidos; não equivalem ao vídeo acadêmico final editado.
7. Publicação depende de autorização final. Após publicar, abra o link sem autenticação, confirme reprodução/áudio e registre URL e revisão nas notas de entrega e no cartão autorizado.

Os segmentos locais estão separados em `video-lista` (solicitante), `video-perfis` (atendente/ADMIN) e `video-historico` (responsável, proprietário e acesso negado). Quatro PNG cobrem lista e histórico em desktop/360px. O pacote local inclui manifesto SHA-256; ele não contém banco ou credenciais.
