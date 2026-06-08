# Architecture Contract — BooklyBarbershopBot

Архитектурный якорь проекта. Любой PR, который меняет flow записи, callback-протокол или слои, **обязан** соответствовать этому документу.

**Статус внедрения**

| Область | Статус |
|---------|--------|
| Инварианты данных (PENDING → CONFIRMED, идемпотентность SMS) | ✅ PR-1 |
| `BookingStateMachine` + Commands | 🔜 PR-2+ |
| `CallbackTransport` v1 + `BookingSessionStore` port | 🔜 PR-4+ |
| Thin Telegram layer | 🔜 PR-6+ |

---

## Якорная фраза (review rule)

> **FSM решает «куда можно перейти»; Policy — «можно ли по правилам домена»; Ports — «что сказал мир»; Use case — «вызвать по порядку»; Mapper — «переименовать»; Handler — «показать».**

---

## Целевая модель слоёв

```
Telegram Update
      ↓
TelegramAdapter          // UI: decode → Command, render View
      ↓
BookingCommand           // intent (domain)
      ↓
UseCase                  // load session → FSM → save → run port effects
      ↓
BookingStateMachine      // единственный источник переходов
      ↓
BookingSession           // dumb snapshot (data only)
      ↓
BookingSessionStore      // port (in-memory → Redis)
```

**Внешние адаптеры (ports):** Yclients, PostgreSQL (`BookingRepository`), `BotNotifier`, `AdminNotifier`.

---

## FSM Rules

### `BookingStateMachine` — единственный source of truth для переходов wizard

**Может:**

- валидировать переход: `phase` + `BookingCommand` → разрешён / запрещён;
- обновлять `BookingSession` (поля + `phase`);
- возвращать `TransitionResult` с обновлённой сессией, `portEffects` и `uiSignals`.

**Не может:**

- импортировать или вызывать Telegram, Yclients, Spring, JPA, repositories;
- знать policy отмены, SMS-код 432, тексты сообщений;
- вызывать `OffsetDateTime.now()` и внешние API;
- содержать ветвления вида «если Yclients вернул …».

### Фазы (`BookingPhase`)

```
IDLE → STAFF_SELECTED → SERVICES_SELECTED → SLOT_SELECTED
    → AWAITING_NAME → AWAITING_PHONE → AWAITING_SMS → CONFIRMING → DONE
```

- `phase` **записывается только FSM** при успешном transition.
- `phase` **не вычисляется** из других полей сессии.

### Purity gate (PR-2+)

Пакет `domain.booking` — **zero imports** из `telegram`, `yclients`, `repository`, `service`, `spring`.

---

## Command Model

### Терминология

| Термин | Слой | Назначение |
|--------|------|------------|
| `UiAction` | transport | декодированный callback (`b:v1:…`) |
| `BookingCommand` | domain | намерение пользователя |
| `TransitionResult` | domain | исход FSM |
| `BookingEffect` | domain | инструкция use case для **port** |
| `UiSignal` | domain | подсказка **View** (не side effect) |
| Domain event | application | факт для аналитики (`BOOKING_CREATED`) |

FSM принимает **`BookingCommand`**, не «события из Telegram».

### Команды (domain)

```
SelectStaffCommand
AddServiceCommand / ContinueToDatesCommand
SelectDateCommand
SelectSlotCommand
SubmitNameCommand
SubmitPhoneCommand
SubmitSmsCodeCommand
CancelWizardCommand
```

### CommandMapper

```
UiAction  →  BookingCommand   (pure mapping, 0 decisions)
LegacyCallbackAdapter  →  UiAction   (только parse legacy string)
```

**CommandMapper не может:**

- проверять `phase` для принятия решения «можно / нельзя»;
- содержать бизнес-ветвления и fallback-логику CRM;
- вызывать FSM или services.

Invalid command → FSM `reject`, не mapper.

---

## BookingSession Rules

`BookingSession` — **dumb DTO**:

- только поля (`slug`, `staffId`, `serviceIds`, `slot`, `pendingBookingId`, `phase`, …);
- immutable / `with*` builders; **package-private** mutators внутри `domain.booking`;
- **без** методов `canX()`, `isReady()`, derived state.

### Запрет двойной истины

| Разрешено читать `phase` | Запрещено |
|--------------------------|-----------|
| FSM (transition table) | `if (session.phase() == …)` в handlers для бизнес-логики |
| View (`switch` для текста кнопок) | `isAwaitingFullName` / `isAwaitingCode` после миграции на FSM |

Единственный writer `phase` — `BookingStateMachine`.

---

## Effect Rules

### Разделение результата FSM

```java
TransitionResult(
    BookingSession session,
    List<BookingEffect> portEffects,   // → BookingEffectExecutor → ports
    List<UiSignal> uiSignals           // → ViewFactory (без executor)
)
```

### `BookingEffect` — только намерения к ports

```java
// ✅ допустимо
record StartConfirmation() implements BookingEffect {}
record ClearWizard() implements BookingEffect {}

// ❌ запрещено в FSM
record CallYclientsCreateBooking(...) {}
record SendTelegramMessage(...) {}
```

### Контракт `BookingEffectExecutor`

| Правило | Описание |
|---------|----------|
| **Idempotent** | повтор `StartConfirmation` с тем же `pendingBookingId` не создаёт вторую запись |
| **Stateless** | executor не хранит позицию в pipeline |
| **Independent** | effect A не вызывает effect B |
| **Linear** | use case выполняет `portEffects` последовательно, без `if`-цепочек внутри executor |

**Executor не может:** ветвить pipeline, вызывать другие effects, строить UI.

### SMS / Yclients

