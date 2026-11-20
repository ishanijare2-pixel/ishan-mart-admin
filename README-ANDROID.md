# Ishan Mart Admin Android App

Native Kotlin + Jetpack Compose app in android/. The existing Admin website at the repository root remains separate and unchanged.

Features: Firebase Admin login, live dashboard, product search/add/edit/delete, order search/status changes, stock adjustment on acceptance/cancellation, cancellation request review, and customer summaries.

Build: GitHub Actions → Build Ishan Mart Admin Android App → Run workflow. Download the Ishan-Mart-Admin-APK artifact after success.

Never add service-account credentials or private keys to this repository. Firestore Security Rules must enforce Admin permissions; the in-app UID check is only an additional UI check.
