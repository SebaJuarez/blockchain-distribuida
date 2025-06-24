# utils/check_gpu.py
import subprocess
import sys

def check_for_nvidia_smi():
    """
    Verifica si 'nvidia-smi' está disponible y CUDA parece estar funcionando.
    Retorna True si es así, False en caso contrario.
    """
    try:
        subprocess.check_call(['nvidia-smi'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print("NVIDIA GPU detected and nvidia-smi is available.", file=sys.stdout, flush=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("No NVIDIA GPU detected or nvidia-smi not found.", file=sys.stdout, flush=True)
        return False
    except Exception as e:
        print(f"An unexpected error occurred while checking GPU: {e}", file=sys.stderr, flush=True)
        return False