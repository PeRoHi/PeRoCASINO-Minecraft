# Decisions

Assumes for the economy-integrity change. Game payout rates and match rules are unchanged.

- Unsettled slot sessions (GUI close, quit, plugin disable) refund the already-taken bet. Chips are never voided. Refund prefers inventory; remainder goes to wallet; if the wallet would overflow, remainder is dropped at the player's feet.
- `/casino <selector>` targeting another player requires `perocasino.admin` when the sender is a player. Self, console, and command blocks are unchanged.
- Roulette right-click opens the bet GUI only on the grindstone registered in `config.yml` (`roulette.world` / `x` / `y` / `z`). Other grindstones keep vanilla behavior.
- Wallet deposit that would overflow `int` is rejected (balance unchanged, items stay). Debt/interest that would overflow is capped at `Integer.MAX_VALUE` so debt cannot wrap to 0.
- Plugin disable refunds open/saved roulette chips (board diamonds + all-in) to wallets so they are not destroyed with the in-memory maps.
