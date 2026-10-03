# ADR 003: Saga de Kafka por defecto, particiones y dead-letter topics

## Estado

Aceptado. Complementa el [ADR 002](002-event-architecture.md).

## Contexto

La saga de intereses y `transactions.confirmed` estaban implementadas, pero el Compose arrancaba con `FEATURE_INTEREST_CREDIT_VIA_KAFKA=false`, así que el camino que nadie ejecutaba por defecto era el asíncrono. Activarlo tal cual dejaba tres huecos: un mensaje que nunca se puede procesar bloqueaba su partición (el listener de `core-service` reintentaba cada segundo sin límite; el de `interests-service` descartaba el mensaje tras unos reintentos sin dejar rastro), todos los tópicos tenían una sola partición y un solo hilo consumidor, y el relay del outbox no garantizaba el orden por cuenta.

## Decisión

- **Por defecto en Compose.** `FEATURE_INTEREST_CREDIT_VIA_KAFKA` vale `true` en `docker-compose.yml`. El camino HTTP sigue disponible con `false`, y el valor por defecto de cada servicio fuera de Compose sigue siendo `false`. El cambio de default se hizo después de verificar la saga de punta a punta contra el stack completo (`scripts/verify-interest-saga.sh`).
- **Tres particiones por tópico** (`interests.calculated`, `interests.credit-results`, `transactions.confirmed` y sus `.DLT`), clave `accountId`. `kafka-init` crea los tópicos y sube a 3 los que ya existían con menos (`--alter` solo puede aumentar). Tres hilos consumidores por listener, uno por partición.
- **Reintentos acotados y dead-letter topic por tópico.** Cada consumidor (`core-service` en `interests.calculated`, `interests-service` en `interests.credit-results`) reintenta 3 veces con 1 s, 2 s y 4 s y publica el registro en `<tópico>.DLT` con clave, payload y cabeceras del fallo; después confirma el offset y sigue. La partición del DLT se elige por la clave. Todos los fallos se reintentan, incluido un payload ilegible: el costo es de unos 7 s, y clasificarlo como no reintentable sería un cambio de una línea.
- **Reintentos bloqueantes, no tópicos de reintento.** Un tópico de reintento dejaría que un mensaje posterior de la misma cuenta adelante al que falla, que es justo el orden que las particiones por cuenta protegen.
- **El outbox se publica en orden de escritura.** `outbox_events.seq` (Flyway `V13`) reemplaza el orden por fecha y UUID. Si falla el envío de un evento, los siguientes de esa cuenta esperan a la siguiente ejecución; las demás cuentas siguen.
- **Un evento ya procesado se confirma, no se revalida.** Aplicar interés de nuevo para el mismo año recalcula el monto con el saldo nuevo; el evento llega con el mismo `eventId` y otro monto. Se reconoce sin crear un segundo resultado (el `event_id` del outbox es único).

## Consecuencias

- Un `200` en `POST /accounts/{id}/interest-applications` ya no significa que la cuenta esté acreditada con la saga encendida. El estado `PENDING` / `APPLIED` / `REJECTED` sigue en memoria de `interests-service` y no hay endpoint para consultarlo (ADR 002).
- Un `InterestCalculated` que cae en el DLT deja su cálculo en `PENDING` y la cuenta sin acreditar. Los DLT se inspeccionan con las herramientas de Kafka y el reproceso es manual.
- `transactions.confirmed.DLT` existe pero hoy nadie consume `transactions.confirmed`.
- Subir las particiones de un tópico que aún tiene registros sin consumir cambia la partición a la que cae cada clave: hay que drenarlo antes. El broker local es desechable y `kafka-init` corre antes que cualquier productor.
- Si el propio envío al DLT falla, el registro se vuelve a entregar y la partición sigue bloqueada: es preferible a perderlo en silencio.
- La configuración del manejo de errores está duplicada (unas 25 líneas) en `core-service` e `interests-service`; no hay un módulo compartido de Kafka.
- La verificación de punta a punta es un script de shell, no una prueba de CI.
