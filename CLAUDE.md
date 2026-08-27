# CLAUDE.md

Guidance for working in this repository.

## Project

PeRoCasino — Spigot 1.21.4 plugin (`me.bokan.perocasino`). Java 21.

In-memory wallet/debt keyed by UUID. Games: slot, roulette, loan; blackjack/HiLo GUIs are stubs.

## Layout

- `src/main/java/me/bokan/perocasino/commands/` — `/balance` `/deposit` `/casino` `/perocasino`
- `economy/` `data/` — wallet and debt
- `games/` — slot, blackjack stub, hilo stub
- `listeners/` — GUI, wallet, quarry, roulette/slot interact
- `roulette/` — hub loop and settlement
- `tasks/` — HUD, loan interest
- `src/main/resources/config.yml` `plugin.yml`
- `docs/DECISIONS.md` — recorded assumes

## Rules

- Do not invent payout rates. Record assumes in `docs/DECISIONS.md`.
- Do not treat missing/corrupt player files as an empty store (persistence is not implemented yet).
- Do not commit secrets, world names from live servers, or `.claude/settings.local.json`.
