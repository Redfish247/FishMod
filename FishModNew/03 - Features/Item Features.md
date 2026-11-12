---
type: feature
status: implemented
category: item
updated: 2026-09-05
related_systems:
  - "[[Mixins Catalogue]]"
  - "[[Croesus Loot Tracker]]"
  - "[[Networth Engine]]"
related_files:
  - "dungeons-26.1.2/src/main/kotlin/fishmod/features/item/"
---

# Item Features

`features/item/*`. Config cards in **HUD & Overlays** ("Item Tooltip") and **Visuals & Rendering**
("Item Rarity Background"), plus `/fm customize`.

## Tooltips

| Feature | Adds | Source | Flag |
|---|---|---|---|
| **ItemPriceTooltip** | `§eValue:` (Bazaar/BIN blended base via `CroesusPrices.price` + `ModifierValue.calc`) + optional `§eNPC Sell:`; `×count` breakdown | `ItemTooltipCallback`; prices 60 s TTL | `itemTooltipPrices`, `itemTooltipNpcSell` |
| **ItemQualityTooltip** | `§6Quality Bonus: +X% (floor)` — reads `baseStatBoostPercentage` / `dungeon_skill_req` / `item_tier` from `CUSTOM_DATA`, no network | `ItemTooltipCallback` | `itemQualityTooltip` |
| **ContainerValue** | no‑background list of every item's coin value in the open container + running total; rarity‑coloured; slides right over the Storage Overlay | `HandledScreenMixin` TAIL, 500 ms recompute | `containerValueEnabled` |
| **ModifierValue** | rough "modifier worth" for the Value line: enchants (book‑doubling, CHIMERA flat), ability scrolls, potato books, recomb, master stars, gemstones, reforge stone, Art of War — priced via `CroesusPrices`, multipliers from `NwConstants`. **Not** a full networth (no pets/prestige/drill‑parts) | — | — |
| **ScrollableTooltip** | see [[General & QoL Features]] |

## Item customization — `/fm customize`

Client‑only cosmetic overrides keyed by Hypixel **item instance UUID**, never touching
NBT/DataComponents.

| Class | Role |
|---|---|
| `ItemCustomizationStore` | `dyeColors`, `animatedDyes` (legacy read‑only), `armorTrims`, `itemNames`, `modelIds` (ITEM_MODEL reskin) → `config/fishmod-item-customization.json` |
| `ItemCustomizeScreen` | NanoVG screen (`/fm customize`): pick a worn/inventory slot, edit Name / Model / Dye / armor Trim; SkyBlock dye palette + hex box |
| `ArmorTrimCache` | resolves a stored `{material, pattern}` to a real `ArmorTrim` via client registries |
| `AnimatedDyeAnimator` | ticks per‑item animated‑dye keyframe state, lerps ARGB. **Legacy only** — the current customizer writes no animated dyes; drives pre‑port config entries. Ticked from `GameRendererNvgMixin.tickFrame()` |

Read by: `DyedItemColorMixin` (dye), `ItemTrimMixin` (trim + model), `ItemModelBehaviorMixin`
(model swap → use animation), `ItemStackMixin` (custom name). See [[Mixins Catalogue]].

## Rarity

`ItemRarity` (12 rarities + tint colours), `ItemRarityHolder` (duck interface caching parsed rarity
on `ItemStack` via `ItemStackMixin`). `ItemRarityHotbar` draws the tinted sprite —
see [[General & QoL Features]].

## Known issues

- LOW‑24 — `ItemCustomizeScreen` KDoc says model override "out of scope" but it's fully wired.
- LOW‑25 / PERF §1.12 — `AnimatedDyeAnimator` `states` map unbounded, `clear()` caller unconfirmed;
  `colorFor` OOB for <2 keyframes.
- PERF §1.7 — `ItemTrimMixin` intercepts **every** `DataComponentHolder.get()` client‑wide.

## Related

[[Croesus Loot Tracker]] (`CroesusPrices`) · [[Networth Engine]] (`NwConstants`) · [[Mixins Catalogue]] · [[Settings Reference]]

