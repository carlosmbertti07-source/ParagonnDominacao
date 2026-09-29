# ParagonnDominacao

Dominação de área por clans (SimpleClans) para **Spigot/Paper 1.8.8**, com cronômetro por clan, pontos,
ranking, ActionBar, tag do clan dominante, NPCs (Citizens) e PlaceholderAPI.

## Requisitos

| Plugin | Obrigatório | Observação |
|---|---|---|
| SimpleClans | **Sim** | Na 1.8 use o build **`simpleclans-mc1.8`**. As versões mais novas exigem Java 16+ (não rodam em Java 8). |
| Citizens | Não (necessário para os NPCs) | Use um build legado compatível com 1.8.8. |
| PlaceholderAPI | Não (recomendado) | Placeholders `%dominacao_*%` e a tag no chat. |
| WorldGuard 6.x | Não | Só para importar uma região existente no `/dominacao create`. |
| LuckPerms | Não | Integração por comandos configuráveis (ver "Tag do dominante"). |

O jar é **Java 8** (bytecode 52): roda em Java 8 e em JVMs mais novas.
SQLite e MySQL usam os drivers que já vêm no Spigot 1.8.8, então nada extra é empacotado.

## Compilar

Precisa de um JDK 17+ para rodar o Gradle. O projeto usa o JDK 25 em `..\runtime`, mas qualquer JDK 17+ serve.

```bat
build.bat
```
ou
```bat
set JAVA_HOME=C:\caminho\do\jdk
gradlew.bat build
```
Resultado: `build/libs/ParagonnDominacao-1.0.0.jar`

## Instalar

1. Coloque `ParagonnDominacao-1.0.0.jar` em `plugins/`, junto com SimpleClans e, opcionalmente, Citizens, PlaceholderAPI e WorldGuard.
2. Inicie o servidor uma vez para gerar `plugins/ParagonnDominacao/config.yml` e `messages.yml`.
3. No jogo, defina a área:
   ```
   /dominacao pos1          (em um canto da mina)
   /dominacao pos2          (no canto oposto)
   /dominacao create minapvp
   ```
   Para usar uma região do WorldGuard: `/dominacao create minapvp nomeDaRegiao`
4. Posicione os NPCs no local onde você está: `/dominacao npc top` e `/dominacao npc campeao`.

## Onde alterar cada coisa

| O que | Onde |
|---|---|
| Tempo para dominar (padrão 15 min) | `config.yml` → `arenas.minapvp.domination-time` (segundos) ou `/dominacao settime minapvp 15` |
| Pontos por abate / por dominação | `arenas.minapvp.points.kill` / `.domination` ou `/dominacao setpoints kill 5` / `setpoints capture 50` |
| Anti-farm | `arenas.minapvp.anti-farm.enabled` / `kill-cooldown` (segundos, padrão 300 = 5 min) |
| Pausar quando disputado | `arenas.minapvp.timer.pause-when-contested` (padrão `false`) |
| Resetar quando o clan sai | `arenas.minapvp.timer.reset-when-clan-leaves` |
| Dominante pode redominar | `arenas.minapvp.timer.owner-can-recapture` |
| Tag do dominante | `arenas.minapvp.rewards.domination-tag.tag` (+ `show-in-chat`, `show-above-head`) |
| Comandos de recompensa | `rewards.domination-tag.gain-commands` / `lose-commands` / `rewards.capture-commands` |
| Intervalo dos NPCs | `arenas.minapvp.npcs.update-interval` |
| Frequência da verificação / ActionBar | fixa em 1 segundo (o cronômetro sobe de 1 em 1) |
| Nome exibido da arena | `arenas.minapvp.display-name` |
| Skin do NPC de top | `arenas.minapvp.npcs.top-skin` (nick de um jogador; `''` = padrão) |
| Altura dos hologramas | `arenas.minapvp.npcs.hologram-height` (padrão 1.9, suba ou desça de 0.1 em 0.1) |
| Menu do NPC de top | `menu.yml` |
| Raio no NPC campeão ao dominar | `arenas.minapvp.npcs.capture-lightning.enabled` / `.strikes` |
| Parede de combate | `arenas.minapvp.combat-wall.*` (duração, raio, material, teleportes bloqueados) |
| Quem vê a ActionBar | `settings.actionbar-dispute-recipients` / `actionbar-holding-recipients` |
| SQLite ↔ MySQL | `database.type` + `database.mysql.*` |
| Todos os textos, ActionBar, hologramas | `messages.yml` |

Depois de editar, use `/dominacao reload`. O progresso em andamento não é perdido.
O `config.yml` tem a documentação completa no cabeçalho. Ele fica sem acentos porque o Bukkit 1.8 regrava o arquivo usando o charset do sistema. O `messages.yml` é lido em UTF-8 e aceita acentos.

