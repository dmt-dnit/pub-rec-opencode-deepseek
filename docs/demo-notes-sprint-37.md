# Demo Notes Sprint 37

- Short but valuable infrastructure sprint: one wrong scheme in a generated `redirect_uri` breaks the entire OAuth handshake.
- Good talk angle: this is a classic proxy-trust bug. The reverse proxy was already correct; the application simply was not honoring forwarded headers.
- Use briefly in a presentation unless you want to emphasize how deployment topology bugs can survive clean local builds.
