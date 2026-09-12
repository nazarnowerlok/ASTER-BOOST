# ASTER BOOST 2.0

This is a clean rebuild focused on proving that the VPN itself works before doing any gaming-route optimization.

## What 2.0 does
- imports a standard WireGuard `.conf` and parses it with the official WireGuard Android tunnel library;
- asks Android for VPN permission correctly;
- starts/stops a real WireGuard userspace tunnel;
- generates real internet traffic through the tunnel;
- reads WireGuard RX/TX statistics and the latest handshake timestamp;
- checks the public IP before and after connection;
- shows `VPN: VERIFIED` only when the tunnel is genuinely verified, not just because Android shows a VPN icon.

The old SMART BOOST/MTU logic is intentionally not part of the main flow yet. First the tunnel must be proven on the user's phone. Route optimization can be added only after this core is known-good.

## Free config option
The app includes a button that opens Proton VPN's official WireGuard configuration instructions. Proton's Free plan currently supports generating standard WireGuard configuration files. The user owns that account/config; ASTER BOOST does not bundle shared private VPN keys.

## Important
A VPN client still needs a real remote WireGuard server. An APK cannot create an internet exit server out of nothing. A working `.conf` can come from a VPN provider that supports standard WireGuard configs or from a VPS you control.

A VPN can improve a bad route, jitter, or loss, but it cannot guarantee a specific PUBG ping or manipulate hit registration.
