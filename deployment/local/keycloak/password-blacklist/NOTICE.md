# Password blacklist provenance

`10k-most-common.txt` is the unmodified `Passwords/Common-Credentials/10k-most-common.txt`
file from the MIT-licensed [SecLists](https://github.com/danielmiessler/SecLists) `2026.1` release.

Pinned SHA-256: `4adb3f0afb4a10cf19ebe48d8c69a46f934bbc8d77c694c210564f9583e7f4ba`

The local realm import applies Keycloak's `passwordBlacklist(10000)` policy. Before changing
the supplied list or realm policy, update this checksum and repeat the Keycloak password-policy
verification in the Gate 2 runbook.
