# Blockchain Distribuida con Minería Paralela

Este proyecto implementa una blockchain distribuida desde cero, con minería basada en prueba de trabajo (PoW) y capacidad de cómputo paralela usando CPU o GPU (CUDA). Utiliza una arquitectura asíncrona basada en eventos, soportada por Redis, RabbitMQ y desplegada sobre Google Cloud Platform con Kubernetes y Terraform.

## 📋 Tabla de Contenidos

1. [Ejecución local con Docker Compose](#ejecución-local-con-docker-compose)
2. [Arquitectura del Sistema](#arquitectura-del-sistema)
3. [Tecnologías utilizadas](#tecnologías-utilizadas)
4. [Demostración del Funcionamiento (Frontend)](#demostración-del-funcionamiento-frontend)
   - [Dashboard](#dashboard)
   - [Explorador de Bloques](#explorador-de-bloques)
   - [Transacciones](#transacciones)
   - [Estadísticas de la Red](#estadísticas-de-la-red)
5. [Comparativa de Rendimiento: CPU vs GPU](#comparativa-de-rendimiento-cpu-vs-gpu)
6. [Documento Técnico](#documento-técnico)

---

## Ejecución local con Docker Compose

El stack completo corre en local con Docker: el coordinador, el mining pool, un minero individual, dos mineros del pool, el frontend y la observabilidad. Todos los servicios se compilan desde el código fuente (no hace falta Maven, Node ni Python instalados).

### Requisitos

- Docker Engine 24+ con Docker Compose v2 (Docker Desktop en Windows/Mac)

### Levantar el stack

```bash
docker compose up --build -d
```

El primer build tarda unos minutos (compila los servicios Java con Maven dentro de Docker). Para ver el progreso:

```bash
docker compose logs -f coordinator-node
```

### Servicios y URLs

| Servicio | URL |
|---|---|
| Frontend (Blockchain Explorer) | http://localhost:3001 |
| Coordinador (API) | http://localhost:8080/api |
| Mining Pool (API) | http://localhost:8081/api/pools |
| RabbitMQ Management | http://localhost:15672 (`guest` / `guest`) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (`admin` / `admin`) |

### Poner a minar la red

1. Abrí http://localhost:3001.
2. En la pestaña **Transactions**, creá una wallet o usá la generada.
3. Cargá fondos con **Load funds** (faucet del modo testing).
4. Creá una transacción con **New transaction**.

El coordinador publica tareas de minería cada 10 segundos; el **minero individual** y los **2 mineros del pool** compiten y los bloques empiezan a aparecer en el Dashboard y en el Explorador de Bloques.

### Verificación rápida

- Mineros registrados en el pool: `curl http://localhost:8081/api/pools/miners`
- Bloques minados: `curl http://localhost:8080/api/blocks?page=0&size=5`
- Logs de los mineros: `docker compose logs -f minero-individual minero-pool-1 minero-pool-2`

### Resetear el entorno

```bash
docker compose down -v
```

Esto elimina los contenedores, la red y los volúmenes (Redis, identidad del pool, etc.), dejando el entorno en cero.

---

## Arquitectura del Sistema

La blockchain opera sobre una red asíncrona donde el **Coordinador** gestiona la creación de bloques y la distribución de tareas. Los **Mining Pools** organizan grupos de mineros, asignando subtareas. Los **Mineros** (individuales o de pool) ejecutan la Prueba de Trabajo, con la opción de aprovechar la **GPU (CUDA)** para un mayor rendimiento. Todo el estado crítico se persiste en Redis.

![Diagrama de Arquitectura](https://github.com/user-attachments/assets/2277982a-0912-4de3-91aa-60e39261fe1c)

---

## Tecnologías utilizadas

| Área              | Tecnología                                     |
|-------------------|------------------------------------------------|
| Lenguajes         | Java, Python                                   |
| Mensajería        | RabbitMQ                                       |
| Almacenamiento    | Redis                                          |
| Cómputo paralelo  | CUDA                                           |
| Infraestructura   | GCP, Kubernetes (GKE), Terraform               |
| CI/CD             | GitHub Actions                                 |

---

## Demostración del Funcionamiento (Frontend)

Para visualizar el funcionamiento de la blockchain, se desarrolló una interfaz sencilla que permite monitorear el estado de la red, los bloques minados y las transacciones.

### Dashboard

Una vista general del estado actual de la blockchain.
![Dashboard de la Blockchain](https://github.com/user-attachments/assets/52fc41ae-38a5-42ac-a3f6-d8439429dca2)

### Explorador de Bloques

Detalle de los bloques minados, incluyendo su hash, transacciones y timestamp.
![Explorador de Bloques](https://github.com/user-attachments/assets/9d0529df-8ab6-4098-a540-e014b1f884d7)
![Detalle de un Bloque](https://github.com/user-attachments/assets/fe65cbbd-f359-491f-a77d-2e37b793a82b)

### Transacciones

Visualización de transacciones pendientes y la capacidad de generar nuevas transacciones o ajustar la dificultad de minería.
![Transacciones Pendientes](https://github.com/user-attachments/assets/9f23de6a-ba96-45c6-8bbc-fe2b305db384)
![Generación de Transacciones y Ajuste de Dificultad](https://github.com/user-attachments/assets/3883cd4f-88da-492e-a3ab-42b409dbb3b8)

### Estadísticas de la Red

Gráficos y métricas clave sobre el rendimiento de la blockchain.
![Estadísticas Generales](https://github.com/user-attachments/assets/f0041c63-fbe4-411b-8ee7-4b152f0e90d9)
![Gráficos de Estadísticas](https://github.com/user-attachments/assets/9532fa1e-7053-4622-b9a4-66e190f576a4)

---

## Comparativa de Rendimiento: CPU vs GPU

El rendimiento del sistema se evaluó mediante pruebas de minería distribuidas bajo diferentes cargas y niveles de dificultad, utilizando un pool de 5 instancias de CPU en la nube y un minero individual con GPU (GTX 1660 Super, CUDA).

A continuación se presenta un resumen visual comparando ambas arquitecturas según dos métricas clave:

- ⏱️ **Tiempo promedio de minado (segundos)**
- ⛏️ **Cantidad de bloques minados**

![Comparativo CPU vs GPU](https://github.com/user-attachments/assets/23a0e3cd-ae03-4915-a5ab-c50ea70a6472)

**Conclusiones clave:**

- La **GPU supera consistentemente al pool de CPU**, especialmente a dificultad 6 y 7.
- En cargas altas (10.000 transacciones), la GPU logra minar bloques en la mitad del tiempo.
- A dificultad 7, las CPUs fallan o presentan tiempos no sostenibles, mientras que la GPU mantiene una respuesta estable.
- El sistema distribuye adecuadamente la carga hasta cierto umbral, donde la potencia de cómputo se vuelve el cuello de botella.

---

## Documento Técnico

Para una exploración exhaustiva de la implementación, el diseño de cada componente y los detalles de las decisiones técnicas detrás de este proyecto, consulta el documento técnico completo.

[Enlace al Documento Técnico Completo](https://drive.google.com/file/d/1YgQ6T4gPgXAMjoayqINlMAoKpbfHYNJ3/view?usp=drive_link)
