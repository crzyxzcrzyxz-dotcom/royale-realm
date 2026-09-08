# Royale Realm

Crie um plugin de Battle Royale COMPLETO, FUNCIONAL, PROFISSIONAL e PRONTO PARA USO em um servidor Minecraft. Não entregue apenas exemplos, pseudocódigo, sistemas incompletos ou placeholders. Todos os sistemas descritos abaixo devem ser realmente implementados e integrados entre si.

O Battle Royale será integrado a um SMP, portanto a proteção dos inventários e dos dados dos jogadores é uma prioridade absoluta.

1. CONCEITO GERAL

O mapa do Battle Royale já está pronto. As construções, casas, prédios, terrenos e BAÚS já existem no mapa.

Não crie um mapa novo.

O plugin deve utilizar o mapa existente e adicionar todo o funcionamento do Battle Royale:

Sistema de entrada

Contagem regressiva

Ônibus

Elytra para o salto

Loot aleatório

Raridades

Itens especiais

Construção

Destruição

Safe Zone

Storm/Barreira

Eliminações

Vitória

Espectador

Regeneração do mapa

Regeneração dos baús

Rotação de mapas

Salvamento dos jogadores

Sistema automático e manual de partidas

Mensagens, títulos, sons, partículas e UI

O plugin deve funcionar sem exigir mods no cliente.

2. ENTRADA NO EVENTO

O administrador deve conseguir iniciar uma partida manualmente através de comando.

Também deve existir um sistema automático.

O intervalo entre eventos deve ser configurável.

Antes de cada partida, envie uma mensagem bonita no chat avisando que um Battle Royale irá começar.

A mensagem deve conter:

Nome do evento

Tempo restante

Quantidade atual de jogadores

Quantidade mínima necessária

Botão clicável:

[ ENTRAR NO BATTLE ROYALE ]

O botão deve realmente funcionar através de ClickEvent.

Também pode existir um botão para sair do evento enquanto a fase de entrada estiver aberta.

3. PERÍODO DE ENTRADA

Quando o evento for anunciado, abra um período para os jogadores entrarem.

O tempo deve ser configurável, inicialmente entre 30 e 60 segundos.

Durante esse período:

Jogadores podem entrar no evento.

O inventário original é salvo imediatamente.

Os jogadores permanecem no local configurado para início da partida.

Seus itens do SMP ficam protegidos.

Comandos ficam bloqueados.

Os jogadores não podem modificar o mapa.

Os jogadores não podem interferir no SMP.

Quando o período terminar, todos os jogadores que entraram devem ser colocados diretamente no ônibus.

Não crie um sistema de lobby separado. Os jogadores devem permanecer no local configurado até o início da partida.

Se não houver jogadores suficientes quando o tempo acabar, cancele a partida e restaure todos os jogadores ao estado original.

4. PROTEÇÃO ABSOLUTA DO SMP

Este é um dos sistemas mais importantes.

Antes de um jogador entrar no Battle Royale, salve completamente o estado dele.

Salvar pelo menos:

Inventário

Armadura

Offhand

XP

Nível

Vida

Fome

Saturação

Efeitos

Posição original

Mundo original

Gamemode

Demais dados necessários para restaurar o estado original

Durante o Battle Royale, o jogador utiliza um inventário temporário exclusivo do evento.

Nunca misture os itens do Battle Royale com os itens do SMP.

Quando o jogador:

morrer;

for eliminado;

sair voluntariamente;

for removido;

sair do mapa;

o evento terminar;

o servidor precisar encerrar a partida;

restaure exatamente o estado que ele tinha antes de entrar.

O jogador NÃO pode perder nenhum item do SMP.

Crie um sistema de recuperação caso o servidor reinicie ou o plugin seja recarregado durante uma partida.

Os dados salvos devem permanecer protegidos até que a restauração seja concluída com sucesso.

5. BLOQUEIO DE COMANDOS

Enquanto estiver participando do Battle Royale, bloquear todos os comandos normais.

Permitir apenas comandos administrativos ou comandos explicitamente definidos na configuração.

Se o jogador tentar executar um comando bloqueado, mostrar uma mensagem informando que comandos estão desativados durante o Battle Royale.

6. ÔNIBUS / BATTLE BUS

