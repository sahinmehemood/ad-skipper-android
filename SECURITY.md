# Security policy

## Scope

This project uses an Android Accessibility Service to interact with the official YouTube app. Accessibility access is sensitive and powerful.

## Reporting

Please do not publish exploitable security issues, sensitive logs or private user data in public issues. Use GitHub's private vulnerability reporting feature for this repository when available, or contact the repository owner privately through GitHub.

## Security expectations

- Keep event and window reads restricted to `com.google.android.youtube`.
- Do not add network permission, telemetry, account access, credential collection or unrelated automation without an explicit design review.
- Keep every gesture and click tied to a positively identified YouTube control.
- Keep tree traversals, retry loops and log output bounded.
- Do not commit keystores, signing credentials, API keys, local.properties or build artifacts.
