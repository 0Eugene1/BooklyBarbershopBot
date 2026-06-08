# System Mapping — CURRENT → TARGET

Source of truth: [ARCHITECTURE_CONTRACT.md](../ARCHITECTURE_CONTRACT.md).

This document maps **existing packages and components** to the **target FSM / hexagonal architecture**. No new concepts beyond the contract.

---

## Layer overview

| Layer | CURRENT (as-is) | TARGET (contract) |
|-------|-----------------|-------------------|
| Entry | `telegramBot.TelegramBot`, `telegramBot.adminBot.AdminBot` | `TelegramAdapter` + thin `TelegramBot` (`execute` only) |
| Routing | `callBackData.CallBack` + `CallBackHandler` list; commands in `TelegramBot` | `UpdateDispatcher` → callback / message handlers |
| Wizard orchestration | Implicit in `callBackData/*`, `TelegramBot`, `handlers/*` | `HandleBookingCommandUseCase` + thin handlers |
| State machine | None (informal FSM in handlers) | `domain.booking.BookingStateMachine` |
| Session | `dto.BookingData` + `service.BookingStateService` | `domain.booking.BookingSession` + `BookingSessionStore` port |
| Commands | Encoded in `callbackData` strings + boolean flags | `domain.booking.command.BookingCommand` |
| Transport | `staff_*`, `service_*`, `date_*`, `slot_*`, … (`split("_")`) | `b:v1:` + `LegacyCallbackAdapter` |
| Domain policy | Inline in `CancelCallBackHandler`, `MyBookingsHandler` | `BookingCancellationPolicy` |
| Persistence | `entity.*` + `repository.*` via `service.BookingService`, etc. | Unchanged; access via use cases / ports |
| Yclients | `service.yclientsService.*` + `service.ParsingDtoService` | `YclientsBookingPort`, `YclientsCatalogPort`, adapter split |
| Outbound Telegram | `sendMessage.MessageSender`, `TelegramSenderService` → `TelegramBot` | `TelegramExecutor` + `BotNotifier` port |
| Admin notify | `service.adminBotService.AdminBotSender` → `AdminBot` | `AdminNotifier` port |
| Analytics | `service.eventService.BotEventService` | Same; events from use case after invariants |
| REST | `eventsController.BotEventStatsController` | Unchanged |
| Scheduled | `bookingstatus.BookingStatusUpdater` | Unchanged |

---

## Entry & routing

| CURRENT | TARGET | Migration |
|---------|--------|-----------|
| `TelegramBot.onUpdateReceived` — callback + message dispatch | `UpdateDispatcher` | Extract dispatch; shrink `TelegramBot` |
| `TelegramBot` — `/start`, wizard text, analytics, service calls | `StartCommandHandler`, `BookingContactHandler`, use cases | Move logic per command |
| `TelegramBot` → `LowRatingReasonCallBackHandler` (text) | `MessageUpdateHandler` → use case / handler | Route via dispatcher |
| `callBackData.CallBack` — `CallBackHandler.supports()` chain | Same pattern or unified `UpdateHandler` with `@Order` | Keep router; handlers become thin |
| `adminBot.AdminBot` + `callBackData.SetAdminCommandHandler` | Admin adapter + command handler | `AdminNotifier` port for outbound |

---

## Booking wizard — step mapping

Informal flow today → contract phases (`BookingPhase`).

| Step | CURRENT component | Callback / trigger | TARGET command | TARGET phase after transition |
|------|-------------------|--------------------|----------------|------------------------------|
| Menu | `inlineButtons.InlineKeyboard`, `handlers.MenuCommandHandler` | `/menu`, `/start` | — (out of wizard) | `IDLE` |
| Staff list | `callBackData.BookCallBackHandler` | `book_{slug}` | — (loads UI) | — |
| Staff select | `callBackData.StaffCallBackHandler` | `staff_{id}_{slug}` | `SelectStaffCommand` | `STAFF_SELECTED` |
| Service add | `callBackData.ServiceCallBackHandler` | `service_{…}` | `AddServiceCommand` | `SERVICES_SELECTED` |
| More services | `callBackData.ChooseServiceCallBackHandler` | `choose_service_{…}` | — (UI) | — |
| To dates | `callBackData.ContinueServicesCallBackHandler` | `continue_services_{…}` | `ContinueToDatesCommand` | — |
| Date | `callBackData.DateCallBackHandler` | `date_{…}` | `SelectDateCommand` | — |
| Slot | `callBackData.SlotCallBackHandler` | `slot_{…}` | `SelectSlotCommand` | `SLOT_SELECTED` |
| Name | `TelegramBot.handleBookingData` | text | `SubmitNameCommand` | `AWAITING_NAME` → `AWAITING_PHONE` |
| Phone | `TelegramBot` + `handlers.PhoneNumberRequestService` | contact | `SubmitPhoneCommand` | `CONFIRMING` |
| SMS | `TelegramBot` + `handlers.BookingConfirmationService` | text code | `SubmitSmsCodeCommand` | `AWAITING_SMS` → `CONFIRMING` |
| Confirm | `handlers.BookingConfirmationService` + `service.yclientsService.YclientsService` | — | effect `StartConfirmation` | `DONE` |
| Cancel wizard | `TelegramBot.handleCancelCommand` → `bookingStateService.remove` | `/cancel` | `CancelWizardCommand` | `IDLE` |