Crie o sistema de ônibus completamente pelo plugin.

Não depender de um mod externo.

Para cada mapa, permitir configurar:

Coordenada inicial

Coordenada final

Altura

Velocidade

Duração do percurso

Aparência do ônibus

O ônibus deve realmente percorrer a rota entre uma coordenada e outra.

Todos os jogadores devem começar dentro do ônibus.

O ônibus deve se mover gradualmente, e não simplesmente teleportar instantaneamente de uma ponta para outra.

O sistema deve ser otimizado para não causar lag.

Quando o jogador decidir saltar:

Remover o jogador do ônibus.

Dar uma Elytra temporária.

Permitir que ele plane normalmente.

Permitir que escolha livremente onde pousar.

Quando o ônibus chegar ao destino final, ele deve desaparecer.

Jogadores que ainda estiverem no ônibus devem ser automaticamente lançados.

7. ELYTRA

Ao sair do ônibus, cada jogador deve receber uma Elytra temporária para realizar o salto.

A Elytra deve ser adicionada ao inventário/slot de peito do jogador automaticamente.

Não utilizar Slow Falling como sistema principal.

O jogador deve poder planar normalmente utilizando a Elytra, permitindo atravessar grandes distâncias do mapa.

A Elytra utilizada no Battle Royale é temporária e NÃO pertence ao jogador.

Assim que o jogador tocar no chão pela primeira vez após o salto:

Remover automaticamente a Elytra do inventário/slot de peito.

Remover qualquer item temporário relacionado ao sistema de queda.

Garantir que a Elytra nunca seja adicionada aos itens permanentes do jogador.

Desativar o sistema de queda do Battle Royale.

Considerar o jogador oficialmente pousado.

Se a Elytra possuir durabilidade, ela deve ser controlada pelo plugin e não deve causar problemas ou permitir que o jogador mantenha a Elytra após o pouso.

8. POUSO

Quando o jogador tocar no chão pela primeira vez depois de saltar:

A Elytra é removida automaticamente.

Cancelar qualquer estado relacionado ao salto.

Não aplicar dano de queda referente ao salto inicial.

Iniciar oficialmente a fase terrestre.

Entregar o kit inicial.

O jogador não deve receber dano de queda ao realizar o primeiro pouso do Battle Royale.

9. KIT INICIAL

Ao pousar pela primeira vez, cada jogador recebe:

1 picareta

64 blocos de tábuas de madeira

32 pães

Deixe esses itens configuráveis.

A picareta deve ser adequada para começar a partida e não deve ser excessivamente poderosa.

10. LOOT DOS BAÚS

Os baús já estão espalhados pelo mapa.

O plugin deve detectar e utilizar os baús existentes.

No início de cada partida:

Limpar os baús.

Gerar loot aleatório.

Aplicar raridades.

Distribuir os itens.

Garantir variedade.

Registrar o estado original dos baús.

O loot deve mudar a cada partida.

Não colocar os mesmos itens nos mesmos baús todas as vezes.

11. ITENS DO LOOT

Adicionar itens vanilla adequados ao Battle Royale.

Inclua:

ARMAS:

Espadas

Arcos

Outras armas vanilla apropriadas

ARMADURAS:

Capacetes

Peitorais

Calças

Botas

FERRAMENTAS:

Picaretas

Machados

Pás

COMIDA:

Pão

Carnes

Alimentos melhores

Maçãs

Outros alimentos equilibrados

CONSUMÍVEIS:

Poções

Maçãs douradas

Outros consumíveis adequados

UTILIDADE:

Escudos

Teias de aranha

Blocos

Flechas

Materiais úteis

Não colocar itens absurdamente fortes.

12. SISTEMA DE RARIDADE

Criar cinco níveis:

★ COMUM

★★ INCOMUM

★★★ RARO

★★★★ ÉPICO

★★★★★ LENDÁRIO

Cada raridade deve possuir uma cor própria.

O nome dos itens deve mostrar as estrelas.

Exemplo:

★ Espada de Ferro

★★ Espada de Ferro

★★★ Espada de Diamante

★★★★ Espada de Diamante

★★★★★ Espada de Diamante

A raridade influencia:

Chance de aparecer

Qualidade

Quantidade

Encantamentos

