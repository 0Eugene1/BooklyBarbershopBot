# Current State

Source of truth: [ARCHITECTURE_CONTRACT.md](../ARCHITECTURE_CONTRACT.md).  
Mapping reference: [SystemMapping.md](./SystemMapping.md).

Snapshot of **what is implemented today** in the repository, and **known gaps** vs the target architecture.

---

## Implementation status

| Contract area | Status | Notes |
|---------------|--------|-------|
| Data invariants (PENDING → CONFIRMED, SMS idempotency) | **Done (PR-1)** | `BookingService`, `BookingConfirmationService` |
| `BOOKING_CREATED` only on success | **Done (PR-1)** | `TelegramBot.saveBookingCreatedEvent` |
| Wizard cache clear on success / `/cancel` | **Done (PR-1)** | `BookingStateService.remove` |
| Cancel persistence via `BookingService` | **Done (PR-1)** | `CancelCallBackHandler` |
| Feedback path consolidation | **Partial (PR-1)** | `ReviewCallBackHandler` = `feedback_` only; `RatingCallBackHandler` = `rating_` |
| `domain.booking` / FSM | **Not started** | No `BookingStateMachine` |
| `BookingCommand` model | **Not started** | Wizard driven by callback strings + flags |
| `BookingSessionStore` port | **Not started** | `BookingStateService` only |
| `CallbackTransport` v1 | **Not started** | Legacy `split("_")` everywhere |
| Thin `TelegramBot` | **Not started** | God-class entry point |
| `TelegramExecutor` / notifier ports | **Not started** | Cycles and direct bot refs remain |
| Yclients adapter split | **Not started** | Monolithic `YclientsService` |
| `BookingCancellationPolicy` | **Not started** | Logic duplicated in handlers |

---

## Runtime topology

- **Spring Boot** monolith, long polling (`config.TelegramBotConfig`).
- **Two bots:** `telegramBot.TelegramBot` (client), `telegramBot.adminBot.AdminBot` (admin registration).
- **PostgreSQL** via JPA (`entity`, `repository`); Liquibase migrations.
- **Yclients** via `service.yclientsService` (WebClient, blocking `.block()`).
- **Wizard session:** in-memory `service.BookingStateService` → `dto.BookingData` keyed by `chatId`.
- **REST:** `eventsController.BotEventStatsController` — `GET /api/stats/{barbershopId}`.

---

## Package responsibilities (actual)

| Package | Role today |
|---------|------------|
| `telegramBot` | Update entry, `/start`, wizard text (name/phone/SMS), command dispatch, analytics hooks, direct `service` calls |
| `callBackData` | Callback router (`CallBack`) + 14 handlers; booking wizard steps, cancel, feedback; builds inline keyboards; calls Yclients/ParsingDto where needed |
| `handlers` | `BookingConfirmationService`, `MenuCommandHandler`, `MyBookingsHandler`, `PhoneNumberRequestService` |
| `service` | `BarbershopService`, `ClientService`, `BookingService`, `BookingStateService`, `ParsingDtoService`, `eventService`, `adminBotService`, `yclientsService` |
| `entity` / `repository` | `Barbershop`, `Client`, `Booking`, `Feedback`, `BotEvent`, … |
| `dto` | `BookingData` (wizard + flags), Yclients DTOs |
| `sendMessage` | `MessageSender`, `TelegramSenderService` (depends on `TelegramBot` via `ObjectProvider`) |
| `inlineButtons` | Main menu keyboard |
| `config` | Bot registration, `@EnableScheduling` |
| `bookingstatus` | `BookingStatusUpdater` — cron status transitions |
| `enums` | `BookingStatus`: PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELED |

**Absent:** `domain.booking`, application use-case package, transport decoders, view layer.

---

## Booking wizard (as implemented)

### Entry

1. `/start {slug}` or `lastUsedSlug` → `TelegramBot` + `BarbershopService` + `InlineKeyboard`.
2. Inline menu → `callBackData` handlers.

### Callback chain

```
book_{slug}
  → BookCallBackHandler (ParsingDtoService / staff)
staff_{staffId}_{slug}
  → StaffCallBackHandler
service_{serviceId}_{staffId}_{slug}
  → ServiceCallBackHandler (cart in BookingStateService)
continue_services_{staffId}_{slug}
  → ContinueServicesCallBackHandler (YclientsService dates)
date_{date}_{staffId}_{serviceIds}_{slug}
  → DateCallBackHandler (YclientsService times)
slot_{datetime}_{staffId}_{…}_{slug}
  → SlotCallBackHandler → awaitingFullName
```

### Contact & confirm (`TelegramBot` + `handlers`)

- Name / phone / SMS → `BookingConfirmationService`.
- Local row: `BookingService.resolvePendingBooking` → **PENDING**; after Yclients success → **CONFIRMED** + `recordId`/`recordHash`.
- `BookingData.pendingBookingId` links SMS retries to one DB row.