- SMS-required (код 432) — **не transition FSM**; ответ port → use case отправляет `SubmitSmsCodeCommand`.
- FSM не знает про код 432.

---

## Transport Rules (callbackData)

`callbackData` = **transport layer**, не domain.

### Формат (целевой)

```
b:v1:<base64url(json)>
{ "a": "slot", "i": 3 }
```

- `sessionId` / ref — **transport reference**, не domain identity.
- Индексы (`i`) ссылаются на данные в `BookingSession`, не на Yclients ID в кнопке.

### Версионирование

- `CallbackTransportV1`, `CallbackTransportV2` — **отдельные классы**.
- Legacy: `LegacyCallbackAdapter` — отдельный `@Deprecated` класс с датой удаления.
- **Запрещено:** legacy-ветки внутри `CallbackTransport.decode()`.

### Dual-read (миграция)

Новые кнопки — только `b:v1:…`. Старые callback в чате — graceful degrade («/menu»).

---

## Use Case Rules

Use case **orchestrate, not decide**.

```java
session = store.get(chatId);
result = fsm.transition(session, command);
if (result.rejected()) return view.rejected(result);
store.save(chatId, result.session());
result.portEffects().forEach(executor::run);
return view.forResult(result);
```

| Решает | Где |
|--------|-----|
| Допустимость перехода wizard | FSM |
| Можно ли отменить запись | `BookingCancellationPolicy` |
| Ответ Yclients (432, ошибки) | `YclientsErrorMapper` → use case |
| Текст и клавиатуры | View |

**Запрещено в use case:** `if (smsCode == null && …)` с прямым вызовом Yclients без FSM/effects (допустимо только временно при миграции с `// TODO`).

---

## Policy (вне FSM)

`BookingCancellationPolicy` — domain, без Telegram/Yclients:

- `canCancel(booking, now)`
- `shouldMarkCompleted(booking, now)`

Отмена в CRM — через `YclientsBookingPort` в use case / executor, не в handler.

---

## Запреты (consolidated)

### Domain / FSM

- ❌ FSM импортирует infrastructure
- ❌ FSM знает Yclients, Telegram, SMS, policy отмены
- ❌ FSM мутирует `BookingData` / entity JPA
- ❌ `BookingSession` содержит бизнес-методы и derived state
- ❌ `phase` меняется вне FSM
- ❌ handlers проверяют `phase` / `isAwaiting*` для бизнес-ветвлений

### Transport / Mapper

- ❌ `split("_")` для slug с `_` в новом коде (legacy adapter — временно)
- ❌ бизнес-логика в `CommandMapper` / `CallbackTransport`
- ❌ versioning через `if` внутри одного decoder

### Application

- ❌ `BookingApplicationService` на 500+ строк (fat use case)
- ❌ handler → `BookingRepository` напрямую
- ❌ handler → `YclientsService` без use case (целевое состояние)
- ❌ `BOOKING_CREATED` без успешного CONFIRMED + `recordId`
- ❌ `CONFIRMED` в БД без успеха Yclients

### Infrastructure

- ❌ `service` → `TelegramLongPollingBot` (использовать `Notifier` port)
- ❌ `TelegramSenderService` ↔ `TelegramBot` без `TelegramExecutor` abstraction
- ❌ мутация singleton-полей (`partnerToken = …`) в HTTP-клиенте

### Effects

- ❌ effect вызывает effect
- ❌ UI-промпты в `BookingEffectExecutor` (только `UiSignal` → View)
- ❌ скрытый workflow внутри executor (`if failed then run X`)

---

## Data invariants (обязательны всегда)

| Инвариант | Правило |
|-----------|---------|
| Статус в БД | `PENDING` до успеха Yclients → затем `CONFIRMED` + `recordId` / `recordHash` |
| SMS-retry | одна `PENDING`-запись на попытку wizard (`pendingBookingId`) |
| Аналитика | `BOOKING_CREATED` только при подтверждённой записи |
| Сессия wizard | `BookingSessionStore.remove` после успеха и при `/cancel` wizard |

---

## Definition of Done (PR с wizard / FSM)

- [ ] `domain.booking` без внешних imports (ArchUnit или grep в CI)
- [ ] Все переходы wizard в FSM + table-driven тест
- [ ] Illegal transition → `reject`, покрыт тестом
- [ ] Idempotency: duplicate `SubmitPhone` / SMS-retry не создаёт вторую запись
- [ ] Handlers не содержат `bookingRepository` / прямых Yclients-вызовов (целевое)
- [ ] `LegacyCallbackAdapter` не смешан с `CallbackTransportV1`

---

## Дорожная карта (кратко)

1. **PR-1** — инварианты данных в существующем коде ✅  
2. **PR-2** — `BookingStateMachine` + Commands + unit-тесты (без Telegram)  
3. **PR-3** — handlers вызывают FSM; `BookingSessionStore` interface  
4. **PR-4** — `CallbackTransport` v1 + `LegacyCallbackAdapter`  
5. **PR-5** — `HandleBookingCommandUseCase` + thin handlers  
6. **PR-6** — thin `TelegramBot`, Views, ports  
7. **PR-7** — Yclients adapter split; Redis `BookingSessionStore`  

---

## Ссылки

- [README.md](./README.md) — обзор продукта и стека
- [docs/SystemMapping.md](./docs/SystemMapping.md) — CURRENT → TARGET mapping по пакетам
- [docs/CurrentState.md](./docs/CurrentState.md) — фактическое состояние кода и отклонения
- Текущий код до полной миграции: `BookingStateService`, `callBackData/*`, `TelegramBot` — **legacy surface**, изменять только с учётом контракта выше
