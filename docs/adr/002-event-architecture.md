# ADR 002: Saga coreografiada para la acreditación de intereses

## Estado

Aceptado.

## Contexto

La acreditación anual de intereses era una llamada HTTP síncrona: `interests-service` calculaba el monto y `core-service` acreditaba saldo, transacción `CREDIT` y `AnnualInterestSummary` en una sola transacción de PostgreSQL (`POST /internal/accounts/{accountId}/interest-credits`, scope `interests:write`).

Ese comando acopla la disponibilidad de los dos procesos. El GET de resumen (`bff-web` → `interests-service` → `core-service`) no tiene ese problema y se queda síncrono. El estado de la cuenta sigue siendo la fila en PostgreSQL. Los eventos solo tienen que coordinar el crédito.

`interests-service` no tiene base de datos. `core-service` sí, y ya persiste el crédito de forma transaccional.

## Decisión

El crédito de intereses, cuando `FEATURE_INTEREST_CREDIT_VIA_KAFKA` está en `true`, es una saga coreografiada sobre un broker Apache Kafka en modo KRaft (un nodo, sin ZooKeeper).

No hay orquestador. Cada servicio reacciona al evento que le corresponde:

1. `interests-service` publica `InterestCalculated` en `interests.calculated` (clave de partición `accountId`) y guarda el cálculo en memoria como `PENDING`.
2. `core-service` consume ese evento, acredita con el mismo `CreditInterestUseCase` y, en la misma transacción, inserta el resultado en `outbox_events`.
3. Un relay de `core-service` publica `InterestCreditApplied` o `InterestCreditRejected` en `interests.credit-results` (misma clave) y marca la fila como publicada solo después del ack.
4. `interests-service` consume el resultado y cierra el cálculo como `APPLIED` o `REJECTED`. Un segundo resultado del mismo `eventId` no cambia un cálculo ya cerrado.

La entrega es at-least-once. El `eventId` determinista `interest:{accountId}:{year}` hace idempotente al consumidor. La clave HTTP del camino síncrono sigue siendo `interest-{accountId}-{year}`.

Con la flag en `false` (default, también en Compose) el POST HTTP actual no cambia. El endpoint `POST /internal/accounts/{accountId}/interest-credits` se mantiene. En Compose el listener de `core-service` arranca encendido para que la saga esté lista cuando alguien active la flag.

## Por qué outbox y no publicar dentro del caso de uso

El crédito y el evento de resultado tienen que confirmarse juntos. Si la transacción del crédito falla, no queda fila en `outbox_events` y el relay no anuncia un crédito que no ocurrió. Si el relay no puede hablar con Kafka, la fila sigue sin publicar y el siguiente ciclo reintenta. El crédito no se deshace.

Un rechazo de negocio (`VALIDATION` o `NOT_FOUND`) no acredita y escribe solo `InterestCreditRejected` con `reason`. Un fallo técnico no confirma el offset: Kafka reentrega.

## Por qué no Event Sourcing

Event Sourcing reconstruye el agregado releyendo su historia. Aquí la cuenta no se reconstruye desde eventos. El saldo, la transacción y el resumen anual siguen siendo filas de PostgreSQL, escritas por el caso de uso que ya existía. `InterestCalculated` y el resultado solo coordinan a dos servicios. Guardar esa historia como fuente de verdad duplicaría el estado de la cuenta y obligaría a un replay que el dominio no necesita.

## Por qué no hay outbox en interests-service

No hay transacción local donde encajar la publicación: el servicio no tiene datasource, JPA ni Flyway. El `eventId` determinista cubre una re-ejecución del cálculo. El estado `PENDING` / `APPLIED` / `REJECTED` vive en un repositorio en memoria del proceso.

Si el proceso muere entre publicar y consumir el resultado, ese estado se pierde. El crédito en `core-service` sigue siendo idempotente, así que un reintento no acredita dos veces. Persistirlo exigiría una base nueva. Este cambio no la agrega.

## Consecuencias

- El GET de resumen y la lectura de saldo siguen en HTTP. Con la flag encendida, el circuit breaker de `creditInterest` no interviene. El de `fetchInterestSummary` y `fetchAccountBalance` sí.
- El listener de `core-service` no pasa por `EnforcementFilter`. El POST HTTP sigue exigiendo `interests:write`.
- En desarrollo no hay ACLs de Kafka. Quien alcance el broker puede producir en `interests.calculated`. Pendiente de producción: solo `interests-service` produce en `interests.calculated` y solo `core-service` produce en `interests.credit-results`.
- La durabilidad del cierre del cálculo en `interests-service` queda pendiente junto con esas ACLs. No se agrega base ni outbox en ese servicio en este cambio.