**CURRENT state holder:** `service.BookingStateService` (`Map<chatId, dto.BookingData>`).

**TARGET state holder:** `BookingSessionStore` (in-memory impl → Redis).

---

## Confirmation & persistence

| CURRENT | TARGET |
|---------|--------|
| `handlers.BookingConfirmationService` — client, pending booking, Yclients, messages | `ConfirmBookingUseCase` + `BookingEffectExecutor` + `YclientsBookingPort` |
| `service.BookingService.createBookingFromData` / `resolvePendingBooking` | Port effect after FSM `CONFIRMING`; same invariants |
| `entity.Booking` + `enums.BookingStatus` | Unchanged |
| `dto.BookingData.pendingBookingId` | Moves to `BookingSession.pendingBookingId` |

**Data invariants (enforced now, contract):** `PENDING` until Yclients success → `CONFIRMED` + `recordId`/`recordHash`; SMS-retry reuses one pending row.

---

## Cancellation & listings

| CURRENT | TARGET |
|---------|--------|
| `handlers.MyBookingsHandler` — query, `canCancel`, UI cards | `ListBookingsQuery` + `BookingCardView` + `BookingCancellationPolicy` |
| `callBackData.CancelCallBackHandler` — validation, Yclients, status, UI, re-book menu | `CancelBookingUseCase` + policy + port |
| `callBackData.CancelMenuCallBackHandler` → `MyBookingsHandler` | Thin callback → use case / query |

---

## Feedback

| CURRENT | TARGET |
|---------|--------|
| `callBackData.ReviewCallBackHandler` — `feedback_` | View / start feedback flow |
| `callBackData.RatingCallBackHandler` — `rating_` | Command + `FeedbackApplicationService` |
| `callBackData.LowRatingReasonCallBackHandler` — `low_rating_reason_` + text in `TelegramBot` | Command + use case; text via message handler |
| `service.adminBotService.FeedbackService` + `AdminBotSender` | `FeedbackApplicationService` + `AdminNotifier` |

---

## Yclients integration

| CURRENT | TARGET |
|---------|--------|
| `service.yclientsService.YclientsHttpClient` | `YclientsApiClient` |
| `service.yclientsService.YclientsDataService` | Part of `YclientsCatalogPort` |
| `service.yclientsService.YclientsService` | `YclientsBookingAdapter` + catalog facade |
| `service.ParsingDtoService` | Merged into catalog port (remove duplicate parse path) |
| `YclientsService` → `service.BarbershopService` (slug → companyId) | `companyId` passed by caller; adapter stays pure |

---

## Transport mapping

| CURRENT `callbackData` | TARGET |
|------------------------|--------|
| `book_{slug}`, `staff_{id}_{slug}`, … | `LegacyCallbackAdapter` → `UiAction` → `BookingCommand` |
| (none) | `CallbackTransportV1`: `b:v1:<payload>` |
| Parsing via `split("_", n)` in each handler | `CommandMapper` (pure); indices into session for slots/services |

---

## Package map (CURRENT → TARGET additions)

```
com.example.BooklyBarbershopBot
├── telegramBot              → thin entry + adminBot (unchanged package)
├── callBackData             → thin handlers (unchanged package)
├── handlers                 → shrinks; some → use cases
├── service                  → domain services + port implementations
│   ├── yclientsService      → adapters
│   ├── eventService         → unchanged
│   └── adminBotService      → notifier impl
├── entity / repository / dto / enums   → unchanged
├── sendMessage / inlineButtons       → Views + MessageSender
├── config / bookingstatus / eventsController
└── domain.booking           → NEW (PR-2+): FSM, Session, Command, Effect, Policy
```

---

## Migration sequence (from contract)

| PR | Mapping focus |
|----|----------------|
| PR-1 ✅ | Data invariants in `BookingService`, `BookingConfirmationService`, `TelegramBot` |
| PR-2 | Add `domain.booking`; FSM parallel to legacy |
| PR-3 | Handlers call FSM; `BookingSessionStore` interface |
| PR-4 | `CallbackTransport` v1 + `LegacyCallbackAdapter` |
| PR-5 | `HandleBookingCommandUseCase`; thin `callBackData` |
| PR-6 | Thin `telegramBot`; Views; `TelegramExecutor` |
| PR-7 | Yclients adapter split; Redis `BookingSessionStore` |

---

## Review anchor

> FSM decides transitions; Policy decides cancel rules; Ports talk to the world; Use case orchestrates; Mapper renames; Handler displays.

Any PR that adds business branching to `TelegramBot`, `callBackData`, or `BookingData` flags instead of FSM violates the target map.
