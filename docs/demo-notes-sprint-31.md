# Demo Notes Sprint 31

- Good ops-story sprint: this came from a real VPS resource incident, not a hypothetical optimization pass.
- Useful talking point: the fix was intentionally artifact-only first, with live apply kept separate from authoring and review.
- Demo proof to capture later: post-apply `ps`/`systemd`/container memory evidence showing capped heaps and restored headroom on the shared VPS.
