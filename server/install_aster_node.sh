#!/usr/bin/env bash
set -euo pipefail

WG_IF="wg0"
WG_PORT="${WG_PORT:-51820}"
SERVER_ADDR="10.77.0.1/24"
CLIENT_ADDR="10.77.0.2/32"
CLIENT_DNS="${CLIENT_DNS:-1.1.1.1}"
MTU="${MTU:-1380}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0"
  exit 1
fi

if ! command -v apt-get >/dev/null 2>&1; then
  echo "Ubuntu/Debian required"
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y wireguard qrencode curl iptables

SERVER_NIC="$(ip route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="dev"){print $(i+1); exit}}')"
PUBLIC_IP="$(curl -4fsS --max-time 10 https://api.ipify.org || true)"

if [[ -z "${SERVER_NIC}" || -z "${PUBLIC_IP}" ]]; then
  echo "Could not detect public interface/IP"
  exit 1
fi

install -m 700 -d /etc/wireguard
umask 077

SERVER_PRIV="$(wg genkey)"
SERVER_PUB="$(printf '%s' "${SERVER_PRIV}" | wg pubkey)"
CLIENT_PRIV="$(wg genkey)"
CLIENT_PUB="$(printf '%s' "${CLIENT_PRIV}" | wg pubkey)"

cat >/etc/wireguard/${WG_IF}.conf <<EOF
[Interface]
Address = ${SERVER_ADDR}
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_PRIV}
PostUp = iptables -A FORWARD -i ${WG_IF} -j ACCEPT; iptables -A FORWARD -o ${WG_IF} -j ACCEPT; iptables -t nat -A POSTROUTING -o ${SERVER_NIC} -j MASQUERADE
PostDown = iptables -D FORWARD -i ${WG_IF} -j ACCEPT; iptables -D FORWARD -o ${WG_IF} -j ACCEPT; iptables -t nat -D POSTROUTING -o ${SERVER_NIC} -j MASQUERADE

[Peer]
PublicKey = ${CLIENT_PUB}
AllowedIPs = ${CLIENT_ADDR}
EOF

cat >/etc/sysctl.d/99-aster-wireguard.conf <<EOF
net.ipv4.ip_forward=1
EOF
sysctl --system >/dev/null

systemctl enable wg-quick@${WG_IF}
systemctl restart wg-quick@${WG_IF}

cat >/root/aster-client.conf <<EOF
[Interface]
PrivateKey = ${CLIENT_PRIV}
Address = 10.77.0.2/32
DNS = ${CLIENT_DNS}
MTU = ${MTU}

[Peer]
PublicKey = ${SERVER_PUB}
Endpoint = ${PUBLIC_IP}:${WG_PORT}
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
EOF

chmod 600 /root/aster-client.conf

echo
printf 'ASTER NODE READY\nPublic IP: %s\nUDP port: %s\nClient config: /root/aster-client.conf\n\n' "${PUBLIC_IP}" "${WG_PORT}"
echo "Open UDP ${WG_PORT} in the cloud firewall/security group if needed."
echo "Do not share aster-client.conf: it contains your private client key."
echo
qrencode -t ansiutf8 </root/aster-client.conf || true
