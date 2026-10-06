# Barreira customizada sem partículas

## Resultado
Substituir a parede de partículas por uma parede visual de vidro colorido, atravessável, sem alterar blocos do mapa e sem exigir pacote de recursos. Manter a zona redonda ou quadrada e os indicadores de distância.

## Implementação
- Remover a renderização de partículas da barreira e desvincular sua visibilidade das opções antigas de partículas.
- Criar painéis visuais que acompanham o centro e o fechamento da safe, usando a mesma geometria do dano.
- Limitar painéis por jogador e distância de renderização para controlar o custo; remover os painéis ao sair e ao finalizar a partida.
- Adicionar configurações de material, alcance e limite de painéis, com padrões para configurações existentes.
- Compilar com Java 21/Maven, inspecionar o JAR e entregar o arquivo atualizado.

## Detalhes técnicos
- Usar entidades `BlockDisplay` de vidro colorido, sem colisão, gravidade ou persistência, visíveis apenas ao participante correspondente.
- A parede cobre a altura do mundo; os painéis seguem o círculo por segmentos ou os lados do quadrado.
- Desativar partículas no cliente não desativa `BlockDisplay`; mods que escondem entidades ainda podem ocultar a parede.
- A aparência em jogo precisa ser confirmada num servidor Paper com cliente Minecraft; compilação não substitui esse teste.
