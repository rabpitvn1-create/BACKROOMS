# Adding Items

The item system is data-driven. Adding an ordinary item must not require changes to Kotlin, LiteRT labels, the save codec, or the UI.

## Golden path

1. Add one object to `app/src/main/assets/items/item_catalog.json`.
2. Add the item ID to one or more tables in `app/src/main/assets/items/loot_tables.json`.
3. Add an icon only if the UI needs one. The catalog may reference it with `icon`.
4. Run `python3 android-apk/validate-item-content.py`.
5. Run the normal Android preflight/tests.

## Core invariants

- New physical item quantity may enter authoritative state only through `EXPLORE_LOOT` or `ENTITY_DROP`.
- Narrative text, UI text, LiteRT, Gemini, world descriptions, and Omnivault never create item quantity.
- Transfer moves existing quantity. It never copies it.
- Give-and-use first transfers ownership, then the recipient uses the item atomically.
- Requesting an item moves existing quantity from the requested character to Kai.
- Discard destroys owned quantity. It never creates a world item or a pickup pile.
- Omnivault only stores/withdraws existing items and may restore existing equipment. It never scans, copies, duplicates, upgrades, or creates items.

## Item definition

Minimal example:

```json
{
  "id": "example-item",
  "name": "Example Item",
  "category": "MATERIAL",
  "stackMode": "STACK",
  "maxStack": 99,
  "transferable": true,
  "discardable": true,
  "effects": [],
  "aliases": ["example"]
}
```

Supported `category` values:

`CONSUMABLE`, `TOOL`, `AMMO`, `MATERIAL`, `EQUIPMENT`, `KEY_ITEM`, `OTHER`.

Supported `stackMode` values:

- `STACK`: ordinary stackable goods.
- `INSTANCE`: unique runtime item. `maxStack` must be `1`.

## Effects

The catalog references stable engine effect handlers. Current handlers are:

- `WATER`
- `FOOD`

Adding another item that uses an existing effect is data-only. Adding a genuinely new gameplay effect requires one new engine handler, after which any number of items can reuse it from JSON.

## Containers

For a three-state container, declare:

```json
{
  "contentModel": "FULL_LOW_EMPTY",
  "stateNames": {
    "FULL": "Full name",
    "LOW": "Low-content name",
    "EMPTY": "Empty name"
  }
}
```

The runtime state becomes `FULL -> LOW -> EMPTY`; exact percentages or milliliters are not used.

## Loot tables

Explore tables use `explore:<level>` and fall back to `explore:default`.

Entity tables use `entity:<ENTITY_ID>` and fall back to `entity:default`.

Entity tables may contain an entry with only a `weight`; that outcome means no drop. Explore quantity is granted only after the existing authoritative explore loot roll succeeds.

## What not to do

Do not add a pickup parser, a world-item object, an inventory mutation in narrative code, a special-case `name.contains(...)`, or a new LiteRT label just to support a new item. If an ordinary item requires any of those, the item system has regressed.
