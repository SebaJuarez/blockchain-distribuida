# model/block.py
import json
import hashlib
import time

class Block:
    def __init__(self, data, timestamp, current_hash, previous_hash, nonce, index):
        self.data = data
        self.timestamp = timestamp
        self.hash = current_hash
        self.previous_hash = previous_hash
        self.nonce = nonce
        self.index = index

    def get_block_content_hash(self):
        """
        Calcula el hash MD5 del contenido del bloque (index + timestamp + data_as_string + previous_hash).
        Corresponde a `md5(index+timestamp+data+previous_hash)`
        """
        data_as_string = json.dumps(self.data, separators=(',', ':'), sort_keys=False)
        content_input = f"{self.index}{self.timestamp}{data_as_string}{self.previous_hash}"
        return hashlib.md5(content_input.encode('utf-8')).hexdigest()

    def calculate_final_block_hash(self):
        """
        Calcula el hash MD5 final del bloque, incluyendo el nonce.
        Corresponde a `md5(nonce + md5(index+timestamp+data+previous_hash))`
        """
        block_content_hash = self.get_block_content_hash()
        final_hash_input = f"{self.nonce}{block_content_hash}"
        return hashlib.md5(final_hash_input.encode('utf-8')).hexdigest()

    def to_dict(self):
        """
        Convierte el objeto Block a un diccionario, listo para ser serializado a JSON.
        Necesario para enviar al Coordinador.
        """
        return {
            "index": self.index,
            "timestamp": self.timestamp,
            "hash": self.hash,
            "previous_hash": self.previous_hash,
            "nonce": self.nonce,
            "data": self.data
        }

    # Método para crear un objeto Block desde el formato de tarea recibido
    @classmethod
    def from_task_payload(cls, task_block_data):
        return cls(
            data=task_block_data["data"],
            timestamp=task_block_data["timestamp"],
            current_hash= task_block_data["hash"],
            previous_hash=task_block_data["previous_hash"],
            nonce=0,
            index=task_block_data["index"]
        )