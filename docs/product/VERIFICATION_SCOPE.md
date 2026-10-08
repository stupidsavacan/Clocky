# Verification scope — moto personal use and Phase 5+

Owner ruling: 2026-10-08. This changes the acceptance scope after PR #49's moto verification;
it does not change measured results or grant merge authorization.

## Current objective

The owner wants to use Clocky on their **moto g13 / Android API34 / Motorola Launcher3**.
The current acceptance criterion is reliable personal use on that host. Additional verification
on other phones, Android API versions, emulators or launchers is deferred to **Phase 5+**.
Phase 5+ is optional for the owner's own use; it is needed when broader compatibility or
public-release claims become a goal. Agents must not resume that work without an owner request.

Phase 5+ means the Phase 5 maturity work and subsequent compatibility work, not a prerequisite
for Phases 3B/3C/3D or 4. Their relevant feature and moto acceptance gates still apply.
Implementation PRs require the existing automated checks and explicit owner merge authorization.
The Phase 3A prerequisite for 3B/3C is owner merge of the implementation and this revised scope;
it no longer requires a non-moto device matrix to pass. The entire Phase 3 is not complete:
Canvas, Library/Stacked and platform work remain separate backlog items.

## Retained requirements

- Preserve widget IDs/settings and existing appearance on moto; restore temporary test changes.
- Use the shared production Preview/resolver/fit/composer and effective-font/AMPM contracts.
- Keep host-side TextClock ticking, goAsync completion and stale-generation protection.
- Keep meaningful unit/Robolectric, lint and build checks, including existing multi-SDK tests.
  Deferring external-device verification does not remove API23–30 pair support or API31+ Keying A.
- Keep the400ms p95 generation target. moto's measured per-class p95 is below400ms;
  first-update latency remains documented. Do not raise the budget or claim cold-start certification.
- Newly observed defects affecting the owner's moto use remain current work. Do not dismiss a
  reproducible moto regression by placing it in Phase 5+.

PR #49's [measurement record](../measurements/phase3a2/RESULTS.md) establishes moto four-class
render/Preview parity, worst-case sends, held-drag callbacks, settings-preserving upgrade and
process-absent ticking. The owner accepted that coverage and stopped further verification.
This is acceptance for the tested widget use, not proof of every Alarm/Timer/app feature.

Production minResize stays250×40dp; default minWidth250dp/targetCell4×2 stay unchanged.
The110×40dp candidate passed representative moto/API30 checks but is not enabled by this policy PR.
Any later widening is a separate change with a template gate and moto regression evidence;
non-moto certification is not its current prerequisite.

## Phase 5+ follow-up

| Item | Recorded state / future acceptance |
|---|---|
| Pixel API35 performance | Card427.57ms /Square671.30ms provider p95 exceeds400ms. Recheck with stable host resources, profile and optimize before claiming this host meets the target. |
| API35 alarm cold-boot crash | Existing AOSP PendingIntent mutability failure reproduced on PR APK. Keep the failed observation; investigate/fix before broader API35 acceptance. AOSP-domain changes still need owner authorization. No claim that moto proves this alarm path safe. |
| Other APIs/launchers | Additional Pixel/API23–33/35+, One UI, Nova, Lawnchair and other phones; include upgrade, rotation, live resize, Preview parity, memory/Parcel and process-absent ticking where supported. Preserve already collected evidence, record omissions. |
| Broader maturity audits | RTL/locales/font scale, accessibility, battery/performance and real backup/restore; execute when needed for broader release claims. |

These are deferred requirements, not passed tests. In particular, the API35 performance and
alarm failures no longer block moto personal-use acceptance or follow-on feature work after
owner merge. Public compatibility/restore/no-crash claims must remain limited to actual evidence.

## Document precedence

This current scope applies to the End-State roadmap/DoD, implementation backlog, Phase 3
architecture gates, device checklist and PR #49 measurement status. Older measurements, port
phase numbering, archived plans and historical verification checklists remain records of their
time; their non-moto requirements must not be treated as current personal-use gates.

The long-term product boundary remains [CLOCKY_END_STATE.md](CLOCKY_END_STATE.md).
Implementation status remains [IMPLEMENTATION_BACKLOG.md](../IMPLEMENTATION_BACKLOG.md).