Materiais

Eficiência

Os encantamentos devem continuar dentro de limites normais do Minecraft.

Não criar coisas absurdas como Sharpness X, Protection X ou Efficiency X.

Os itens devem parecer especiais sem quebrar o equilíbrio.

13. LOOT POR RARIDADE

Criar probabilidades configuráveis.

Exemplo inicial:

Comum: 50%
Incomum: 27%
Raro: 15%
Épico: 6%
Lendário: 2%

Esses valores devem poder ser alterados no config.

Cada baú deve escolher aleatoriamente:

Quantidade de slots ocupados

Raridades

Itens

Quantidades

Baús em locais mais importantes podem ter uma chance maior de loot raro.

14. ITENS ESPECIAIS

Crie itens especiais inspirados em Battle Royale/Fortnite, mas adaptados ao Minecraft.

Eles devem funcionar sem mods obrigatórios.

Inclua obrigatoriamente um GRAPPLER.

GRAPPLER

Criar um Grappler funcional.

Quando usado:

Dispara um gancho.

Identifica uma superfície válida.

Cria efeito visual.

Puxa o jogador até o local.

Possui cooldown ou número limitado de usos.

Possui som.

Não permite atravessar paredes.

Não permite exploits.

Não pode ser usado para sair da área protegida.

Crie também outros itens especiais úteis e equilibrados, por exemplo:

Itens de mobilidade

Itens de cura

Itens de escape

Itens de impulso

Itens defensivos

Não transformar o plugin em um modpack.

Os itens devem continuar parecendo Minecraft.

15. CONSTRUÇÃO

Durante a partida, os jogadores podem construir.

Permitir:

Colocar blocos

Quebrar blocos

Construir estruturas

Usar blocos encontrados nos baús

O mapa não deve ficar permanentemente alterado.

Registrar todas as alterações feitas durante a partida ou utilizar um sistema eficiente de snapshot/regeneração.

16. REGENERAÇÃO DO MAPA

Ao terminar uma partida, restaurar o mapa ao estado original.

Restaurar:

Blocos quebrados

Blocos colocados

Baús

Containers

Portas

Alçapões

Estados relevantes

Itens dropados

Outras alterações causadas pelos jogadores

O mapa original deve ser preservado.

O sistema precisa funcionar novamente para a próxima partida.

Não utilizar métodos que causem grandes travamentos.

17. SAFE ZONE

Criar um sistema de Safe Zone inspirado no Fortnite.

A primeira zona deve ser configurável.

Depois, criar novas zonas automaticamente.

As zonas seguintes devem ser aleatórias, mas sempre jogáveis.

A próxima zona deve ficar dentro ou parcialmente dentro da zona anterior.

Nunca gerar uma zona impossível ou fora do mapa.

Permitir configurar:

Tamanho inicial

Tamanho final

Centro

Quantidade de fases

Duração

Tempo de espera

Tempo de fechamento

Velocidade

Mostrar a próxima Safe Zone aos jogadores.

18. BARREIRA / STORM

Criar uma barreira visível e funcional.

O jogador pode sair dela.

Porém, fora da barreira:

Recebe dano periódico.

O dano pode aumentar conforme a partida avança.

Mostrar aviso.

Tocar som.

Mostrar efeitos.

A barreira deve diminuir gradualmente.

A partida termina quando:

Restar apenas um jogador

Todos os jogadores morrerem

A zona chegar ao tamanho final conforme as regras configuradas

19. EFEITOS DA BARREIRA

Adicionar:

Partículas

Sons

Efeitos visuais

BossBar

ActionBar

Avisos na tela

Quando o jogador entrar na Storm:

"⚠ VOCÊ ESTÁ FORA DA ZONA!"

Mostrar também a distância até a Safe Zone.

20. ELIMINAÇÕES

Quando um jogador morrer:

Marcar como eliminado.

Impedir que continue participando.

Remover do Battle Royale.

Restaurar seus itens do SMP.

Registrar kills.

Mostrar mensagem de eliminação.

Permitir espectador.

Criar estatísticas:

Kills

Mortes

Colocação

Dano causado

Dano recebido

Jogadores eliminados

21. ESPECTADOR

