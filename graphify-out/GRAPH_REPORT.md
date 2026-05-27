# Graph Report - .  (2026-05-11)

## Corpus Check
- Corpus is ~31,964 words - fits in a single context window. You may not need a graph.

## Summary
- 695 nodes · 1023 edges · 75 communities (15 shown, 60 thin omitted)
- Extraction: 80% EXTRACTED · 20% INFERRED · 0% AMBIGUOUS · INFERRED: 206 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Dungeon Phase Tracking|Dungeon Phase Tracking]]
- [[_COMMUNITY_Mod Init & Feature Registration|Mod Init & Feature Registration]]
- [[_COMMUNITY_Party Command Handler|Party Command Handler]]
- [[_COMMUNITY_Entity Utilities|Entity Utilities]]
- [[_COMMUNITY_HUD Editor & Slot Events|HUD Editor & Slot Events]]
- [[_COMMUNITY_Debug & Command System|Debug & Command System]]
- [[_COMMUNITY_Dungeon Split Tracking|Dungeon Split Tracking]]
- [[_COMMUNITY_Text & Style Processing|Text & Style Processing]]
- [[_COMMUNITY_Boss Bar & Folder Utils|Boss Bar & Folder Utils]]
- [[_COMMUNITY_Bridge Bot & Mod Screen|Bridge Bot & Mod Screen]]
- [[_COMMUNITY_Wiki Screen Browser|Wiki Screen Browser]]
- [[_COMMUNITY_Packet & Player List Events|Packet & Player List Events]]
- [[_COMMUNITY_Slayer XP Tracker|Slayer XP Tracker]]
- [[_COMMUNITY_Render Utilities|Render Utilities]]
- [[_COMMUNITY_Mouse & Warp Map|Mouse & Warp Map]]
- [[_COMMUNITY_Keybinds & Misc Utils|Keybinds & Misc Utils]]
- [[_COMMUNITY_Rendering Events|Rendering Events]]
- [[_COMMUNITY_Powder Tracker|Powder Tracker]]
- [[_COMMUNITY_Fish Party Tracker|Fish Party Tracker]]
- [[_COMMUNITY_Custom Events System|Custom Events System]]
- [[_COMMUNITY_Config Category & Label Settings|Config Category & Label Settings]]
- [[_COMMUNITY_Config Setting Components|Config Setting Components]]
- [[_COMMUNITY_Waypoint System|Waypoint System]]
- [[_COMMUNITY_Input Int Setting|Input Int Setting]]
- [[_COMMUNITY_Block Entity Events|Block Entity Events]]
- [[_COMMUNITY_Slot Change Events|Slot Change Events]]
- [[_COMMUNITY_Draw Context Mixin|Draw Context Mixin]]
- [[_COMMUNITY_FishMod Assets & Docs|FishMod Assets & Docs]]
- [[_COMMUNITY_Color Setting|Color Setting]]
- [[_COMMUNITY_Dropdown Setting|Dropdown Setting]]
- [[_COMMUNITY_Input Setting|Input Setting]]
- [[_COMMUNITY_Slider Int Setting|Slider Int Setting]]
- [[_COMMUNITY_Button Setting|Button Setting]]
- [[_COMMUNITY_Toggle Setting|Toggle Setting]]
- [[_COMMUNITY_Subcategory Header|Subcategory Header]]
- [[_COMMUNITY_Inventory Screen Mixin|Inventory Screen Mixin]]
- [[_COMMUNITY_Skull Block Entity Renderer|Skull Block Entity Renderer]]
- [[_COMMUNITY_Status Effects Display|Status Effects Display]]
- [[_COMMUNITY_Mod Menu Integration|Mod Menu Integration]]
- [[_COMMUNITY_Info Setting|Info Setting]]
- [[_COMMUNITY_Entity Glow Mixin|Entity Glow Mixin]]
- [[_COMMUNITY_Recipe Book Screen Mixin|Recipe Book Screen Mixin]]
- [[_COMMUNITY_Recipe Book Widget Mixin|Recipe Book Widget Mixin]]
- [[_COMMUNITY_Stuck Arrows Renderer|Stuck Arrows Renderer]]
- [[_COMMUNITY_Boss Bar Accessor|Boss Bar Accessor]]
- [[_COMMUNITY_Key Binding Accessor|Key Binding Accessor]]
- [[_COMMUNITY_Config Components Init|Config Components Init]]
- [[_COMMUNITY_Block Interaction Event|Block Interaction Event]]
- [[_COMMUNITY_Entity Event|Entity Event]]
- [[_COMMUNITY_Game Message Event|Game Message Event]]
- [[_COMMUNITY_Particle Event|Particle Event]]
- [[_COMMUNITY_Pet Event|Pet Event]]
- [[_COMMUNITY_Phase Event|Phase Event]]
- [[_COMMUNITY_Play Sound Event|Play Sound Event]]
- [[_COMMUNITY_Run End Event|Run End Event]]
- [[_COMMUNITY_Scoreboard Event|Scoreboard Event]]
- [[_COMMUNITY_Section Event|Section Event]]
- [[_COMMUNITY_Server Tick Event|Server Tick Event]]
- [[_COMMUNITY_World Event|World Event]]
- [[_COMMUNITY_Game HUD Interface|Game HUD Interface]]
- [[_COMMUNITY_Rendering Event|Rendering Event]]
- [[_COMMUNITY_Render Layer Accessor|Render Layer Accessor]]
- [[_COMMUNITY_Constants|Constants]]
- [[_COMMUNITY_Config|Config]]
- [[_COMMUNITY_Fish Config|Fish Config]]
- [[_COMMUNITY_Dungeons Config Values|Dungeons Config Values]]
- [[_COMMUNITY_Extra Options Config|Extra Options Config]]
- [[_COMMUNITY_Fish Settings Config|Fish Settings Config]]
- [[_COMMUNITY_Floor 7 Config|Floor 7 Config]]
- [[_COMMUNITY_Visual Config|Visual Config]]
- [[_COMMUNITY_Events Registry|Events Registry]]
- [[_COMMUNITY_Draw Events|Draw Events]]
- [[_COMMUNITY_Render Pipelines|Render Pipelines]]

