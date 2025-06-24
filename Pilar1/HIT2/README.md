# Hit #2 - Hola mundo en CUDA

Visite la presentación original de CUDA, o su versión actualizada y realice un programa básico en CUDA de hola mundo como los del ejemplo que se menciona. Comience a elaborar un informe de las tareas realizadas, describiendo:
Qué entorno está utilizando,
Si se encontró con problemas como los solucionó
Cuál es su setup y
Si usa hardware nativo, las características del mismo.

Los ejemplos a continuación y esta guía fueron probadas con una GPU GT 750M y CUDA 10.1, con data de 2013.
Nótese que entre versiones de CUDA puede tener que realizar cambios en el código y no todos los ejemplos de CUDA 10.1 son compatibles con las versiones más nuevas. Documente las adaptaciones que realice si corresponde.
Consideración: Corto y conciso.

# Informe de Tareas Realizadas

## Entorno utilizado

- Sistema Operativo del Host: Windows 11
- GPU Utilizada: Nvidia GTX 1660 Super
- Controladores de Nvidia: 576.57 
- Version de CUDA: 12.9
- Compilador de CUDA: nvcc


## Problemas encontrados y soluciones

|  #  | Problema                                                          | Solución                                                                                                                                  |
| :-: | :---------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------- |
|  1  |	nvcc fatal : Cannot find compiler 'cl.exe' in PATH — nvcc no encontraba el compilador de MSVC porque no estaba en la variable PATH. |Instalar las Build Tools de Visual Studio con el workload “Desarrollo de escritorio con C++” y lanzar VS Code desde el x64 Native Tools Command Prompt. |

## Comando de ejecución

```bash
nvcc hola-mundo.cu -o hola-mundo.exe
hola-mundo.exe
```