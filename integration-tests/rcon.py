"""Minimal Source RCON client used by the SkCine integration tests.

Usage: python rcon.py <host> <port> <password> <command> [<command> ...]
Prints each command's response. Exit code 1 on authentication failure.
"""

import socket
import struct
import sys


def _packet(req_id: int, kind: int, body: str) -> bytes:
    payload = struct.pack("<ii", req_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def _read(sock: socket.socket):
    raw_len = _recv_exact(sock, 4)
    (length,) = struct.unpack("<i", raw_len)
    data = _recv_exact(sock, length)
    req_id, kind = struct.unpack("<ii", data[:8])
    return req_id, kind, data[8:-2].decode("utf-8", errors="replace")


def _recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        buf += chunk
    return buf


def run(host: str, port: int, password: str, commands):
    with socket.create_connection((host, port), timeout=30) as sock:
        sock.sendall(_packet(1, 3, password))
        req_id, _, _ = _read(sock)
        if req_id == -1:
            print("RCON authentication failed", file=sys.stderr)
            return 1
        for i, cmd in enumerate(commands, start=2):
            sock.sendall(_packet(i, 2, cmd))
            _, _, body = _read(sock)
            print(body)
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 5:
        print(__doc__)
        sys.exit(2)
    sys.exit(run(sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4:]))