### Nova arena
Basta repetir o processo com outro nome, por exemplo `/dominacao create arena2`. O plugin cria a seção `arenas.arena2` com os valores padrão, e cada arena tem pontos, ranking, NPCs e dominante próprios.

## Regras implementadas

- **Participação:** só jogadores **com clan** participam. Mortos e os modos em `ignored-gamemodes` não contam presença.
- **Cronômetro por clan:** continua enquanto houver pelo menos 1 membro dentro. Se todos saem, volta a `00:00` e o clan recebe o aviso `reset`.
- **Captura:** o clan recebe `+points.domination`, vira o dominante, a tag passa do antigo para o novo dominante, há anúncio no servidor e os NPCs são atualizados.
- **Abates:** valem `+points.kill` somente quando a vítima está dentro da arena e o assassino também (configurável). Não contam:
  - morte sem assassino;
  - suicídio;
  - vítima ou assassino sem clan;
  - abate dentro do mesmo clan;
  - aliados (configurável);
  - repetição dentro do cooldown anti-farm (mesma vítima → mesmo clan).
- **Mortes:** toda morte de jogador com clan dentro da arena conta nas estatísticas.

## Holograma de top de clans

`/dominacao holograma set [arena]` coloca, onde você está, um holograma avulso (sem NPC) com o título, uma descrição e o top de clans por pontos. `/dominacao holograma remove [arena]` remove.

- **Formato:** `[TAG] - pontos`. Textos e cores ficam no `messages.yml` → `hologram.board`.
- **Quantidade:** o número de clans vem de `settings.top-size`.
- **Posição:** é salva no `config.yml` → `arenas.<arena>.top-hologram`.
- **Atualização:** no intervalo `npcs.update-interval`, e na hora em capturas e no `/dominacao points`.

Acima do NPC de top ficam só duas linhas, "TOP DOMINAÇÃO" e o nome da arena (`hologram.top.lines`). O ranking completo fica no menu e no holograma avulso.

## Menu do NPC de top

Ao clicar (botão direito ou esquerdo) no NPC criado com `/dominacao npc top`, o jogador abre um menu com **cabeças personalizadas**. Cada cabeça é um ranking, e a descrição (ao passar o mouse) lista as primeiras posições e a posição do jogador ou do seu clan:

| Slot | Cabeça | Ranking |
|---|---|---|
| 12 | Bloco de ferro | TOP Abates (jogadores) |
| 13 | Bloco de ouro | TOP Pontos (clans) |
| 14 | Bloco de diamante | TOP Dominações (clans) |
| 31 | Bloco de redstone | Fechar |

Tudo é configurável no `menu.yml`: título, linhas, quantidade de posições, slots, textos e texturas. Para trocar uma cabeça, copie o campo **Value** de uma cabeça em minecraft-heads.com para `texture`. Os tipos de ranking disponíveis são `CLAN_POINTS`, `CLAN_DOMINATIONS`, `CLAN_KILLS` e `PLAYER_KILLS`. Abrir o menu exige a permissão `dominacao.top`.

## Parede de combate

Quem dá ou recebe dano de outro jogador **dentro da arena** entra em combate por `combat-wall.combat-seconds` (padrão 15 s). Cada novo golpe renova o tempo. Enquanto estiver em combate, o jogador não sai da arena:

- **Parede só para ele:** a borda da arena, num raio de `combat-wall.radius` blocos ao redor do jogador, vira blocos falsos (`sendBlockChange`). Só o jogador em combate vê e colide com eles. Para todos os outros a borda continua livre, então quem está fora entra normalmente.
- **Blocos reais preservados:** os blocos falsos só aparecem onde o bloco real não é sólido. Paredes reais nunca são alteradas.
- **Material:** o padrão é vidro vermelho (`STAINED_GLASS` + `data: 14`), para o jogador enxergar o limite. Use `BARRIER` para uma parede invisível de verdade.
- **Proteção contra clientes modificados:** se o cliente atravessar os blocos falsos, o servidor cancela o movimento para fora. Teleportes por pérola, comando ou plugin também são bloqueados (`combat-wall.blocked-teleports`).
- **Fim do combate:** o combate acaba quando o tempo expira ou o jogador morre. A visão real dos blocos é restaurada.
- **Altura da região:** a região precisa cobrir toda a altura jogável da arena, porque sair pelo teto ou pelo chão também conta como sair.

## Comandos

