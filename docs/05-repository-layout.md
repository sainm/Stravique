# Repository Layout

```text
.
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- backend/
|   |-- build.gradle
|   |-- settings.gradle
|   |-- gradlew
|   |-- gradlew.bat
|   `-- src/
|       |-- main/
|       |   |-- java/dev/stravique/
|       |   |   |-- StraviqueApplication.java
|       |   |   |-- api/
|       |   |   |-- application/
|       |   |   |-- config/
|       |   |   |-- domain/
|       |   |   |-- infrastructure/
|       |   |   `-- inventory/
|       |   `-- resources/
|       |       |-- application.yml
|       |       |-- db/migration/
|       |       `-- i18n/
|       `-- test/
|           |-- java/dev/stravique/
|           `-- resources/
|-- frontend/
|   |-- package.json
|   |-- src/
|   |   |-- app/
|   |   |-- components/
|   |   |-- features/
|   |   |-- lib/
|   |   |-- locales/
|   |   `-- styles/
|   `-- tests/
|-- sample-projects/
|   `-- legacy-web-demo/
|-- docs/
|-- scripts/
|-- .env.example
|-- README.md
`-- verify.sh
```

## Boundaries

- `frontend` never imports source-file APIs from the operating system.
- `backend/inventory` contains scanner-only code and cannot write project files.
- `domain` owns persisted state and certainty language.
- `api` maps transport concerns but does not perform parsing.
- `docs` is the source of design intent; runtime results remain authoritative for observed scans.
- `sample-projects` is an inspection fixture and is not packaged as production data.