Após ser eliminado, permitir modo espectador se estiver ativado(configurado no plugin e a pessoa que morreu decide se quer assistir ou voltar logo para o spawn).

O jogador não pode:

Pegar itens

Interferir

Abrir baús

Quebrar blocos

Colocar blocos

Ajudar jogadores vivos

Ao terminar a partida, restaurar o estado original do jogador.

22. VENCEDOR

Quando restar apenas um jogador:

Parar a partida.

Mostrar título grande na tela.

Anunciar o vencedor no chat.

Mostrar quantidade de kills.

Mostrar colocação.

Tocar som.

Utilizar partículas.

Restaurar o inventário original.

Devolver o jogador ao spawn do SMP.

Exemplo:

🏆 VICTORY ROYALE

[Nome do jogador]

1º LUGAR
X ELIMINAÇÕES

23. SAÍDA DO MAPA

Definir uma área máxima do Battle Royale.

Se um jogador sair da área permitida:

Avisar.

Impedir que explore fora do mapa.

Eliminá-lo ou teleportá-lo de volta conforme configuração.

Restaurar seus itens do SMP.

Não permitir que o jogador use isso para escapar da partida.

24. MAPAS E ROTAÇÃO

Suportar vários mapas.

Cada mapa possui seu próprio:

Mundo

Centro

Tamanho

Local inicial dos jogadores

Rota do ônibus

Zonas

Loot

Configurações

Criar sistema de rotação:

Mapa 1 → Mapa 2 → Mapa 3 → Mapa 1...

Permitir ordem configurável.

Também permitir selecionar um mapa específico manualmente.

25. SISTEMA AUTOMÁTICO

Permitir configurar:

Intervalo entre eventos

Hora de início

Mínimo de jogadores

Máximo de jogadores

Duração

Mapa

Exemplo:

A cada 30 minutos:

"⚔ BATTLE ROYALE COMEÇARÁ EM 60 SEGUNDOS!"

[ ENTRAR NO BATTLE ROYALE ]

26. COMANDOS

Criar comandos administrativos completos.

Exemplos:

/br start
/br stop
/br reload
/br status
/br join
/br leave
/br forcestart
/br forceend
/br map
/br map list
/br map next
/br map set
/br setstart
/br setbusstart
/br setbusend
/br setcenter
/br setborder
/br setspawn
/br regenerate
/br loot
/br debug

Os nomes podem ser ajustados, mas todos os sistemas necessários devem possuir comandos administrativos.

27. PERMISSÕES

Criar permissões organizadas.

Exemplo:

battleroyale.admin
battleroyale.start
battleroyale.stop
battleroyale.reload
battleroyale.setup
battleroyale.spectate

28. CONFIGURAÇÃO

Tudo que for possível deve estar configurável em YAML.

Separar configurações em arquivos organizados, por exemplo:

config.yml
maps.yml
loot.yml
items.yml
messages.yml
permissions.yml

Permitir configurar:

Mensagens

Cores

Sons

Partículas

Tempos

Mapas

Rotação

Loot

Raridades

Itens

Grappler

Zonas

Barreira

Ônibus

Elytra

Kit inicial

Quantidade mínima/máxima

Comandos

Permissões

29. MENSAGENS

Criar mensagens modernas.

Usar:

Cores

Símbolos

Títulos

ActionBar

BossBar

Sons

Partículas

Contagens regressivas

Exemplos:

"⚔ BATTLE ROYALE"

"🔥 A partida começa em 10 segundos!"

"🚌 O ônibus está partindo!"

"🪂 Escolha onde pousar!"

"⚠ A SAFE ZONE está diminuindo!"

"☠ Você foi eliminado!"

"🏆 VICTORY ROYALE!"

As mensagens devem ser configuráveis.

30. ESTADOS DA PARTIDA

Criar uma máquina de estados clara:

WAITING
COUNTDOWN
BUS
GLIDING
ACTIVE
STORM
FINAL
ENDING
REGENERATING

Não permitir que sistemas de uma fase interfiram incorretamente em outra.

31. REINÍCIO DO SERVIDOR

Se o servidor reiniciar durante uma partida:

Detectar partidas incompletas.

Recuperar os jogadores.

Restaurar inventários.

Limpar o estado temporário.

Impedir perda de itens.

Restaurar o mapa.