## God Nodes (most connected - your core abstractions)
1. `Phase` - 25 edges
2. `HypixelApi` - 24 edges
3. `WikiScreen` - 21 edges
4. `Setting` - 18 edges
5. `Split` - 17 edges
6. `Section` - 16 edges
7. `PartyCommandHandler` - 15 edges
8. `RenderUtils` - 14 edges
9. `FishModScreen` - 13 edges
10. `FishHudEditor` - 11 edges

## Surprising Connections (you probably didn't know these)
- `FishMod` --references--> `FishMod Icon (BA logo on blue background)`  [INFERRED]
  README.md → src/main/resources/assets/fishmod/icon.png
- `FishMod` --references--> `Essence Entity Texture (pixel art entity sprite)`  [INFERRED]
  README.md → src/main/resources/assets/fishmod/textures/entity/essence.png

## Communities (75 total, 60 thin omitted)

### Community 0 - "Dungeon Phase Tracking"
Cohesion: 0.05
Nodes (4): Phase, Section, Split, TerminalEvent

### Community 1 - "Mod Init & Feature Registration"
Cohesion: 0.05
Nodes (11): Bladeaddons, FishModInit, FishPuzzleDisplay, PuzzleDisplay, RunHistory, EventHandler, ModInitializer, DrawHandler (+3 more)

### Community 2 - "Party Command Handler"
Cohesion: 0.1
Nodes (5): PartyCommandHandler, ChatHudMixin, DungeonData, DungeonDataCallback, HypixelApi

### Community 3 - "Entity Utilities"
Cohesion: 0.07
Nodes (10): EntityUtil, ItemUtil, getClass(), getColor(), init(), isTeammate(), parseClass(), reset() (+2 more)

### Community 4 - "HUD Editor & Slot Events"
Cohesion: 0.06
Nodes (7): SlotEvent, FishHudEditor, HandledScreenMixin, KeyboardMixin, Screen, Scheduler, Task

### Community 5 - "Debug & Command System"
Cohesion: 0.08
Nodes (10): Debug, printClasses(), DungeonDeathMessage, SoulflowHud, LocationChangeEvent, changeLocation(), getCurrentLocation(), getLocation() (+2 more)

### Community 6 - "Dungeon Split Tracking"
Cohesion: 0.13
Nodes (3): FishEstTotal, LocalSplit, LagTracker

### Community 7 - "Text & Style Processing"
Cohesion: 0.13
Nodes (4): StyleTracker, TextUtil, ChatHudInvoker, ChatScreenMixin

### Community 8 - "Boss Bar & Folder Utils"
Cohesion: 0.14
Nodes (4): FolderUtility, BossBarFeature, FishBossBarHudMixin, SearchBar

### Community 11 - "Packet & Player List Events"
Cohesion: 0.12
Nodes (4): PacketEvent, PlayerListEvent, ClientConnectionMixin, ClientPlayNetworkHandlerMixin

### Community 19 - "Custom Events System"
Cohesion: 0.2
Nodes (4): CustomEvents, LeapEvent, PartyMessageEvent, inDungeon()

### Community 20 - "Config Category & Label Settings"
Cohesion: 0.22
Nodes (3): Category, LabelSetting, SliderDoubleSetting

### Community 27 - "FishMod Assets & Docs"
Cohesion: 0.4
Nodes (5): FishMod Icon (BA logo on blue background), FishMod, Hypixel Mod API, FishMod Version 1.21.10, Essence Entity Texture (pixel art entity sprite)

## Knowledge Gaps
- **17 isolated node(s):** `RenderLayerAccessor`, `Constants`, `DungeonData`, `Config`, `FishConfig` (+12 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **60 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ChatScreenMixin` connect `Text & Style Processing` to `HUD Editor & Slot Events`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Why does `FishHudEditor` connect `HUD Editor & Slot Events` to `Mod Init & Feature Registration`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **Why does `RenderUtils` connect `Render Utilities` to `Boss Bar & Folder Utils`, `Entity Utilities`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **What connects `RenderLayerAccessor`, `Constants`, `DungeonData` to the rest of the system?**
  _17 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Dungeon Phase Tracking` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `Mod Init & Feature Registration` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `Party Command Handler` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._