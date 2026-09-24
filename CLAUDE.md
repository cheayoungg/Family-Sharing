# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew bootRun                              # run the app (defaults to the local profile)
./gradlew build                                # compile + test + package
./gradlew test                                 # all tests
./gradlew test --tests 'HomeProjectApplicationTests'          # one test class
./gradlew test --tests 'HomeProjectApplicationTests.contextLoads'  # one test method
```

Gradle 9.7.1 via the wrapper; Java toolchain 17; Spring Boot 3.5.9. No linter or formatter is configured.

## Architecture

Base package: `org.miniproject.homeproject`, split into two top-level areas:

- **`domain/`** — one subpackage per bounded context, each meant to hold its own entity/repository/service/controller: `schedule`, `expense`, `task`, `note`, `user`. Keep cross-domain dependencies one-directional (e.g. `expense`/`task`/`note`/`schedule` referencing `user`, not the reverse) since `user` is the shared identity owner for the others.
- **`global/`** — cross-cutting concerns shared by all domains: `config` (bean/config classes, e.g. `SecurityFilterChain`, `PasswordEncoder`), `security` (JWT filter/provider, `UserDetailsService`), `exception` (global `@ControllerAdvice` and error types).

Persistence is Spring Data JPA over PostgreSQL. Auth is stateless JWT (`io.jsonwebtoken:jjwt`) rather than session-based; expect a `JwtAuthenticationFilter` in `global/security` sitting in front of Spring Security's filter chain, with `SecurityFilterChain` configured for stateless sessions.

### Configuration profiles

`application.yml` only selects the active profile (`SPRING_PROFILES_ACTIVE`, default `local`); all environment-specific values live in the per-profile files and are read from environment variables — no DB credentials are ever committed:

- `application-local.yml` — for local development against a local PostgreSQL instance. **Gitignored** (each developer keeps their own). Env vars fall back to local defaults (`localhost:5432/homeproject`, `admin`/`1234`) so `bootRun` works out of the box if a local Postgres is running with that database/user. Local env vars are prefixed `HOMEPROJECT_` (`HOMEPROJECT_DB_HOST`, `HOMEPROJECT_DB_PORT`, `HOMEPROJECT_DB_NAME`, `HOMEPROJECT_DB_USERNAME`, `HOMEPROJECT_DB_PASSWORD`, `HOMEPROJECT_JWT_SECRET`) so generic `DB_*`/`JWT_SECRET` values exported for other projects don't silently redirect the app to another database.
- `application-dev.yml` — deployed dev environment; `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` must be supplied, no defaults. `ddl-auto: update`.
- `application-prod.yml` — same required env vars, but `ddl-auto: validate` (schema must be managed explicitly, e.g. via migrations, not auto-updated) and `show-sql: false`.

When adding a new domain's persistence config or a new required setting, add it to `-dev.yml`/`-prod.yml` (no default) and to `-local.yml` (with a safe local default, using the `HOMEPROJECT_`-prefixed env var name).

## Commit convention

Prefix commit subjects with the change type:

- `feat: ...` — new functionality
- `fix: ...` — bug fix
- `refactor: ...` — code change that doesn't alter external behavior