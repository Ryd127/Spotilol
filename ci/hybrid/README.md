# Spotilol 1.2.0 Vivo Hybrid

Test integration only. Never merge into stable main without validation.

Sources: exact upstream lyssadev/Spotilol commit a62e55dee71ecf558136d52958d89fac5ed6599b.
The CI runner overlays the proven island11 artwork provider and decompresses
the Brotli patch in this folder. It validates source hashes before applying.

Hybrid changes: single play/pause route, coalesced status/notification updates,
race-protected artwork, bounded metadata bitmaps, Spotify HTTPS artwork URI,
reacquired bounded wake locks and Spotify package name for Vivo Origin Island.

Code compile/green CI does not certify Vivo runtime smoothness or stability.
Upstream 1.2.0 WebView lifecycle is not yet fully reconciled with our 1.1.9.
