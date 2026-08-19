# Correção e conclusão do plugin Battle Royale

## Objetivo
Concluir os sistemas que estavam apenas declarados, corrigir os bugs reportados e gerar um novo `.jar` validado para Paper 1.21.x.

## Implementação

1. **Fluxo de entrada e ônibus**
   - Separar a fase de entrada da fase de embarque: o tempo configurado de entrada termina e só então começam 5 segundos adicionais com inscrições fechadas.
   - Corrigir posição/assentos para todos nascerem dentro do ônibus, manter câmera livre e aprimorar o modelo visual.
   - Bloquear remoção, movimentação, drop e retenção da elytra temporária; removê-la ao pousar, morrer ou sair.

2. **Barreira e safe zones**
   - Fazer a primeira barreira usar por padrão todo o raio jogável do mapa, centrado em `0,0`, sem cortar o mapa no início.
   - Permitir sobrescrever centro/raio inicial em `maps.yml` e pelos comandos de setup.
   - Unificar o cálculo visual e o cálculo de dano, incluindo tolerância interna, mundo correto e fechamento aleatório em terreno válido.
   - Manter dano fixo configurável de 1 coração por ciclo.

3. **Loot, raridades e encantamentos**
   - Aplicar efetivamente os bônus automáticos de cada raridade a armas, armaduras, arcos e bestas, inclusive itens míticos.
   - Evitar bônus sem utilidade em comida, flechas e materiais; manter diferenças por tipo/quantidade/efeito.
   - Adicionar baldes de água às tabelas adequadas e revisar materiais/encantamentos inválidos.

4. **Itens especiais funcionais por clique direito**
   - Corrigir o Grappler com ray trace configurável de alcance maior e impulso proporcional/consistente.
   - Implementar e conectar medkit, bandagem, escudos, chug jug, launch pad arremessável 3x3, fumaça/darkness, impulso, shockwave, boogie bomb, rift, adrenalina e port-a-fort.
   - Marcar projéteis/blocos temporários para limpeza e restauração, com consumo, cooldown/canalização e efeitos defensivos.

5. **Mapa, equipes, morte e restauração**
   - Criar `/br criar <nome com espaços>` usando o mundo em que o jogador está, adicionando-o ao `maps.yml` e à rotação com padrões automáticos; não executar nem substituir `/mv import`.
   - Completar equipes SOLO/DUO/TRIO/SQUAD, símbolos/cores e bloqueio de friendly fire.
   - Criar baú de morte com os itens da partida, manter o eliminado como espectador e retornar ao SMP apenas pelo botão/comando.
   - Garantir limpeza de drops, projéteis, entidades temporárias, blocos colocados/destruídos e restauração dos baús.

6. **Validação e entrega**
   - Compilar com Java 21/Maven contra Paper 1.21.1, corrigir todos os erros encontrados e inspecionar o artefato final.
   - Copiar o novo `BattleRoyale-1.0.0.jar` para a área de download e documentar configurações/comandos alterados.

## Detalhes técnicos
- Eventos Paper serão usados para proteger slots da armadura, interceptar clique direito, projéteis, dano entre equipes, morte e alterações do mapa.
- Configurações existentes continuarão compatíveis; novas chaves terão padrões seguros quando ausentes.
- O comando de criação registra o mundo já carregado pelo servidor/Multiverse; ele não importa pastas nem chama comandos do Multiverse.