Cancelar a partida com segurança.

Nunca deixar um jogador preso em um estado temporário.

32. PERFORMANCE

O plugin deve ser otimizado.

Evitar:

Loops desnecessários

Tarefas executadas a cada tick sem necessidade

Operações pesadas no thread principal

Carregamento excessivo de chunks

Regeneração causando freeze

Armazenamento ineficiente

O sistema do ônibus, Elytra, Storm, loot e regeneração deve ser otimizado para servidores com vários jogadores.

33. SEGURANÇA / ANTI-EXPLOIT

Prevenir:

Duplicação de itens

Duplicação do Grappler

Duplicação de loot

Duplicação de itens do SMP

Exploits de morte

Exploits de desconexão

Exploits de teleporte

Exploits de comandos

Exploits de construção

Fuga da área

Manipulação dos inventários salvos

Nunca permitir que um jogador consiga duplicar os itens do SMP entrando e saindo do Battle Royale.

34. FLUXO COMPLETO

O fluxo final deve ser:

O sistema aguarda o próximo evento.

O evento é anunciado.

Aparece botão clicável para entrar.

Abre período de entrada de 30–60 segundos.

Jogadores entram.

Inventários são salvos.

Jogadores permanecem no local configurado.

Contagem regressiva termina.

Jogadores são colocados no ônibus.

O ônibus começa a percorrer a rota.

Jogadores escolhem quando saltar.

O jogador recebe uma Elytra temporária.

O jogador plana até o local escolhido.

Ao tocar no chão pela primeira vez, a Elytra é removida automaticamente.

O jogador não recebe dano de queda do pouso inicial.

Kit inicial é entregue.

Battle Royale começa.

Jogadores procuram loot.

Jogadores podem construir e destruir.

Safe Zone começa a mudar.

Storm começa a fechar.

Jogadores fora da zona recebem dano.

Jogadores são eliminados.

Eliminados podem assistir.

A zona continua diminuindo.

Restam poucos jogadores.

Último jogador vivo vence.

Mostrar Victory Royale.

Restaurar inventários originais.

Jogadores retornam ao SMP.

Limpar entidades e itens.

Restaurar mapa.

Restaurar baús.

Gerar novo loot.

Selecionar próximo mapa.

Preparar próximo evento.

35. ESTRUTURA DO PROJETO

Organize o código profissionalmente.

Separar sistemas em classes/módulos como:

BattleRoyaleManager

MatchManager

PlayerManager

PlayerDataManager

MapManager

MapRotationManager

LootManager

RarityManager

SpecialItemManager

GrapplerManager

BusManager

ElytraManager

StormManager

SafeZoneManager

RegenerationManager

SpectatorManager

CommandManager

MessageManager

ConfigManager

Os nomes podem ser diferentes, mas a arquitetura deve ser modular.

36. COMPATIBILIDADE

Faça o plugin utilizando a API adequada para a versão do servidor indicada no projeto.

Evite NMS desnecessário quando houver API oficial equivalente.

Caso alguma função realmente necessite de implementação específica da versão, organize-a de maneira isolada para facilitar futuras atualizações.

37. RESULTADO FINAL

Quero receber um plugin REAL e COMPLETO.

Não entregue:

Pseudocódigo

Funções vazias

TODO

Placeholders

Sistemas simulados

Comandos que não funcionam

Itens apenas com nomes

Grappler falso

Ônibus que apenas teleporta instantaneamente

Storm apenas visual

Regeneração falsa

Salvamento de inventário incompleto

Tudo deve estar conectado e funcionando.

O resultado deve ser um Battle Royale completo para Minecraft, inspirado na experiência de Fortnite, mas adaptado ao Minecraft vanilla, integrado ao meu SMP e com foco em:

DIVERTIMENTO + PERFORMANCE + SEGURANÇA DOS ITENS + MAPAS REUTILIZÁVEIS + LOOT ALEATÓRIO + PROGRESSÃO DA ZONA + MOBILIDADE + CONSTRUÇÃO + REGENERAÇÃO AUTOMÁTICA.

Priorize a estabilidade e a segurança do inventário dos jogadores acima de qualquer efeito visual.

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/54bc136d-ecf1-4533-98bf-65ea53a4723f).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```
