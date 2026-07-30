import os
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization

KEY_FILE = os.environ.get("MINER_KEY_FILE", ".miner_key.pem")


def load_or_create_keypair():
    """
    Carga el par de claves EC (secp256k1) desde KEY_FILE, o genera uno nuevo
    y lo persiste si no existe. Retorna (private_key, public_key_hex_comprimida).
    """
    if os.path.exists(KEY_FILE):
        with open(KEY_FILE, "rb") as f:
            private_key = serialization.load_pem_private_key(f.read(), password=None)
    else:
        private_key = ec.generate_private_key(ec.SECP256K1())
        with open(KEY_FILE, "wb") as f:
            f.write(private_key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.NoEncryption(),
            ))

    public_key_hex = public_key_to_compressed_hex(private_key.public_key())
    return private_key, public_key_hex


def public_key_to_compressed_hex(public_key) -> str:
    encoded = public_key.public_bytes(
        encoding=serialization.Encoding.X962,
        format=serialization.PublicFormat.CompressedPoint,
    )
    return encoded.hex()