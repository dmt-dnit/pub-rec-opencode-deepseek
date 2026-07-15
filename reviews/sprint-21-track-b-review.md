# Sprint 21 Track B Review - Dependency Currency

Review target: `67635c2` with companion commits `b03f211` and `8563b88`  
Handoff: `docs/backlog/sprint-21-handoff.md`  
Verdict: ACCEPT — no blocking findings

## Findings

No blocking source-level findings in Sprint 21.

## Verified Against Handoff

- **D-1 is integrated as described.** The workflow now uses `actions/checkout@v7` in eight jobs and `actions/upload-artifact@v7` in four jobs at [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:48), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:66), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:87), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:108), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:128), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:145), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:181), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:230), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:294), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:302), [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:310), and [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:318). No unrelated workflow churn is mixed into that change.

- **D-2 is integrated narrowly and consistently.** The enforcer plugin is `3.6.3` in all four Maven modules at [shared-model/pom.xml](C:/projects/pub-rec-opencode-deepseek/shared-model/pom.xml:41), [auth-server/pom.xml](C:/projects/pub-rec-opencode-deepseek/auth-server/pom.xml:70), [order-service/pom.xml](C:/projects/pub-rec-opencode-deepseek/order-service/pom.xml:117), and [inventory-service/pom.xml](C:/projects/pub-rec-opencode-deepseek/inventory-service/pom.xml:101). The explicit Testcontainers patch bump is present only where claimed, in [order-service/pom.xml](C:/projects/pub-rec-opencode-deepseek/order-service/pom.xml:100), [order-service/pom.xml](C:/projects/pub-rec-opencode-deepseek/order-service/pom.xml:108), and [inventory-service/pom.xml](C:/projects/pub-rec-opencode-deepseek/inventory-service/pom.xml:92), while the core artifact remains BOM-managed.

- **D-3 fixes the Angular lockstep problem instead of forcing around it.** Both UIs now move the full Angular peer set together, not just a subset: [order-ui/package.json](C:/projects/pub-rec-opencode-deepseek/order-ui/package.json:11), [order-ui/package.json](C:/projects/pub-rec-opencode-deepseek/order-ui/package.json:27), [inventory-ui/package.json](C:/projects/pub-rec-opencode-deepseek/inventory-ui/package.json:11), and [inventory-ui/package.json](C:/projects/pub-rec-opencode-deepseek/inventory-ui/package.json:27). The lockfiles reflect the same resolved family versions, including `@angular/core` `22.0.6`, `@angular/cdk` `22.0.4`, and `@angular/compiler-cli` `22.0.6`, at [order-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/order-ui/package-lock.json:472), [order-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/order-ui/package-lock.json:808), [inventory-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/inventory-ui/package-lock.json:472), and [inventory-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/inventory-ui/package-lock.json:808).

## Dependency / Security Assessment

- I re-checked production npm exposure with `npm audit --omit=dev` in both `order-ui` and `inventory-ui`; both returned `0 vulnerabilities`, matching the handoff claim.
- The workflow action bumps are current relative to the official upstream usage examples for [`actions/checkout@v7`](https://github.com/actions/checkout) and [`actions/upload-artifact@v7`](https://github.com/actions/upload-artifact).
- I did not find evidence of stale or partially applied dependency updates in the committed manifests or lockfiles.

## Residual Verification Gaps

- I did not re-run `mvnw clean verify` from this environment, so the Maven verification claim remains handoff-backed rather than independently reproduced here.
- I did not re-run the Angular builds locally. The committed manifests and lockfiles are internally consistent, but the local working copy's installed `node_modules` state is not authoritative evidence for this sprint.
- `actionlint` was not available here, so CI workflow correctness beyond static inspection still depends on the live GitHub Actions run.
