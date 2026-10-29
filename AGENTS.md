# Project instructions

- Repository name: mj_proto_assist. Keep this spelling.
- Current stage: initial implementation. Claim only features backed by code and verification; keep planned MVP work clearly marked.
- Default server stack: Spring Boot + Kotlin. Use another stack only with a concrete technical reason and explain it.
- Keep Kotlin Core independent of UI, HTTP, mj_fav business rules, and ApiLens internals.
- ApiLens is Flutter/Dart. Prefer the documented stdio adapter; do not assume TypeScript libraries can be imported directly.
- Keep target HTTP POST/Protobuf traffic separate from internal JSON control protocols.
- Never commit real credentials, API bodies, bookmarks, private schemas, or copied application code.
- Update PRD acceptance criteria, design, and integration docs together when scope changes.