## Item List (SkyItemList.kt, setting `skyItemList`)
- JEI-style panel right of any container screen: search box, scrollable grid of every NEU item, hover tooltip, left-click opens the existing RecipeScreen.
- Reuses `NeuRepo` ids/display names and `RecipeViewer.stackFor` (cached stack per id, built only for visible cells). No new fetching.
- Perf rule: sorted name/haystack arrays built once when NEU loads; filter runs only when the query changes (IntArray of indices); render loop touches only visible rows, no per-frame allocation.
- Input goes through hooks in `FishScreenMixin` (click, scroll, key) and `FishKeyMixin` (char). Own tiny text field, no EditBox. While the box is focused all keys are swallowed except Esc (unfocus), so E does not close the inventory.
- Colour/opacity: `skyItemListBgColor`, `skyItemListAccentColor`, `skyItemListOpacity`, `skyItemListColumns`. Panel shrinks columns to fit and hides if under 2 columns.
- Status: built by inspection only, not built, untested. May overlap other side panels (Party Finder, Visitors).
- Click key (#535, `skyItemListClickKey`, default on): hovered tooltip = `Screen.getTooltipFromItem` + yellow lines built from the same conditions as `onClick` (Left: View Recipe only if `RecipeViewer.recipesFor` is non-empty; Right: Add/Remove Favorite; Shift-Left: Bazaar or Auction House). Keep `tooltipWithKey` in sync when a click action changes.
- Shift-left click sends exactly one `/bz <name>` or `/ahs <name>`. Bazaar vs AH = id present in `CroesusPrices.bazaarPrice` (Hypixel bazaar products); NEU book ids `X;N` map to `ENCHANTMENT_X_N`. Search name: NEU display name, book enchant from lore line 0, pet `[Lvl ...]` tag stripped. Bazaar data is requested once when the list builds, so before it loads every item reads as AH.

## Sign Calculator (SignCalculator.kt, setting `signCalculator`, default on, fishmod.dev #531)
- Math in Hypixel input signs (bazaar/AH quantity, bids, any sign with `^^^` on line 2 or anywhere in Skyblock): typing `9*9` on line 1 shows `= 81` right of the sign; on close the line is replaced with `81` before vanilla sends it.
- Own recursive-descent parser on BigDecimal (no script engine): `+ - * /`, brackets (max depth 16), decimals, `k/m/b` suffixes on numbers or brackets, `x`/`×` as multiply, commas/spaces ignored. Result rounded to 2 decimals, trailing zeros stripped.
- Only rewrites when the text has an operator or bracket. Plain numbers (`64`, `1.5k`, `-5`) and invalid text (`9*`, `abc`, divide by zero) are sent raw, so Hypixel's own parsing still applies.
- Hook: `AbstractSignEditScreenMixin.removed()` HEAD calls `SignCalculator.beforeSend`, which edits the `messages` array through `AbstractSignEditScreenAccessor`; skipped when `AuctionPriceAutofill.suppressSignSend` cancels. The Auto BIN Price replacement screen (`AuctionPriceScreen`) has its own field and is not covered.
- Preview position (`w/2 + 52, y 72`) is a guess at the first sign line; adjust after Eli sees it. Status: not built, untested.

## See Others' Items (RemoteItems) behaviour (2026-11-11, fishmod.dev #548)
- Needs `remoteItemsEnabled` ON (default OFF) on BOTH the sharer and the viewer; the toggle also controls uploading. Customs are keyed by vanilla item type, not item uuid.
- Two-client test on a local vanilla server (real accounts, live fishmod.dev): dye, name, trim and trim+dye sync to the other client, including live edits from the Item Customize screen. A trim on the worn armor showed on the viewer even when the viewer's copy of the stack had no uuid.
- Fixed: clearing a trim/dye/name/model never reached viewers (the remote store only ever added entries); `acceptItems` now drops a player's synthetic entries before re-adding. Upload now retries after a network error/5xx/429 (`lastUploaded` was set before the POST, so one failed upload meant nothing was shared until the next change).
- Not reproduced: the Hypixel-only "friend can't see my trim" case. If it recurs, first check both players have the toggle on and run the same jar.
