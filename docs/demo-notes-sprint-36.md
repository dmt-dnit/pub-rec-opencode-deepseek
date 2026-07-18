# Demo Notes Sprint 36

- Good demo-story sprint: what looked like "Google login exists" was actually missing the entire backend-to-frontend bridge needed to turn a third-party login into a local session.
- Useful talk angle: the hard part was not the button. It was stitching OAuth identity, local user provisioning, admin approval, JWT issuance, and SPA callback handling into one coherent flow.
- Good follow-up pairing: Sprint 36 is strongest when immediately followed by Sprint 38, because it shows how a feature can still fail live even after a substantial amount of seemingly-correct integration work.
