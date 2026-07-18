# Demo Notes Sprint 38

- Strong reviewer-story sprint: Sprint 36 had substantial correct work, but the live provider still bypassed the custom provisioning hook because Google was OIDC, not plain OAuth2.
- Good talk angle: the bug was not "OAuth is hard" in the abstract. It was one wrong framework extension point, proven by the absence of the expected `PENDING` row and fixed by moving to `oidcUserService(...)`.
- This is a clean example of why realistic test doubles matter: generic `OAuth2User` mocks passed, but realistic OIDC-shaped test data was what finally covered the real failure mode.
