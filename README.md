# ASTER BOOST

Android route-quality tester with optional WireGuard tunnel support.

This repository is built automatically with GitHub Actions. The APK artifact is named `ASTER-BOOST-APK`.

Important: the app cannot create a remote VPN exit server by itself. Without a WireGuard node it works in DIRECT measurement mode; with a private WireGuard `.conf` it compares DIRECT vs VPN and keeps the cleaner route.
