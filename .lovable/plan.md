# Correções da safe zone, itens, mapas e Battle Bus

## Objetivo
Eliminar o dano incorreto dentro da safe zone, ampliar e tornar configurável a borda inicial, completar itens arremessáveis e o Grappler, permitir exclusão segura de mapas e melhorar a experiência visual do Battle Bus.

## Implementação

1. **Safe zone e barreira**
   - Usar uma única geometria quadrada para dano e WorldBorder, pois a borda visual do Minecraft é quadrada; incluir tolerância interna configurável.
   - Separar raio jogável do mapa e tamanho inicial da safe zone, com multiplicador/margem configurável e padrão um pouco maior.
   - Impedir dano da própria WorldBorder e validar mundo, centro e fase antes de aplicar storm.

2. **Mapas**
   - Adicionar `/br deletar <mapa>` e alias em inglês, recusando exclusão durante partida nesse mapa.
   - Remover o mapa do YAML, da rotação e do mapa forçado, persistir, recarregar e emitir mensagens claras.

3. **Itens e loot**
   - Refazer o Grappler para lançar um projétil visual de longo alcance e resolver o impacto pela trajetória configurada, sem depender do alcance de interação vanilla.
   - Adicionar bola de fogo arremessável e mais utilitários configuráveis; todo item conceitualmente arremessável usará projétil e ativará no impacto.
   - Validar encantamentos por compatibilidade com o item e limitar níveis ao máximo vanilla; itens não encantáveis não receberão bônus.
   - Inserir os novos especiais nas tabelas de raridade adequadas.

4. **Battle Bus e apresentação**
   - Posicionar jogadores em assentos externos/superiores com câmera livre e vista do mapa, mantendo-os presos ao veículo até o salto.
   - Adicionar animações configuráveis de embarque/partida, partículas e sons sem travar a câmera.

5. **Validação e entrega**
   - Compilar com Java 21/Maven, inspecionar o JAR e confirmar que recursos e classes atualizados estão empacotados.
   - Copiar o JAR validado para download.

## Detalhes técnicos
- O cálculo da safe usará distância Chebyshev (`max(abs(dx), abs(dz))`) para coincidir com a WorldBorder quadrada.
- Projéteis especiais serão identificados por PersistentDataContainer e terão efeitos sem dano indevido quando aplicável.
- A exclusão remove apenas o registro do plugin; nunca apaga a pasta/mundo do servidor.