| Comando | Permissão |
|---|---|
| `/dominacao`, `/dominacao help` | `dominacao.use` |
| `/dominacao status [arena]` | `dominacao.use` |
| `/dominacao top [arena]` | `dominacao.top` |
| `/dominacao stats [arena]` | `dominacao.stats` |
| `/dominacao pos1`, `pos2`, `create <arena> [regiaoWG]` | `dominacao.create` |
| `/dominacao delete <arena>` | `dominacao.delete` |
| `/dominacao reload` | `dominacao.reload` |
| `/dominacao settime <arena> <minutos>` | `dominacao.admin` |
| `/dominacao setpoints <kill\|capture> <qtd> [arena]` | `dominacao.admin` |
| `/dominacao points <add\|remove\|set> <clan> <qtd> [arena]` | `dominacao.points` |
| `/dominacao reset clan <clan> [arena]` / `reset arena <arena>` | `dominacao.admin` |
| `/dominacao npc <top\|campeao> [arena]` | `dominacao.npc` |
| `/dominacao holograma <set\|remove> [arena]` | `dominacao.npc` |

Aliases: `/dom`, `/dominar`. `dominacao.admin` inclui todas as permissões. `use`, `top` e `stats` são liberadas para todos por padrão.
Sem `[arena]`, os comandos usam `settings.default-arena` (`minapvp`).

## Placeholders (PlaceholderAPI)

Arena padrão: `%dominacao_<x>%`. Arena específica: `%dominacao_<arena>_<x>%`.

| Placeholder | Valor |
|---|---|
| `top_1_clan`, `top_1_pontos`, `top_1_dominacoes` (1..N) | Ranking |
| `dominante` | Clan dominante |
| `tag` | Tag de dominante, se o clan do jogador domina alguma arena (use no chat) |
| `clan`, `pontos`, `posicao`, `dominacoes` | Dados do clan do jogador |
| `kills`, `mortes`, `kdr`, `tempo`, `progresso` | Dados do jogador / progresso do clan |

## Tag do dominante

**Todos os membros do clan dominante** recebem automaticamente a tag de `rewards.domination-tag.tag` (padrão `&6[Dominante]`):

- `show-in-chat: true`: a tag aparece antes do nome no chat. Ela é inserida no formato do chat, então funciona com o chat padrão e com a maioria dos plugins de chat.
- `show-above-head: true`: a tag aparece acima da cabeça e no TAB, usando o prefixo de um time do scoreboard principal. O limite é de 16 caracteres na 1.8. Desligue essa opção se outro plugin controlar os nametags.
- **Atualização automática:** acontece na captura (na hora), ao entrar no servidor e a cada 2 s. Quem entra no clan ganha a tag e quem sai perde, sem nenhum comando.

Outras formas, opcionais:
- **Placeholder:** `%dominacao_tag%` no formato de um plugin de chat. Nesse caso, desligue `show-in-chat` para a tag não aparecer duas vezes.
- **LuckPerms:** use `gain-commands` e `lose-commands`, que são executados para cada membro:
  ```yaml
  gain-commands: ['lp user %player% meta setsuffix 100 " %tag%"']
  lose-commands: ['lp user %player% meta removesuffix 100']
  ```
  Observação: com comandos, quem entrar no clan depois da captura só recebe a tag na próxima captura. O placeholder não tem essa limitação.

## Banco de dados

As tabelas usam o prefixo `dom_`: `clans`, `players`, `arenas` (clan dominante) e `npcs`.
Os dados ficam em memória e são gravados a cada `save-interval-seconds` e no desligamento. As gravações rodam numa thread própria, nunca na thread principal.

## Arquitetura

```
DominacaoPlugin          monta os componentes e controla o ciclo de vida
config/                  ConfigManager, MessageManager, GlobalSettings, ArenaSettings, DatabaseSettings
arena/                   Arena (config + estado em tempo real), Cuboid, ArenaManager, SelectionManager
clan/ClanManager         fachada de clans (usa hook/SimpleClansHook)
domination/              DominationManager (varredura, cronômetros, captura, ActionBar), TagRewardManager
kill/KillManager         abates, mortes e anti-farm
combat/CombatWallManager combate na arena: parede de blocos falsos por jogador + bloqueio de saída
score/                   ScoreManager (cache + ranking + write-behind), ClanStats, PlayerStats
npc/                     NpcManager (NPC top / campeão, cliques), Hologram (ArmorStands)
menu/TopMenu             menu de rankings com cabeças personalizadas (menu.yml)
database/                DatabaseManager (conexão + thread do banco), DataRepository (SQL)
hook/                    SimpleClansHook, CitizensHook, NpcProvider, PlaceholderHook, WorldGuardHook
command/                 DominacaoCommand + um arquivo por subcomando
util/                    ActionBar (NMS por reflexão), Text
```

**Performance:**
- A área é verificada a cada 10 ticks, olhando apenas os jogadores **do mundo da arena**.
- A ActionBar é enviada a cada 1 s, só para quem está dentro.
- Os NPCs são atualizados no intervalo configurado, ou na hora em capturas e abates relevantes.
- O ranking fica em cache e só é recalculado quando os pontos mudam.
