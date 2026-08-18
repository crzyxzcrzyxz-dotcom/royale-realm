# Reconstrução completa do plugin BattleRoyale

## Objetivo

Substituir a implementação atual por um plugin Paper 1.21.1/1.21.2 com arquitetura modular, comportamento determinístico e testes automatizados. O código antigo não será tratado como fonte de verdade; apenas os arquivos de configuração e requisitos funcionais serão usados como referência.

## Escopo funcional

- Ciclo completo: anúncio único, entrada/saída por botões separados, espera no local original, embarque nos 5 segundos finais, ônibus parado durante o embarque, voo, pouso, combate, vitória, espectador e retorno ao SMP.
- Partidas manuais e automáticas, limite padrão de 100 jogadores e modos Solo, Duo, Trio e Squad.
- Equipes balanceadas, identificação visual por cor/símbolo, bloqueio real de friendly fire e vitória por última equipe viva.
- Ônibus reconstruído com interior utilizável, assentos internos, câmera livre, modelo configurável, rota e velocidade configuráveis.
- Safe Zone circular com uma única fonte de verdade, interpolação a cada tick, centros aleatórios contidos na zona anterior, validação de terreno, fechamento opcional até zero e dano fixo padrão de 2 HP.
- Loot em baús existentes, probabilidades e raridades configuráveis, itens de raridade superior com vantagens reais e encantamentos válidos garantidos para armas, armaduras, arcos e bestas.
- Itens especiais reconstruídos com identificação segura e uso consistente no ar ou em bloco: grappler, medkit, bandagem, escudos, jump pad 3x3 lançável, smoke/darkness bomb e demais itens declarados no arquivo de loot. Cada item terá validação, cooldown, feedback e consumo atômico.
- Construção, destruição, explosões, fogo, líquidos, containers, drops e entidades temporárias rastreados durante a partida.
- Eliminação com baú de itens, espectador automático e botão explícito para voltar ao SMP.
- Vitória com efeitos/fogos configuráveis, estatísticas, MVP e top 3.
- Snapshots duráveis de jogadores, recuperação após crash e retorno à localização original ou spawn configurado.
- Rotação de mapas e `/br create <id>` usando o mundo atual, com centro padrão 0,0 e compatibilidade com mundos já carregados pelo Multiverse-Core.
- Comandos reorganizados, ajuda contextual, permissões e tab completion.

## Arquitetura proposta

1. **Domínio puro e testável**
   - Estados e transições da partida, equipes, placar, fases da zona, sorteio de loot e cooldowns sem dependência direta de Bukkit.
   - Regras inválidas falham explicitamente em vez de serem ignoradas.

2. **Serviços Paper isolados**
   - `MatchService`, `PlayerSessionService`, `BattleBusService`, `StormService`, `LootService`, `SpecialItemService`, `WorldResetService` e `TeamService`.
   - Listeners finos, filtrando mão principal, estado da partida, mundo e participante antes de delegar.

3. **Integridade do mapa**
   - O mapa ativo terá baseline persistente por região/chunk antes da partida.
   - Alterações serão registradas antes de ocorrerem; restauração respeitará física, tile entities e ordem de dependência.
   - Entidades criadas pela partida serão marcadas por PDC e somente elas serão removidas, evitando apagar entidades legítimas.
   - Containers serão restaurados ao baseline e só depois receberão novo loot na próxima partida.

4. **Configuração validada**
   - Arquivos YAML com defaults completos, migração de chaves antigas e validação na inicialização/reload.
   - Configuração inválida bloqueará apenas o recurso afetado e registrará uma mensagem acionável.

## Estratégia específica para os bugs atuais

- Remover a disputa entre `WorldBorder`, borda fixa e círculo lógico. O `StormService` calculará centro/raio uma vez por tick e esse mesmo snapshot alimentará visual, bossbar e dano.
- Não depender do dano nativo da WorldBorder; ela será apenas visual. O teste geométrico usará distância ao quadrado e tolerância explícita.
- Processar interações especiais somente na mão principal e retornar um resultado tipado (`SUCCESS`, `COOLDOWN`, `NO_TARGET`, `INVALID_STATE`) antes de cancelar/consumir o item.
- Grappler traçará a partir dos olhos até o alcance configurado e aplicará impulso em direção ao ponto atingido, com usos e cooldown persistidos no próprio item.
- Medkit/bandagem serão canalizados e cancelados por movimento/dano; cura instantânea e regeneração respeitarão vida máxima.
- Jump pad registrará os blocos originais antes de criar a plataforma e só substituirá blocos permitidos.
- Loot terá regras por categoria: itens utilitários idênticos não serão duplicados artificialmente em raridades diferentes; equipamento de alta raridade sempre receberá atributos/encantamentos válidos.
- Ônibus usará uma entidade raiz e assentos internos relativos a ela, evitando teletransportar centenas de peças e passageiros de forma independente.

## Testes e verificação

- Adicionar JUnit 5, Mockito e MockBukkit compatível com 1.21 quando aplicável.
- Testes unitários para geometria da zona, transições, formação/vitória de equipes, probabilidades de loot, encantamentos, cooldowns e snapshots serializados.
- Testes de listeners para mão principal, friendly fire, interação no ar/bloco, morte, desconexão e comandos.
- Testes de integração simulando: partida completa, cancelamento por jogadores insuficientes, encerramento forçado, crash/recovery e restauração do mapa.
- Compilar com Java 21 e Paper API 1.21.1 para manter compatibilidade com 1.21.1/1.21.2.
- Gerar o JAR somente após todos os testes passarem e inspecionar seu conteúdo e metadados.

## Critérios de aceite

- Jogador dentro da zona, inclusive junto à borda interna, nunca recebe dano da tempestade.
- Cada item especial funciona tanto em clique no ar quanto em bloco quando sua mecânica permitir, sem disparo duplo ou consumo silencioso.
- Baús recebem loot em toda partida e equipamentos altos cumprem as vantagens configuradas.
- Após a partida, blocos, containers, líquidos, drops e entidades temporárias retornam ao baseline verificável.
- Os 5 segundos finais são exclusivamente de embarque parado; a partida começa com todos dentro do ônibus.
- Solo termina com um jogador vivo; modos de equipe terminam com uma equipe viva.
- Inventário e localização do SMP sobrevivem a saída normal, morte, desconexão, reload e reinício.
- O artefato final é compilado e testado, acompanhado de configuração padrão e referência de comandos.