### Session flags (informal FSM)

`dto.BookingData`: `awaitingFullName`, `awaitingCode`, plus accumulated selection fields.  
**No `phase` enum** — contract’s `BookingPhase` is not implemented.

---

## Persistence & booking lifecycle

| Concern | Implementation |
|---------|----------------|
| Create local booking | `BookingService.createBookingFromData` / `resolvePendingBooking` |
| Confirm in CRM | `YclientsService.createBooking` |
| SMS challenge | `YclientsSmsConfirmationException` (HTTP 422, code 432) |
| Status cron | `bookingstatus.BookingStatusUpdater` every 15 min |
| Cancel | `CancelCallBackHandler` → `YclientsService.cancelBooking` → `BookingService.saveBooking` |

---

## Integrations

### Yclients (`service.yclientsService`)

- `YclientsHttpClient` — HTTP, auth headers, `.block()`.
- `YclientsDataService` — parse/cache staff & service names.
- `YclientsService` — booking, slots, dates, cancel; resolves slug via `BarbershopService` in `createBooking`.
- `ParsingDtoService` — second parse path used by `callBackData` handlers.

**Env tokens:** `YclientsHttpClient` accepts `${YCLIENTS_*}` with fallback to `${yclients.*}`; `YclientsService` uses `${yclients.partner-token}` / `user-token`.

### Telegram

- `TelegramSenderService` implements `MessageSender`; executes via `TelegramBot`.
- `AdminBotSender` calls `AdminBot` directly from `FeedbackService`.

---

## Deviations from target architecture

### Critical (workflow / consistency)

| Deviation | Location | Contract violation |
|-----------|----------|-------------------|
| No explicit FSM | `BookingStateService` + scattered `if` in handlers / `TelegramBot` | Transitions not centralized |
| `split("_")` callback protocol | All `callBackData` booking handlers | Transport encodes domain; breaks on `_` in slug |
| 64-byte callback truncation | `DateCallBackHandler` | Slot buttons may lose `slug` |
| Hardcoded `+3:00` / `Europe/Moscow` | `SlotCallBackHandler`, `MyBookingsHandler`, `CancelCallBackHandler` | Timezone not per barbershop |
| Handler → Yclients direct | `BookCallBackHandler`, `DateCallBackHandler`, `CancelCallBackHandler`, … | Should go through use case + port |
| `TelegramBot` god entry | `telegramBot.TelegramBot` | Routing + wizard + analytics + services |

### Structural (layers)

| Deviation | Location |
|-----------|----------|
| `TelegramSenderService` → `TelegramBot` | Circular presentation dependency |
| `AdminBotSender` → `AdminBot` | Service layer → Telegram bot |
| Duplicate feedback handlers history | `ReviewCallBackHandler` trimmed; `RatingCallBackHandler` + `LowRatingReasonCallBackHandler` remain separate |
| `CancelCallBackHandler` owns re-book UI | Duplicates `BookCallBackHandler` staff menu |
| `MyBookingsHandler` / `CancelCallBackHandler` duplicate card formatting | No shared view |
| `YclientsHttpClient` mutates `partnerToken` field | `getAvailableTimes` |
| Dual Yclients parse paths | `ParsingDtoService` vs `YclientsDataService` |

### Resolved in PR-1 (was deviation, now aligned)

| Item | Resolution |
|------|------------|
| CONFIRMED before Yclients | Now PENDING until success |
| Duplicate bookings on SMS retry | `resolvePendingBooking` + `pendingBookingId` |
| `BOOKING_CREATED` on failure | Event only when `confirmBooking` returns true |
| `CancelCallBackHandler` → `BookingRepository` | Uses `BookingService.saveBooking` |
| Stale wizard cache after success | `BookingStateService.remove` on confirm |

---

## Data invariants (enforced now)

| Invariant | Enforcement |
|-----------|-------------|
| No CONFIRMED without Yclients success | `BookingConfirmationService` sets CONFIRMED after `createBooking` success |
| Single PENDING row per wizard attempt (SMS) | `resolvePendingBooking` + `pendingBookingId` on `BookingData` |
| Analytics integrity | `BOOKING_CREATED` gated on `confirmed == true` |
| Session cleanup | `remove(chatId)` on confirm; `/cancel` clears wizard cache |

---

## Operational constraints

- **Single instance assumption:** `BookingStateService` is JVM-local; restart drops in-progress wizards.
- **Thread pool:** `TelegramBotConfig` — `maxThreads(2)`.
- **Tests:** `BooklyBarbershopBotApplicationTests` requires datasource configuration (no test profile in repo).

---

## Related documents

- [ARCHITECTURE_CONTRACT.md](../ARCHITECTURE_CONTRACT.md) — target rules and prohibitions
- [SystemMapping.md](./SystemMapping.md) — CURRENT → TARGET component map
- [README.md](../README.md) — product overview
