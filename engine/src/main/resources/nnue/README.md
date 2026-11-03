# NNUE networks

All four nets share one architecture (768 inputs → hidden accumulator per side → 1 output), so they
evaluate at the same speed; only their weights differ.

## Ranking

From `NnueNetComparison` (round robin, each net driving `TempoNegamaxBot`, fixed depth 6,
24 games per pairing, 2026-11-02):

| Rank | Net                  | Score     | Elo vs field | Notes |
|------|----------------------|-----------|--------------|-------|
| 1    | `default.nnue`       | 44.5 / 72 | +84          | **Best. Used by the UCI jar (`UciApp`).** Also `NnueEvaluator()`'s default. |
| 2    | `default-v0.nnue`    | 41.0 / 72 | +49          | Level with `default` head-to-head (+4 −4 =16); not separable at this sample size. |
| 3    | `iter2.nnue`         | 34.0 / 72 | −19          | Earlier training iteration. |
| 4    | `iter1.nnue`         | 24.5 / 72 | −115         | First training iteration. |

`default` and `default-v0` are both clearly stronger than `iter1`/`iter2`; between the two of
them the gap is within the noise (±~70 Elo at 72 games). Settle it with a longer head-to-head:

```powershell
.\gradlew.bat :engine:longTest --tests "engine.tournament.NnueNetComparison" "-Pnnue.cmp.nets=default,default-v0" "-Pnnue.cmp.games=200" "-Pnnue.cmp.fixedDepth=6"
```

Results land in `engine/build/test-results/longTest/TEST-engine.tournament.NnueNetComparison.xml`.

## Gating a new net

The promotion test is `NnueGate`: candidate vs `default` on OmegaNegamaxBot (what the VM plays),
10s+0.1s, every opening of the balanced suite played with both colours, stopped by an SPRT.
Promote only on `PASSED`. See NNUE_PLAN.md (repo root) for the full workflow.

```powershell
.\gradlew.bat :engine:longTest --tests "engine.tournament.NnueGate" "-Pnnue.gate.candidate=../nnue-data/candidate.nnue"
```

## Changing the deployed net

Edit `NETWORK` in `desktop/src/main/kotlin/app/UciApp.kt`, then `.\gradlew.bat :desktop:uciJar`.
Training (`:engine:trainNnue`) writes `new.nnue` (or `out=`), never `default.nnue`; a net only becomes
`default.nnue` through the gated promotion in `scripts\nnue-cycle.ps1 -Promote`.
