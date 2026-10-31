# Contributing

## Before changing code

1. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
2. Read [docs/VALIDATION.md](docs/VALIDATION.md).
3. Keep the service restricted to the official YouTube package.
4. Preserve the no-advertiser-click invariant.

## Local validation

Use JDK 17 and Android SDK 35:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

## Pull requests

- Keep changes focused and explain the failure mode being fixed.
- Add or update unit tests for pure matching and policy code.
- Include device, Android and YouTube versions for runtime bugs.
- Include redacted DEBUG logs when the accessibility tree differs from assumptions.
- Never attach unredacted screen text, account identifiers, tokens, passwords or signing files.
- Do not merge until CI passes and the manual device test required by the change is recorded.
