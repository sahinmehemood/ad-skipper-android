# APK signing and updates

## Current CI artifact

The workflow uploads a **debug APK**, not a production release. Android Gradle Plugin signs debug builds with the runner's debug keystore. GitHub-hosted runners are ephemeral, so the debug signing certificate can change between workflow runs.

Android requires an update APK to be signed by the same certificate as the installed package. If the certificate changes, installation over the earlier CI APK fails with a signature mismatch. For this repository's current CI artifact, uninstall the old Ad Skipper package, install the new APK, and enable the Accessibility service again.

This was verified by comparing the SHA-256 fingerprints of the signing certificates in the 1.3.1 and 1.3.2 CI APKs: they differ.

## Production / repeatable signing

A stable release needs a keystore owned and controlled by the repository owner. Do not commit a keystore, signing password, encoded keystore, or private signing key to Git.

Before publishing upgradeable release APKs:

1. Generate and securely back up a release keystore outside the repository.
2. Store the keystore and its passwords in private GitHub Actions secrets.
3. Configure a release-only Gradle signing configuration that reads those secrets from the CI environment.
4. Build and verify the signed release APK, then keep the keystore backed up securely. Losing the keystore prevents future in-place updates signed with that certificate.

No production release-signing workflow is configured yet. The current GitHub artifact is intentionally a debug/test build. Never reuse a publicly distributed debug key as the production signing key.
