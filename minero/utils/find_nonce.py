# utils/find_nonce.py
import hashlib

def find_nonce_with_prefix(challenge_prefix, block_content_hash, start_nonce, end_nonce):
    """
    Intenta encontrar un nonce que, cuando se combina con el hash del contenido del bloque,
    produzca un hash que comience con el prefijo de dificultad dado.
    """
    for nonce in range(start_nonce, end_nonce + 1):
        test_hash_input = f"{nonce}{block_content_hash}"
        test_block_hash = hashlib.md5(test_hash_input.encode('utf-8')).hexdigest()

        if test_block_hash.startswith(challenge_prefix):
            return nonce, test_block_hash
    return 0, ""