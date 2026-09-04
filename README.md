# VUEO-NEXT

VUEO monorepo for Android Mobile + Android TV with a shared Kotlin core.

## Start here

Before making changes, especially with an AI coding agent, read:

- [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) - canonical architecture, product direction, current milestone and handoff rules
- [`AI_HANDOFF.md`](AI_HANDOFF.md) - short mandatory checklist for AI agents

## Modules

```text
:mobile       Android Mobile, com.vueo.app
:tv           Android TV, com.vueo.tv
:shared:core  Shared domain/runtime/storage/integration logic
```

Dependency direction:

```text
        shared/core
         /       \
    mobile       tv
```

## Build

```bash
./gradlew :mobile:assembleDebug :tv:assembleDebug
```

When Shared Core changes, validate both application modules.
