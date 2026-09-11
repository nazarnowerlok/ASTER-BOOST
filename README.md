# ASTER BOOST

ASTER BOOST is an Android network-route tester for gaming.

What it does:
- measures DIRECT internet latency, P95 tail latency, jitter and connection-failure loss;
- can import a private WireGuard `.conf`;
- tests several safe MTU values automatically;
- keeps the VPN only when its measured route is better than DIRECT;
- leaves DIRECT active if the tunnel is slower or unstable.

Daily use after a private node is loaded:
**open ASTER BOOST → SMART BOOST → play**.

Important: the app cannot create a remote VPN server out of nothing and it cannot guarantee 10–20 ms. A real remote WireGuard node is required for VPN routing. The included `server/install_aster_node.sh` can configure a small Ubuntu/Debian VPS you control. The generated `aster-client.conf` contains a private key and must not be shared publicly.

Public probe latency is only a route-quality signal; it is not the same as PUBG server ping or hit registration.
