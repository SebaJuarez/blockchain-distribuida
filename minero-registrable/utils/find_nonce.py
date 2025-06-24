# utils/find_nonce.py
import hashlib
from typing import Optional

def find_nonce(prefix: str, content_hash: str, n: int) -> Optional[tuple[int, str]]:
    data = f"{n}{content_hash}".encode()
    digest = hashlib.md5(data).hexdigest()
    if digest.startswith(prefix):
        return n, digest
    return None