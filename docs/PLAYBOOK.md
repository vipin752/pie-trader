# PIE Trader — Option Buyer's Playbook (NIFTY first)

The owner's distilled rulebook. Everything here is either enforced in code
(marked ⚙) or is a manual pre-market/mindset discipline (marked ✋).
Data-feed-dependent checks are marked 📡 until the feed exists.

## Pre-market checklist (before 09:15)
- ✋📡 Global cues: DOW futures, DAX futures — sentiment only, not a signal.
- ✋📡 Top 5 NIFTY heavyweights on 30m: a real trend day usually needs 1–2 of
  them moving without breaking their first 15–30m extremes.
- ✋📡 **India VIX**: 18–20 is the buyer's zone. Low-and-rising → premiums expand
  with you. High (>24) or high-and-falling → sellers' market, cut size or sit out.
- ✋📡 **PCR**: >1.4–1.5 overbought (careful with calls near resistance, no BTST);
  <0.4–0.5 oversold (careful with puts; rising from 0.45 = fresh support);
  ≈1.0 = indecision.
- ✋ **Event days**: RBI policy, budget, major earnings — never pre-empt.
  Trade the follow-through after the number, or skip the day.
- ✋ **Emotional filter**: conflict, stress, bad sleep, big argument → no trading
  today. Unstable mental capital loses real capital.

## Setups (in code)
- ⚙ **PDH/PDL breakout** (`pdhl_break`): previous day's high/low marked;
  the first candle of the day is observation only; a candle closing beyond
  PDH/PDL with >80% body is the signal; stop beyond the breakout candle.
- ⚙ **Stop-and-reverse**: if the breakout fails and the candle that takes the
  stop closes strongly the other way, the reverse trade is armed once.
- ⚙ **Sweep → BOS** (`sweep_bos`): liquidity taken, then structure breaks the
  other way. Stop beyond the sweep extreme.
- ⚙ **Trend BOS** (`trend_bos`): BOS aligned with the 15m trend.
- ⚙ **26-SMA filter**: longs only above, shorts only below the 26-SMA of the
  execution timeframe (optional, `sma_filter = true`).
- ⚙ **Reverse scalp**: a profitable CHoCH exit arms the same CHoCH the other way.

> **Precedence note:** the Guard always wins. With `stop_after_first_loss = true`
> a stop-and-reverse after a *losing* breakout will be blocked — that is the
> one-loss rule doing its job. To actually trade stop-and-reverse, set
> `max_consecutive_losses = 2` and `cooldown_minutes = 0` instead, and accept
> that the second loss ends the day.

## Risk and management (in code)
- ⚙ 1:2 minimum room to the target; ratchet trail +1R→BE, +2R→lock +1R, ….
- ⚙ Sizing from ₹ risk at the stop; ATR noise floor: a stop inside normal
  noise (< ~0.8× ATR of the execution TF) is widened or the trade is skipped.
- ⚙ Three session windows (09:20 / 11:30 / 13:45), one trade per window.
- ⚙ One booked loss → done for the day. Two losses can never happen.
- ⚙ Daily target is a minimum: after banking it, half risk, and the day ends
  only if half the peak is given back.
- ⚙ No averaging losers. No expiry-day entries. No naked weekly overnight
  without ≥1R cushion and stop ≥ BE.
- ✋ Stop-loss lives **in the broker terminal**, never in the head.

## Capital and psychology
- ✋ **1/10th reset rule**: after a losing streak, drop to one-tenth size until
  the head is straight. Never refill a blown account from debt or savings.
- ✋ **40-day rule**: one setup, 40 consecutive sessions, half size, journal —
  before changing anything.
- ✋ Judge performance monthly, not per day and never per minute.
- ✋ Journal every trade: screenshot, entry/exit, R, emotional state.
- ✋ No social-media P&L screenshots as input. No "I am God" sizing after a streak.
- ✋ Purpose first: trade to cover a real obligation (an EMI, a bill) before
  trading to get rich. Extraction discipline beats compounding fantasies.
