package com.testproject.calendar.owner;

import com.testproject.calendar.api.ApiFailure;
import com.testproject.calendar.generated.model.PrepareBookingAttemptCommand;
import com.testproject.calendar.generated.model.PreparedBookingAttempt;
import com.testproject.calendar.generated.model.BookingAttemptResult;
import com.testproject.calendar.generated.model.BookingDetails;
import com.testproject.calendar.generated.model.BookingDecisionCommand;
import com.testproject.calendar.generated.model.RescheduleBookingCommand;
import com.testproject.calendar.generated.model.RescheduleSlots;
import com.testproject.calendar.generated.model.SubmittedBookingAttempt;
import com.testproject.calendar.availability.SlotAvailability;
import com.testproject.calendar.generated.model.PublicSlots;
import com.testproject.calendar.generated.model.ApiError;
import com.testproject.calendar.generated.model.FieldError;
import com.testproject.calendar.generated.model.OwnerProfile;
import com.testproject.calendar.generated.model.OwnerOperationResult;
import com.testproject.calendar.generated.model.PublicBookingCommand;
import com.testproject.calendar.generated.model.ProfileCommand;
import com.testproject.calendar.generated.model.TimeZoneCommand;
import com.testproject.calendar.generated.model.MeetingType;
import com.testproject.calendar.generated.model.CreateMeetingTypeCommand;
import com.testproject.calendar.generated.model.UpdateMeetingTypeCommand;
import com.testproject.calendar.generated.model.ArchiveMeetingTypeCommand;
import com.testproject.calendar.generated.model.WeeklyHoursCommand;
import com.testproject.calendar.generated.model.DateOverride;
import com.testproject.calendar.generated.model.DateOverrideCommand;
import com.testproject.calendar.generated.model.DeleteDateOverrideCommand;
import com.testproject.calendar.generated.model.OutOfOffice;
import com.testproject.calendar.generated.model.TimeBlock;
import com.testproject.calendar.generated.model.TimeBlockResult;
import com.testproject.calendar.generated.model.CreateTimeBlockCommand;
import com.testproject.calendar.generated.model.UpdateTimeBlockCommand;
import com.testproject.calendar.generated.model.DeleteTimeBlockCommand;
import com.testproject.calendar.generated.model.CreateOutOfOfficeCommand;
import com.testproject.calendar.generated.model.UpdateOutOfOfficeCommand;
import com.testproject.calendar.generated.model.DeleteOutOfOfficeCommand;
import com.testproject.calendar.generated.model.AffectedBooking;
import com.testproject.calendar.generated.model.WorkingDay;
import com.testproject.calendar.generated.model.WorkingInterval;
import com.testproject.calendar.generated.model.PublicBookingPage;
import com.testproject.calendar.generated.model.PublicMeetingType;
import com.testproject.calendar.generated.model.PublicMeetingTypePage;
import java.time.DayOfWeek;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;

@Service
public class OwnerState {

    private final InitialCalendarSettings initial;
    private final SlotAvailability availability;
    private final java.time.Clock clock;
    private boolean bookingEnabled;
    private String name;
    private String username;
    private String timeZone;
    private long version = 1;
    private final Map<String, SavedCommand> operations = new HashMap<>();
    private final Map<String, MeetingType> meetingTypes = new LinkedHashMap<>();
    private final Map<String, OutOfOffice> vacations = new LinkedHashMap<>();
    private final Map<String, TimeBlock> timeBlocks = new LinkedHashMap<>();
    private final java.security.SecureRandom random = new java.security.SecureRandom();
    private final Map<String, PreparedAttempt> attempts = new HashMap<>();
    private final Map<String, BookingDetails> bookings = new HashMap<>();
    private final Map<String, BookingDetails> resultLinks = new HashMap<>();

    // Only immutable values, never the mutable transport command. No generated toString containing secrets.
    private static final class PreparedAttempt {
        private final String secret;
        private final String username;
        private final String meetingTypeId;
        private final String conditionsToken;
        private final java.time.OffsetDateTime startsAt;
        private final String guestName;
        private final String guestEmail;
        private final String comment;
        private SubmittedBookingAttempt success;
        private ApiFailure failure;

        private PreparedAttempt(String secret, String username, PrepareBookingAttemptCommand command) {
            this.secret = secret;
            this.username = username;
            this.meetingTypeId = command.getMeetingTypeId();
            this.conditionsToken = command.getConditionsToken();
            this.startsAt = command.getStartsAt();
            this.guestName = command.getGuestName();
            this.guestEmail = command.getGuestEmail();
            this.comment = command.getComment();
        }
    }

    public record Submission(boolean created, SubmittedBookingAttempt result) { }

    private PreparedAttempt authenticatedAttempt(String id, String authorization) {
        var attempt = attempts.get(id);
        if (attempt == null || authorization == null || !MessageDigest.isEqual(
                ("Bearer " + attempt.secret).getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiFailure(410, new ApiError("ATTEMPT_UNAVAILABLE",
                    "Попытка недоступна. Возможен сброс данных."));
        }
        return attempt;
    }

    public synchronized BookingAttemptResult attemptResult(String id, String authorization) {
        var attempt = authenticatedAttempt(id, authorization);
        if (attempt.success != null) {
            refreshBookings();
            var result = submissionResult(attempt);
            return new BookingAttemptResult(BookingAttemptResult.StateEnum.SUCCEEDED)
                    .booking(result.getBooking()).resultUrl(result.getResultUrl());
        }
        if (attempt.failure != null) {
            return new BookingAttemptResult(BookingAttemptResult.StateEnum.FAILED).error(attempt.failure.error());
        }
        return new BookingAttemptResult(BookingAttemptResult.StateEnum.PREPARED);
    }

    public synchronized Submission submitAttempt(String id, String authorization) {
        var attempt = authenticatedAttempt(id, authorization);
        if (attempt.success != null) {
            refreshBookings();
            return new Submission(false, submissionResult(attempt));
        }
        if (attempt.failure != null) {
            throw attempt.failure;
        }
        try {
            return createBooking(attempt);
        } catch (ApiFailure failure) {
            attempt.failure = failure;
            throw failure;
        }
    }

    private Submission createBooking(PreparedAttempt attempt) {
        requireUsername(attempt.username);
        var type = meetingType(attempt.meetingTypeId);
        if (!bookingEnabled) {
            throw new ApiFailure(409, new ApiError("BOOKING_DISABLED", "Запись временно недоступна"));
        }
        if (type.getArchived()) {
            throw new ApiFailure(409, new ApiError("TYPE_ARCHIVED", "Запись на этот тип встречи прекращена"));
        }
        if (!conditionsToken(type).equals(attempt.conditionsToken)) {
            throw new ApiFailure(409, new ApiError("CONDITIONS_CHANGED",
                    "Условия встречи изменились. Выберите время заново.").currentConditions(publicType(type)));
        }
        var slots = availability.slots(type, profile().getTimeZone(), conditionsToken(type));
        var start = attempt.startsAt.toInstant();
        var end = start.plusSeconds(type.getDurationMinutes() * 60L);
        if (start.isBefore(slots.getWindowStart().toInstant()) || end.isAfter(slots.getWindowEnd().toInstant())) {
            throw new ApiFailure(409, new ApiError("OUTSIDE_BOOKING_WINDOW",
                    "Окно записи изменилось. Выберите время заново."));
        }
        if (vacationClosed(start, end, vacations.values())
                || slots.getItems().stream().noneMatch(slot -> slot.getStartsAt().toInstant().equals(start))) {
            throw new ApiFailure(409, new ApiError("SLOT_UNAVAILABLE",
                    "Время недоступно. Выберите время заново."));
        }
        if (occupied(start, end)) {
            throw new ApiFailure(409, new ApiError("TIME_CONFLICT", "Время уже занято. Выберите другое время."));
        }
        var booking = new BookingDetails(randomToken(), type.getId(), name, profile().getTimeZone(),
                type.getTitle(), type.getDescription(), type.getDurationMinutes(), type.getRequiresApproval(),
                attempt.startsAt, attempt.startsAt.plusMinutes(type.getDurationMinutes()),
                type.getRequiresApproval() ? BookingDetails.StatusEnum.PENDING : BookingDetails.StatusEnum.CONFIRMED,
                attempt.guestName, attempt.guestEmail, attempt.comment, 1L, List.of("cancel", "export"));
        var token = randomToken();
        while (resultLinks.containsKey(token)) {
            token = randomToken();
        }
        attempt.success = new SubmittedBookingAttempt(SubmittedBookingAttempt.StateEnum.SUCCEEDED,
                booking, "/bookings/" + token);
        bookings.put(booking.getId(), booking);
        resultLinks.put(token, booking);
        return new Submission(true, submissionResult(attempt));
    }

    private SubmittedBookingAttempt submissionResult(PreparedAttempt attempt) {
        return new SubmittedBookingAttempt(SubmittedBookingAttempt.StateEnum.SUCCEEDED,
                bookingSnapshot(attempt.success.getBooking()), attempt.success.getResultUrl());
    }

    // Do not expose state that another request can mutate while Spring serializes this response.
    private BookingDetails bookingSnapshot(BookingDetails booking) {
        return new BookingDetails(booking.getId(), booking.getMeetingTypeId(), booking.getOwnerName(),
                booking.getOwnerTimeZone(), booking.getTitle(), booking.getDescription(), booking.getDurationMinutes(),
                booking.getRequiresApproval(), booking.getStartsAt(), booking.getEndsAt(), booking.getStatus(),
                booking.getGuestName(), booking.getGuestEmail(), booking.getComment(), booking.getVersion(),
                List.copyOf(booking.getAllowedActions())).rejectionReason(booking.getRejectionReason());
    }

    public synchronized BookingDetails bookingResult(String token) {
        var booking = resultLinks.get(token);
        if (booking == null) {
            throw new ApiFailure(404, new ApiError("BOOKING_NOT_FOUND", "Бронирование не найдено"));
        }
        refreshBookings();
        return bookingSnapshot(booking);
    }

    public synchronized String bookingCalendar(String token,
            com.testproject.calendar.booking.PublicApplicationOrigin origin) {
        var booking = resultLinks.get(token);
        if (booking == null) {
            throw new ApiFailure(404, new ApiError("BOOKING_NOT_FOUND", "Бронирование не найдено"));
        }
        var now = clock.instant();
        refreshBookings(now);
        if (booking.getStatus() != BookingDetails.StatusEnum.PENDING
                && booking.getStatus() != BookingDetails.StatusEnum.CONFIRMED) {
            throw new ApiFailure(409, new ApiError("ACTION_NOT_ALLOWED",
                    "Экспорт недоступен для текущего статуса.").currentBooking(bookingSnapshot(booking)));
        }
        return com.testproject.calendar.booking.CalendarFile.render(booking, now, origin.resultUrl(token));
    }

    public synchronized BookingDetails ownerBooking(String id) {
        var booking = bookings.get(id);
        if (booking == null) {
            throw new ApiFailure(404, new ApiError("BOOKING_NOT_FOUND", "Бронирование не найдено"));
        }
        refreshBookings();
        return bookingSnapshot(booking);
    }

    public synchronized BookingDetails cancelGuestBooking(String token, BookingDecisionCommand command) {
        var current = bookingResult(token);
        if (command.getExpectedVersion() == null || command.getExpectedVersion() < 1) {
            throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте данные запроса.")
                    .fieldErrors(List.of(new FieldError("expectedVersion", "INVALID_VALUE",
                            "Укажите целую версию не меньше 1."))));
        }
        if (current.getStatus() == BookingDetails.StatusEnum.CANCELLED) {
            return current;
        }
        if (current.getVersion() != command.getExpectedVersion()) {
            throw new ApiFailure(409, new ApiError("RESOURCE_CHANGED",
                    "Бронирование изменилось. Проверьте актуальные данные и подтвердите отмену заново.")
                    .currentBooking(current));
        }
        if ((current.getStatus() != BookingDetails.StatusEnum.PENDING
                && current.getStatus() != BookingDetails.StatusEnum.CONFIRMED)
                || !clock.instant().isBefore(current.getStartsAt().toInstant())) {
            throw new ApiFailure(409, new ApiError("ACTION_NOT_ALLOWED",
                    "Действие недоступно для текущего статуса или времени.").currentBooking(current));
        }
        var booking = resultLinks.get(token);
        booking.setStatus(BookingDetails.StatusEnum.CANCELLED);
        booking.setVersion(booking.getVersion() + 1);
        booking.setAllowedActions(List.of());
        return bookingSnapshot(booking);
    }

    private record BookingDecision(String id, String action, Long expectedVersion) { }

    private record BookingMove(String id, java.time.OffsetDateTime startsAt, Long expectedVersion) { }

    private MeetingType requireReschedulable(BookingDetails booking) {
        if ((booking.getStatus() != BookingDetails.StatusEnum.PENDING
                && booking.getStatus() != BookingDetails.StatusEnum.CONFIRMED)
                || !clock.instant().isBefore(booking.getStartsAt().toInstant())) {
            throw new ApiFailure(409, new ApiError("ACTION_NOT_ALLOWED",
                    "Перенос недоступен для текущего статуса или времени.").currentBooking(booking));
        }
        var type = meetingType(booking.getMeetingTypeId());
        if (type.getArchived()) {
            throw new ApiFailure(409, new ApiError("ACTION_NOT_ALLOWED", "Перенос записи архивного типа недоступен.")
                    .currentBooking(booking));
        }
        return type;
    }

    public synchronized RescheduleSlots rescheduleSlots(String id, String fromDate, String toDate) {
        var current = ownerBooking(id);
        var type = requireReschedulable(current);
        java.time.LocalDate from;
        java.time.LocalDate to;
        try {
            from = java.time.LocalDate.parse(fromDate);
            to = java.time.LocalDate.parse(toDate);
            if (!from.isBefore(to)) {
                throw new java.time.DateTimeException("Empty range");
            }
        } catch (java.time.DateTimeException failure) {
            throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте диапазон дат.")
                    .fieldErrors(List.of(new FieldError("toDate", "INVALID_VALUE",
                            "Укажите даты YYYY-MM-DD, конец позже начала."))));
        }
        var zone = java.time.ZoneId.of(timeZone);
        var start = from.atStartOfDay(zone).toInstant();
        // Include a duration tail so starts on the last date may cross midnight.
        var end = to.atStartOfDay(zone).toInstant();
        var now = clock.instant();
        var items = availability.available(type, start, end.plusSeconds(current.getDurationMinutes() * 60L),
                current.getDurationMinutes()).stream()
                .filter(slot -> slot.getStartsAt().toInstant().isBefore(end)
                        && slot.getStartsAt().toInstant().isAfter(now)
                        && !vacationClosed(slot.getStartsAt().toInstant(), slot.getEndsAt().toInstant(),
                                vacations.values())
                        && !occupied(slot.getStartsAt().toInstant(), slot.getEndsAt().toInstant(), id)).toList();
        return new RescheduleSlots(fromDate, toDate, timeZone, type.getTimeZone(), items);
    }

    public synchronized BookingDetails rescheduleBooking(String key, String id, RescheduleBookingCommand command) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var request = new BookingMove(id, command.getStartsAt(), command.getExpectedVersion());
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(request)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return bookingSnapshot((BookingDetails) saved.result());
        }
        try {
            var errors = new ArrayList<FieldError>();
            if (command.getExpectedVersion() == null || command.getExpectedVersion() < 1) {
                errors.add(new FieldError("expectedVersion", "INVALID_VALUE", "Укажите версию не меньше 1."));
            }
            if (command.getStartsAt() == null) {
                errors.add(new FieldError("startsAt", "INVALID_VALUE", "Выберите новое время."));
            }
            if (!errors.isEmpty()) {
                throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте данные запроса.")
                        .fieldErrors(errors));
            }
            var current = ownerBooking(id);
            if (!current.getVersion().equals(command.getExpectedVersion())) {
                throw new ApiFailure(409, new ApiError("RESOURCE_CHANGED",
                        "Бронирование изменилось. Проверьте актуальные данные.").currentBooking(current));
            }
            var type = requireReschedulable(current);
            var start = command.getStartsAt().toInstant();
            var end = start.plusSeconds(current.getDurationMinutes() * 60L);
            if (!start.isAfter(clock.instant()) || vacationClosed(start, end, vacations.values())
                    || availability.available(type, start, end, current.getDurationMinutes()).isEmpty()) {
                throw new ApiFailure(409, new ApiError("SLOT_UNAVAILABLE",
                        "Время не соответствует текущей доступности."));
            }
            if (occupied(start, end, id)) {
                throw new ApiFailure(409, new ApiError("TIME_CONFLICT", "Время уже занято."));
            }
            // All competing commands use this same monitor. Recheck temporal boundaries at commit.
            current = ownerBooking(id);
            if (!current.getVersion().equals(command.getExpectedVersion())) {
                throw new ApiFailure(409, new ApiError("RESOURCE_CHANGED",
                        "Бронирование изменилось. Проверьте актуальные данные.").currentBooking(current));
            }
            requireReschedulable(current);
            if (!start.isAfter(clock.instant())) {
                throw new ApiFailure(409, new ApiError("SLOT_UNAVAILABLE", "Новое начало должно быть в будущем."));
            }
            var booking = bookings.get(id);
            booking.setStartsAt(command.getStartsAt());
            booking.setEndsAt(end.atZone(java.time.ZoneId.of(type.getTimeZone())).toOffsetDateTime());
            booking.setVersion(booking.getVersion() + 1);
            var result = bookingSnapshot(booking);
            operations.put(key, new SavedCommand(request, result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(request, null, failure));
            throw failure;
        }
    }

    public synchronized BookingDetails decideBooking(String key, String id, String action,
            BookingDecisionCommand command) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var request = new BookingDecision(id, action, command.getExpectedVersion());
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(request)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return bookingSnapshot((BookingDetails) saved.result());
        }
        try {
            if (command.getExpectedVersion() == null || command.getExpectedVersion() < 1) {
                throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте данные запроса.")
                        .fieldErrors(List.of(new FieldError("expectedVersion", "INVALID_VALUE",
                                "Укажите целую версию не меньше 1."))));
            }
            var current = ownerBooking(id);
            requireDecisionVersion(current, command.getExpectedVersion(), action);
            if (action.equals("confirm")) {
                var type = meetingType(current.getMeetingTypeId());
                var start = current.getStartsAt().toInstant();
                var end = current.getEndsAt().toInstant();
                if (vacationClosed(start, end, vacations.values())
                        || availability.available(type, start, end, current.getDurationMinutes()).isEmpty()) {
                    throw new ApiFailure(409, new ApiError("SLOT_UNAVAILABLE",
                            "Время не соответствует текущей доступности."));
                }
                if (occupied(start, end, id)) {
                    throw new ApiFailure(409, new ApiError("TIME_CONFLICT", "Время уже занято."));
                }
            }
            // Re-read time immediately before committing, still under the same state lock.
            requireDecisionVersion(ownerBooking(id), command.getExpectedVersion(), action);
            var booking = bookings.get(id);
            booking.setStatus(action.equals("cancel") ? BookingDetails.StatusEnum.CANCELLED : action.equals("confirm")
                    ? BookingDetails.StatusEnum.CONFIRMED : BookingDetails.StatusEnum.REJECTED);
            booking.setVersion(booking.getVersion() + 1);
            refreshBookings();
            var result = bookingSnapshot(booking);
            operations.put(key, new SavedCommand(request, result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(request, null, failure));
            throw failure;
        }
    }

    private void requireDecisionVersion(BookingDetails booking, long expectedVersion, String action) {
        if (booking.getVersion() != expectedVersion) {
            throw new ApiFailure(409, new ApiError("RESOURCE_CHANGED",
                    "Бронирование изменилось. Проверьте актуальные данные.").currentBooking(booking));
        }
        // ownerBooking refreshes expiry using one clock reading under this same lock.
        var requiredStatus = action.equals("cancel")
                ? BookingDetails.StatusEnum.CONFIRMED : BookingDetails.StatusEnum.PENDING;
        if (booking.getStatus() != requiredStatus
                || !clock.instant().isBefore(booking.getStartsAt().toInstant())) {
            throw new ApiFailure(409, new ApiError("ACTION_NOT_ALLOWED",
                    "Действие недоступно для текущего статуса или времени.").currentBooking(booking));
        }
    }

    public synchronized List<BookingDetails> ownerCalendar(java.time.OffsetDateTime from, java.time.OffsetDateTime to) {
        if (!from.toInstant().isBefore(to.toInstant())) {
            throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Начало диапазона должно быть раньше конца.")
                    .fieldErrors(List.of(new FieldError("to", "INVALID_VALUE", "Укажите конец позже начала."))));
        }
        refreshBookings();
        return bookings.values().stream()
                .filter(booking -> booking.getStartsAt().toInstant().isBefore(to.toInstant())
                        && from.toInstant().isBefore(booking.getEndsAt().toInstant()))
                .sorted(java.util.Comparator.comparing(booking -> booking.getStartsAt().toInstant()))
                .map(this::bookingSnapshot).toList();
    }

    public synchronized List<BookingDetails> ownerBookings(String view, String status) {
        if (!List.of("upcoming", "history").contains(view)
                || (status != null && java.util.Arrays.stream(BookingDetails.StatusEnum.values())
                        .noneMatch(value -> value.getValue().equals(status)))) {
            throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте фильтры бронирований.")
                    .fieldErrors(List.of(new FieldError(
                            List.of("upcoming", "history").contains(view) ? "status" : "view",
                            "INVALID_VALUE", "Выберите допустимый фильтр."))));
        }
        refreshBookings();
        var now = clock.instant();
        return bookings.values().stream().filter(booking -> {
            boolean upcoming = (booking.getStatus() == BookingDetails.StatusEnum.PENDING
                    || booking.getStatus() == BookingDetails.StatusEnum.CONFIRMED)
                    && booking.getStartsAt().toInstant().isAfter(now);
            return upcoming == view.equals("upcoming")
                    && (status == null || booking.getStatus().getValue().equals(status));
        }).sorted(java.util.Comparator.comparing(booking -> booking.getStartsAt().toInstant()))
                .map(this::bookingSnapshot).toList();
    }

    private void refreshBookings() {
        refreshBookings(clock.instant());
    }

    private void refreshBookings(java.time.Instant now) {
        for (var booking : bookings.values()) {
            booking.setOwnerName(name);
            booking.setOwnerTimeZone(profile().getTimeZone());
            if (booking.getStatus() == BookingDetails.StatusEnum.PENDING
                    && !now.isBefore(booking.getStartsAt().toInstant())) {
                booking.setStatus(BookingDetails.StatusEnum.REJECTED);
                booking.setRejectionReason(BookingDetails.RejectionReasonEnum.APPROVAL_EXPIRED);
                booking.setVersion(booking.getVersion() + 1);
            }
            boolean exportable = booking.getStatus() == BookingDetails.StatusEnum.PENDING
                    || booking.getStatus() == BookingDetails.StatusEnum.CONFIRMED;
            booking.setAllowedActions(!exportable ? List.of()
                    : now.isBefore(booking.getStartsAt().toInstant())
                            ? List.of("cancel", "export") : List.of("export"));
        }
    }

    private boolean occupied(java.time.Instant start, java.time.Instant end) {
        return occupied(start, end, null);
    }

    private boolean occupied(java.time.Instant start, java.time.Instant end, String excludedId) {
        refreshBookings();
        return blockClosed(start, end, null) || bookings.values().stream().anyMatch(booking ->
                !booking.getId().equals(excludedId) && (booking.getStatus() == BookingDetails.StatusEnum.PENDING
                || booking.getStatus() == BookingDetails.StatusEnum.CONFIRMED)
                && start.isBefore(booking.getEndsAt().toInstant()) && booking.getStartsAt().toInstant().isBefore(end));
    }

    private boolean blockClosed(java.time.Instant start, java.time.Instant end, String excludedId) {
        return timeBlocks.values().stream().anyMatch(block -> !block.getId().equals(excludedId)
                && start.isBefore(block.getEndsAt().toInstant()) && block.getStartsAt().toInstant().isBefore(end));
    }

    public synchronized PreparedBookingAttempt prepareAttempt(String requestedUsername,
            PrepareBookingAttemptCommand command) {
        requireUsername(requestedUsername);
        var normalizedName = normalize(command.getGuestName());
        var email = command.getGuestEmail() == null ? ""
                : command.getGuestEmail().replaceAll("^\\p{Zs}+|\\p{Zs}+$", "");
        var comment = command.getComment() == null ? "" : command.getComment();
        var errors = new ArrayList<FieldError>();
        if (command.getMeetingTypeId() == null || !command.getMeetingTypeId().matches("[A-Za-z0-9-]{1,64}")) {
            errors.add(new FieldError("meetingTypeId", "INVALID_VALUE", "Выберите тип встречи."));
        }
        if (!errors.isEmpty()) {
            throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте данные формы.").fieldErrors(errors));
        }
        var type = meetingType(command.getMeetingTypeId());
        if (!bookingEnabled) {
            throw new ApiFailure(409, new ApiError("BOOKING_DISABLED", "Запись временно недоступна"));
        }
        if (type.getArchived()) {
            throw new ApiFailure(409, new ApiError("TYPE_ARCHIVED", "Запись на этот тип встречи прекращена"));
        }
        if (command.getConditionsToken() == null || command.getConditionsToken().isBlank()) {
            errors.add(new FieldError("conditionsToken", "INVALID_VALUE", "Загрузите условия встречи."));
        }
        if (command.getStartsAt() == null) {
            errors.add(new FieldError("startsAt", "INVALID_VALUE", "Выберите время встречи."));
        }
        if (!errors.isEmpty()) {
            throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте данные формы.").fieldErrors(errors));
        }
        if (!conditionsToken(type).equals(command.getConditionsToken())) {
            throw new ApiFailure(409, new ApiError("CONDITIONS_CHANGED",
                    "Условия встречи изменились. Выберите время заново.").currentConditions(publicType(type)));
        }
        if (!printable(command.getGuestName()) || normalizedName.isEmpty()
                || normalizedName.codePointCount(0, normalizedName.length()) > 100) {
            errors.add(new FieldError("guestName", "INVALID_VALUE", "Введите имя: 1–100 печатных символов."));
        }
        if (!printable(command.getGuestEmail()) || email.codePointCount(0, email.length()) > 254
                || !validEmail(email)) {
            errors.add(new FieldError("guestEmail", "INVALID_VALUE", "Введите один email длиной до 254 символов."));
        }
        if (comment.codePointCount(0, comment.length()) > 2000
                || !comment.replace("\r\n", "\n").codePoints().allMatch(code -> code == '\n' || printable(code))) {
            errors.add(new FieldError("comment", "INVALID_VALUE",
                    "Введите до 2000 символов; разрешены переносы, но не управляющие и невидимые символы."));
        }
        if (!errors.isEmpty()) {
            throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте данные формы.").fieldErrors(errors));
        }
        var normalized = new PrepareBookingAttemptCommand(command.getMeetingTypeId(), command.getConditionsToken(),
                command.getStartsAt(), normalizedName, email).comment(comment);
        var id = randomToken();
        while (attempts.containsKey(id)) {
            id = randomToken();
        }
        var secret = randomToken();
        attempts.put(id, new PreparedAttempt(secret, requestedUsername, normalized));
        return new PreparedBookingAttempt(id, secret, PreparedBookingAttempt.StateEnum.PREPARED);
    }

    private boolean validEmail(String email) {
        int at = email.lastIndexOf('@');
        if (at < 1 || at == email.length() - 1) {
            return false;
        }
        var local = email.substring(0, at);
        if (local.codePointCount(0, local.length()) > 64 || !local.matches(
                "[\\p{L}\\p{N}!#$%&'*+/=?^_`{|}~-]+(?:\\.[\\p{L}\\p{N}!#$%&'*+/=?^_`{|}~-]+)*"
                + "|\"(?:[^\"\\\\]|\\\\.)+\"")) {
            return false;
        }
        try {
            var domain = java.net.IDN.toASCII(email.substring(at + 1), java.net.IDN.USE_STD3_ASCII_RULES);
            return domain.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String randomToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private record SavedCommand(Object request, Object result, ApiFailure failure) {
        OwnerProfile profile() {
            return (OwnerProfile) result;
        }
    }
    private record MalformedCommand(String path, String body) { }

    public OwnerState(InitialCalendarSettings initial, SlotAvailability availability, java.time.Clock clock) {
        this.initial = initial;
        this.availability = availability;
        this.clock = clock;
        bookingEnabled = initial.bookingEnabled();
        name = initial.name();
        username = initial.username();
        timeZone = initial.timeZone();
    }

    public synchronized OwnerProfile profile() {
        return new OwnerProfile(name, username, timeZone,
                bookingEnabled, version, "/booking/" + username);
    }

    public synchronized OwnerProfile updateTimeZone(String key, TimeZoneCommand command) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(command)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return saved.profile();
        }
        try {
            if (command.getTimeZone() == null
                    || !java.time.ZoneId.getAvailableZoneIds().contains(command.getTimeZone())) {
                throw invalidHours("timeZone", "Выберите валидный IANA-пояс.");
            }
            if (command.getApplyToActiveTypes() == null) {
                throw invalidHours("applyToActiveTypes", "Явно укажите true или false.");
            }
            requireTypeVersionValue(command.getExpectedVersion());
            if (version != command.getExpectedVersion()) {
                throw new ApiFailure(409, new ApiError("RESOURCE_CHANGED",
                        "Профиль изменился. Проверьте актуальные данные.").currentProfile(profile()));
            }
            var translated = new LinkedHashMap<String, MeetingType>();
            if (command.getApplyToActiveTypes()) {
                for (var current : meetingTypes.values()) {
                    if (!current.getArchived()) {
                        translated.put(current.getId(), new MeetingType(current.getId(), current.getTitle(),
                                current.getDescription(), current.getDurationMinutes(), current.getRequiresApproval(),
                                command.getTimeZone(), current.getVersion() + 1, false,
                                current.getDays(), current.getDateOverrides()));
                    }
                }
            }
            requireTimeZoneConsent(command, translated);
            timeZone = command.getTimeZone();
            version++;
            meetingTypes.putAll(translated);
            var result = profile();
            operations.put(key, new SavedCommand(command, result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(command, null, failure));
            throw failure;
        }
    }

    private record ZoneProposal(String timeZone, boolean applyToActiveTypes, long profileVersion,
            Map<String, Long> activeTypeVersions) { }
    private record ZoneImpact(ZoneProposal proposal, List<AffectedBooking> bookings) { }
    private final Map<String, ZoneImpact> zoneConfirmations = new HashMap<>();

    private void requireTimeZoneConsent(TimeZoneCommand command, Map<String, MeetingType> translated) {
        var now = clock.instant();
        refreshBookings(now);
        var activeVersions = new java.util.TreeMap<String, Long>();
        meetingTypes.values().stream().filter(type -> !type.getArchived())
                .forEach(type -> activeVersions.put(type.getId(), type.getVersion()));
        var affected = bookings.values().stream()
                .filter(booking -> booking.getStartsAt().toInstant().isAfter(now)
                        && (booking.getStatus() == BookingDetails.StatusEnum.PENDING
                        || booking.getStatus() == BookingDetails.StatusEnum.CONFIRMED))
                .filter(booking -> {
                    var type = meetingType(booking.getMeetingTypeId());
                    var proposed = translated.getOrDefault(type.getId(), type);
                    return fitsZoneAvailability(type, timeZone, booking, now)
                            && !fitsZoneAvailability(proposed, command.getTimeZone(), booking, now);
                })
                .sorted(java.util.Comparator.comparing((BookingDetails booking) -> booking.getStartsAt().toInstant())
                        .thenComparing(BookingDetails::getId))
                .map(booking -> new AffectedBooking(booking.getId(), booking.getMeetingTypeId(),
                        booking.getTitle(), booking.getGuestName(), booking.getStartsAt(), booking.getEndsAt(),
                        AffectedBooking.StatusEnum.valueOf(booking.getStatus().name()), booking.getVersion())).toList();
        var impact = new ZoneImpact(new ZoneProposal(command.getTimeZone(), command.getApplyToActiveTypes(),
                version, Map.copyOf(activeVersions)), affected);
        var token = command.getConfirmationToken();
        if ((!affected.isEmpty() || token != null) && !impact.equals(zoneConfirmations.get(token))) {
            var fresh = randomToken();
            zoneConfirmations.put(fresh, impact);
            throw new ApiFailure(409, new ApiError("IMPACT_CONFIRMATION_REQUIRED",
                    "Новый пояс изменит доступность показанных записей. "
                            + "Бронирования, блокировки и отпуска не переносятся.")
                    .affectedBookings(affected).confirmationToken(fresh));
        }
    }

    private boolean fitsZoneAvailability(MeetingType type, String ownerZone, BookingDetails booking,
            java.time.Instant now) {
        var zone = java.time.ZoneId.of(ownerZone);
        var tomorrow = now.atZone(zone).toLocalDate().plusDays(1);
        var start = booking.getStartsAt().toInstant();
        var end = booking.getEndsAt().toInstant();
        return !start.isBefore(tomorrow.atStartOfDay(zone).toInstant())
                && !end.isAfter(tomorrow.plusDays(14).atStartOfDay(zone).toInstant())
                && fitsHours(type, booking) && !occupied(start, end, booking.getId());
    }

    public synchronized MeetingType createMeetingType(String key, CreateMeetingTypeCommand command) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(command)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return (MeetingType) saved.result();
        }
        try {
            var result = newMeetingType(command, null);
            operations.put(key, new SavedCommand(command, result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(command, null, failure));
            throw failure;
        }
    }

    private record MeetingTypeEdit(String id, UpdateMeetingTypeCommand command) { }
    private record MeetingTypeArchive(String id, ArchiveMeetingTypeCommand command) { }
    private record WeeklyHoursEdit(String id, WeeklyHoursCommand command) { }
    private record DateOverrideEdit(String id, String date, Object command) { }

    private record BlockCommand(String operation, String id, Object command) { }
    private record DeletedBlock() { }
    private record BlockProposal(String operation, String id, Long version, String title,
            java.time.OffsetDateTime start, java.time.OffsetDateTime end, String zone) { }
    private record BlockImpact(BlockProposal proposal, List<AffectedBooking> bookings) { }
    private final Map<String, BlockImpact> blockConfirmations = new HashMap<>();

    public synchronized List<TimeBlock> calendarBlocks(java.time.OffsetDateTime from, java.time.OffsetDateTime to) {
        return timeBlocks.values().stream()
                .filter(block -> from.toInstant().isBefore(block.getEndsAt().toInstant())
                        && block.getStartsAt().toInstant().isBefore(to.toInstant()))
                .sorted(java.util.Comparator.comparing(block -> block.getStartsAt().toInstant())).toList();
    }

    public synchronized TimeBlockResult createBlock(String key, CreateTimeBlockCommand command) {
        return editBlock(key, "POST", null, command, command.getTitle(), command.getStartsAt(), command.getEndsAt(),
                null, command.getConfirmationToken());
    }

    public synchronized TimeBlockResult updateBlock(String key, String id, UpdateTimeBlockCommand command) {
        return editBlock(key, "PUT", id, command, command.getTitle(), command.getStartsAt(), command.getEndsAt(),
                command.getExpectedVersion(), command.getConfirmationToken());
    }

    public synchronized void deleteBlock(String key, String id, DeleteTimeBlockCommand command) {
        editBlock(key, "DELETE", id, command, null, null, null, command.getExpectedVersion(), null);
    }

    private TimeBlockResult editBlock(String key, String operation, String id, Object command, String title,
            java.time.OffsetDateTime start, java.time.OffsetDateTime end, Long expectedVersion, String token) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var request = new BlockCommand(operation, id, command);
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(request)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return saved.result() instanceof TimeBlockResult result ? result : null;
        }
        try {
            var current = id == null ? null : timeBlocks.get(id);
            if (id != null) {
                requireTypeVersionValue(expectedVersion);
                if (current == null) {
                    throw new ApiFailure(404, new ApiError("TIME_BLOCK_NOT_FOUND", "Блокировка не найдена."));
                }
                if (!current.getVersion().equals(expectedVersion)) {
                    throw new ApiFailure(409, new ApiError("RESOURCE_CHANGED",
                            "Блокировка изменилась. Проверьте актуальные данные.").currentTimeBlock(current));
                }
            }
            TimeBlockResult result = null;
            if (operation.equals("DELETE")) {
                timeBlocks.remove(id);
            } else {
                var normalized = normalize(title);
                if (normalized.isEmpty() || !printable(title)) {
                    throw invalidBlock("title", "Укажите непустое название без невидимых символов.");
                }
                requireBlockTime(start, "startsAt");
                requireBlockTime(end, "endsAt");
                if (!start.toInstant().isBefore(end.toInstant())) {
                    throw invalidBlock("endsAt", "Окончание должно быть позже начала.");
                }
                refreshBookings();
                if (blockClosed(start.toInstant(), end.toInstant(), id)
                        || bookings.values().stream().anyMatch(booking ->
                                booking.getStatus() == BookingDetails.StatusEnum.CONFIRMED
                                        && start.toInstant().isBefore(booking.getEndsAt().toInstant())
                                        && booking.getStartsAt().toInstant().isBefore(end.toInstant()))) {
                    throw new ApiFailure(409, new ApiError("TIME_CONFLICT",
                            "Время занято подтверждённой встречей или другой блокировкой."));
                }
                var block = new TimeBlock(current == null ? randomToken() : id, normalized, start, end,
                        current == null ? 1L : current.getVersion() + 1);
                var affected = bookings.values().stream()
                        .filter(booking -> booking.getStatus() == BookingDetails.StatusEnum.PENDING
                                && start.toInstant().isBefore(booking.getEndsAt().toInstant())
                                && booking.getStartsAt().toInstant().isBefore(end.toInstant()))
                        .sorted(java.util.Comparator.comparing(BookingDetails::getId))
                        .map(booking -> new AffectedBooking(booking.getId(), booking.getMeetingTypeId(),
                                booking.getTitle(), booking.getGuestName(), booking.getStartsAt(), booking.getEndsAt(),
                                AffectedBooking.StatusEnum.PENDING, booking.getVersion())).toList();
                var impact = new BlockImpact(new BlockProposal(operation, id, expectedVersion, normalized,
                        start, end, profile().getTimeZone()), affected);
                if ((!affected.isEmpty() || token != null) && !impact.equals(blockConfirmations.get(token))) {
                    var fresh = randomToken();
                    blockConfirmations.put(fresh, impact);
                    throw new ApiFailure(409, new ApiError("IMPACT_CONFIRMATION_REQUIRED",
                            "Блокировка отклонит показанные ожидающие запросы. Подтвердите актуальные последствия.")
                            .affectedBookings(affected).confirmationToken(fresh));
                }
                // Validation, hard conflicts and fresh consent precede ALL writes, under the booking monitor.
                var rejectedIds = affected.stream().map(AffectedBooking::getId).toList();
                for (var rejectedId : rejectedIds) {
                    var booking = bookings.get(rejectedId);
                    booking.setStatus(BookingDetails.StatusEnum.REJECTED);
                    booking.setVersion(booking.getVersion() + 1);
                    booking.setAllowedActions(List.of());
                }
                result = new TimeBlockResult(block, rejectedIds);
                timeBlocks.put(block.getId(), block);
            }
            operations.put(key, new SavedCommand(request, result == null ? new DeletedBlock() : result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(request, null, failure));
            throw failure;
        }
    }

    private void requireBlockTime(java.time.OffsetDateTime value, String field) {
        if (value == null || !java.time.ZoneId.of(profile().getTimeZone()).getRules()
                .getValidOffsets(value.toLocalDateTime()).contains(value.getOffset())) {
            throw invalidBlock(field,
                    "Укажите существующее местное время и допустимое UTC-смещение в текущем поясе владельца.");
        }
    }

    private ApiFailure invalidBlock(String field, String message) {
        return new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте блокировку времени.")
                .fieldErrors(List.of(new FieldError(field, "INVALID_VALUE", message))));
    }

    private record VacationCommand(String operation, String id, Object command) { }
    private record DeletedVacation() { }
    private record VacationProposal(String operation, String id, Long version,
            String startDate, String endDate, String timeZone) { }
    private record VacationImpact(VacationProposal proposal, List<AffectedBooking> bookings) { }
    private final Map<String, VacationImpact> vacationConfirmations = new HashMap<>();

    public synchronized List<OutOfOffice> vacations() {
        return List.copyOf(vacations.values());
    }

    public synchronized OutOfOffice createVacation(String key, CreateOutOfOfficeCommand command) {
        return editVacation(key, "POST", null, command, command.getStartDate(), command.getEndDate(), null,
                command.getConfirmationToken());
    }

    public synchronized OutOfOffice updateVacation(String key, String id, UpdateOutOfOfficeCommand command) {
        return editVacation(key, "PUT", id, command, command.getStartDate(), command.getEndDate(),
                command.getExpectedVersion(), command.getConfirmationToken());
    }

    public synchronized void deleteVacation(String key, String id, DeleteOutOfOfficeCommand command) {
        editVacation(key, "DELETE", id, command, null, null, command.getExpectedVersion(),
                command.getConfirmationToken());
    }

    private OutOfOffice editVacation(String key, String operation, String id, Object command,
            String startDate, String endDate, Long expectedVersion, String confirmationToken) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var request = new VacationCommand(operation, id, command);
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(request)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return saved.result() instanceof OutOfOffice value ? value : null;
        }
        try {
            var current = id == null ? null : vacations.get(id);
            if (id != null) {
                requireTypeVersionValue(expectedVersion);
                if (current == null) {
                    throw new ApiFailure(404, new ApiError("OUT_OF_OFFICE_NOT_FOUND", "Отпуск не найден."));
                }
                if (!current.getVersion().equals(expectedVersion)) {
                    throw new ApiFailure(409, new ApiError("RESOURCE_CHANGED",
                            "Отпуск изменился. Проверьте актуальные данные.").currentOutOfOffice(current));
                }
            }
            OutOfOffice result = null;
            if (!operation.equals("DELETE")) {
                var start = vacationDate(startDate, "startDate");
                var end = vacationDate(endDate, "endDate");
                if (start.isAfter(end)) {
                    throw invalidHours("endDate", "Конечная дата не может быть раньше начальной.");
                }
                result = new OutOfOffice(current == null ? randomToken() : id, startDate, endDate,
                        current == null ? profile().getTimeZone() : current.getTimeZone(),
                        current == null ? 1L : current.getVersion() + 1);
            }
            var proposal = new ArrayList<>(vacations.values().stream()
                    .filter(value -> !value.getId().equals(id)).toList());
            if (result != null) {
                proposal.add(result);
            }
            requireVacationConsent(new VacationProposal(operation, id, expectedVersion, startDate, endDate,
                    result == null ? current.getTimeZone() : result.getTimeZone()), proposal, confirmationToken);
            if (result == null) {
                vacations.remove(id);
            } else {
                vacations.put(result.getId(), result);
            }
            operations.put(key, new SavedCommand(request, result == null ? new DeletedVacation() : result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(request, null, failure));
            throw failure;
        }
    }

    private java.time.LocalDate vacationDate(String date, String field) {
        try {
            if (date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new java.time.DateTimeException("Invalid date");
            }
            return java.time.LocalDate.parse(date);
        } catch (java.time.DateTimeException exception) {
            throw invalidHours(field, "Укажите существующую дату ГГГГ-ММ-ДД.");
        }
    }

    private void requireVacationConsent(VacationProposal proposal, List<OutOfOffice> values, String token) {
        var now = clock.instant();
        refreshBookings(now);
        var affected = bookings.values().stream()
                .filter(booking -> booking.getStartsAt().toInstant().isAfter(now)
                        && (booking.getStatus() == BookingDetails.StatusEnum.PENDING
                        || booking.getStatus() == BookingDetails.StatusEnum.CONFIRMED))
                .filter(booking -> fitsHours(meetingType(booking.getMeetingTypeId()), booking)
                        && vacationClosed(booking.getStartsAt().toInstant(), booking.getEndsAt().toInstant(), values))
                .sorted(java.util.Comparator.comparing(booking -> booking.getStartsAt().toInstant()))
                .map(booking -> new AffectedBooking(booking.getId(), booking.getMeetingTypeId(),
                        booking.getTitle(), booking.getGuestName(), booking.getStartsAt(), booking.getEndsAt(),
                        AffectedBooking.StatusEnum.valueOf(booking.getStatus().name()), booking.getVersion())).toList();
        var impact = new VacationImpact(proposal, affected);
        if ((!affected.isEmpty() || token != null) && !impact.equals(vacationConfirmations.get(token))) {
            var fresh = randomToken();
            vacationConfirmations.put(fresh, impact);
            throw new ApiFailure(409, new ApiError("IMPACT_CONFIRMATION_REQUIRED",
                    "Изменение закроет время показанных бронирований. Они не будут отменены или перенесены.")
                    .affectedBookings(affected).confirmationToken(fresh));
        }
    }

    private boolean vacationClosed(java.time.Instant start, java.time.Instant end,
            java.util.Collection<OutOfOffice> values) {
        return values.stream().anyMatch(value -> {
            var zone = java.time.ZoneId.of(value.getTimeZone());
            var from = java.time.LocalDate.parse(value.getStartDate()).atStartOfDay(zone).toInstant();
            var to = java.time.LocalDate.parse(value.getEndDate()).plusDays(1).atStartOfDay(zone).toInstant();
            return from.isBefore(to) && start.isBefore(to) && from.isBefore(end);
        });
    }

    public synchronized MeetingType editDateOverride(String key, String id, String date, DateOverrideCommand command) {
        return editDateOverride(key, id, date, command, command.getExpectedVersion(), command.getIntervals(),
                command.getConfirmationToken());
    }

    public synchronized MeetingType editDateOverride(String key, String id, String date,
            DeleteDateOverrideCommand command) {
        return editDateOverride(key, id, date, command, command.getExpectedVersion(), null,
                command.getConfirmationToken());
    }

    private MeetingType editDateOverride(String key, String id, String date, Object command, Long expectedVersion,
            List<WorkingInterval> intervals, String confirmationToken) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var request = new DateOverrideEdit(id, date, command);
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(request)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return (MeetingType) saved.result();
        }
        try {
            requireTypeVersionValue(expectedVersion);
            var current = meetingType(id);
            requireTypeVersion(current, expectedVersion);
            try {
                if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                    throw new java.time.DateTimeException("Invalid date format");
                }
                java.time.LocalDate.parse(date);
            } catch (java.time.DateTimeException exception) {
                throw invalidHours("date", "Укажите существующую дату ГГГГ-ММ-ДД в поясе типа.");
            }
            var overrides = new ArrayList<>(current.getDateOverrides().stream()
                    .filter(value -> !value.getDate().equals(date)).toList());
            if (command instanceof DateOverrideCommand) {
                var canonical = canonicalIntervals(intervals, "intervals");
                overrides.add(new DateOverride(date, canonical));
            }
            overrides.sort(java.util.Comparator.comparing(DateOverride::getDate));
            var result = new MeetingType(current.getId(), current.getTitle(), current.getDescription(),
                    current.getDurationMinutes(), current.getRequiresApproval(), current.getTimeZone(),
                    current.getVersion() + 1, false, current.getDays(), overrides);
            var proposal = new DateProposal(command instanceof DateOverrideCommand ? "PUT" : "DELETE", date,
                    overrides.stream().filter(value -> value.getDate().equals(date))
                            .map(DateOverride::getIntervals).findFirst().orElse(List.of()));
            requireAvailabilityConsent(current, result, proposal, confirmationToken);
            meetingTypes.put(id, result);
            operations.put(key, new SavedCommand(request, result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(request, null, failure));
            throw failure;
        }
    }

    private record DateProposal(String operation, String date, List<WorkingInterval> intervals) { }
    private record HoursImpact(String id, long version, Object proposal, List<AffectedBooking> bookings) { }
    private final Map<String, HoursImpact> hoursConfirmations = new HashMap<>();

    public synchronized MeetingType updateWeeklyHours(String key, String id, WeeklyHoursCommand command) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var request = new WeeklyHoursEdit(id, command);
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(request)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return (MeetingType) saved.result();
        }
        try {
            requireTypeVersionValue(command.getExpectedVersion());
            var current = meetingType(id);
            requireTypeVersion(current, command.getExpectedVersion());
            var days = canonicalDays(command.getDays());
            var result = new MeetingType(current.getId(), current.getTitle(), current.getDescription(),
                    current.getDurationMinutes(), current.getRequiresApproval(), current.getTimeZone(),
                    current.getVersion() + 1, false, days, current.getDateOverrides());
            requireAvailabilityConsent(current, result, days, command.getConfirmationToken());
            meetingTypes.put(id, result);
            operations.put(key, new SavedCommand(request, result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(request, null, failure));
            throw failure;
        }
    }

    private void requireAvailabilityConsent(MeetingType current, MeetingType result, Object proposal,
            String confirmationToken) {
        // Expiry, impact comparison, token verification and save share the owner-state monitor.
        // Owner decision #27: only NEW invalidity for active bookings strictly in the future.
        var now = clock.instant();
        refreshBookings(now);
        var affected = bookings.values().stream()
                .filter(booking -> booking.getMeetingTypeId().equals(current.getId())
                        && booking.getStartsAt().toInstant().isAfter(now)
                        && (booking.getStatus() == BookingDetails.StatusEnum.PENDING
                        || booking.getStatus() == BookingDetails.StatusEnum.CONFIRMED))
                .filter(booking -> fitsHours(current, booking) && !fitsHours(result, booking))
                .sorted(java.util.Comparator.comparing(booking -> booking.getStartsAt().toInstant()))
                .map(booking -> new AffectedBooking(booking.getId(), booking.getMeetingTypeId(),
                        booking.getTitle(), booking.getGuestName(), booking.getStartsAt(), booking.getEndsAt(),
                        AffectedBooking.StatusEnum.valueOf(booking.getStatus().name()), booking.getVersion()))
                .toList();
        var impact = new HoursImpact(current.getId(), current.getVersion(), proposal, affected);
        if ((!affected.isEmpty() || confirmationToken != null)
                && !impact.equals(hoursConfirmations.get(confirmationToken))) {
            var token = randomToken();
            hoursConfirmations.put(token, impact);
            throw new ApiFailure(409, new ApiError("IMPACT_CONFIRMATION_REQUIRED",
                    "Изменение закроет время показанных бронирований. Они не будут отменены или перенесены.")
                    .affectedBookings(affected).confirmationToken(token));
        }
    }

    private boolean fitsHours(MeetingType type, BookingDetails booking) {
        return !vacationClosed(booking.getStartsAt().toInstant(), booking.getEndsAt().toInstant(), vacations.values())
                && !availability.available(type, booking.getStartsAt().toInstant(), booking.getEndsAt().toInstant(),
                booking.getDurationMinutes()).isEmpty();
    }

    private List<WorkingDay> canonicalDays(List<WorkingDay> days) {
        var result = new ArrayList<WorkingDay>();
        var seen = new java.util.HashSet<WorkingDay.DayEnum>();
        if (days == null) {
            throw invalidHours("days", "Укажите дни недели.");
        }
        for (int i = 0; i < days.size(); i++) {
            var day = days.get(i);
            if (day == null || day.getDay() == null || !seen.add(day.getDay()) || day.getIntervals() == null) {
                throw invalidHours("days[" + i + "]", "Укажите уникальный день и его интервалы.");
            }
            result.add(new WorkingDay(day.getDay(), canonicalIntervals(day.getIntervals(),
                    "days[" + i + "].intervals")));
        }
        var ordered = new ArrayList<WorkingDay>();
        for (var day : WorkingDay.DayEnum.values()) {
            ordered.add(result.stream().filter(value -> value.getDay() == day).findFirst()
                    .orElseGet(() -> new WorkingDay(day, List.of())));
        }
        return ordered;
    }

    private List<WorkingInterval> canonicalIntervals(List<WorkingInterval> intervals, String field) {
        if (intervals == null) {
            throw invalidHours(field, "Укажите массив интервалов; пустой массив закрывает день.");
        }
        for (int i = 0; i < intervals.size(); i++) {
            var interval = intervals.get(i);
            if (interval == null || !validTime(interval.getStart(), false)
                    || !validTime(interval.getEnd(), true)
                    || interval.getStart().compareTo(interval.getEnd()) >= 0) {
                throw invalidHours(field + "[" + i + "]",
                        "Введите интервал ЧЧ:ММ от 00:00 до 24:00; начало раньше конца, 24:00 только конец.");
            }
        }
        var merged = new ArrayList<WorkingInterval>();
        for (var interval : intervals.stream()
                .sorted(java.util.Comparator.comparing(WorkingInterval::getStart)).toList()) {
            if (merged.isEmpty() || merged.getLast().getEnd().compareTo(interval.getStart()) < 0) {
                merged.add(new WorkingInterval(interval.getStart(), interval.getEnd()));
            } else if (merged.getLast().getEnd().compareTo(interval.getEnd()) < 0) {
                merged.getLast().setEnd(interval.getEnd());
            }
        }
        return merged;
    }

    private boolean validTime(String value, boolean end) {
        return value != null && (value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
                || end && value.equals("24:00"));
    }

    private ApiFailure invalidHours(String field, String message) {
        return new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте рабочие часы.")
                .fieldErrors(List.of(new FieldError(field, "INVALID_VALUE", message))));
    }

    public synchronized MeetingType archiveMeetingType(String key, String id, ArchiveMeetingTypeCommand command) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var request = new MeetingTypeArchive(id, command);
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(request)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return (MeetingType) saved.result();
        }
        try {
            requireTypeVersionValue(command.getExpectedVersion());
            var current = meetingType(id);
            requireTypeVersion(current, command.getExpectedVersion());
            // Replace rather than mutate: earlier operation outcomes remain immutable snapshots.
            var result = new MeetingType(current.getId(), current.getTitle(), current.getDescription(),
                    current.getDurationMinutes(), current.getRequiresApproval(), current.getTimeZone(),
                    current.getVersion() + 1, true, current.getDays(), current.getDateOverrides());
            meetingTypes.put(id, result);
            operations.put(key, new SavedCommand(request, result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(request, null, failure));
            throw failure;
        }
    }

    public synchronized MeetingType updateMeetingType(String key, String id, UpdateMeetingTypeCommand command) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var request = new MeetingTypeEdit(id, command);
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(request)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return (MeetingType) saved.result();
        }
        try {
            requireTypeVersionValue(command.getExpectedVersion());
            var current = meetingType(id);
            requireTypeVersion(current, command.getExpectedVersion());
            var result = newMeetingType(new CreateMeetingTypeCommand(id, command.getTitle(),
                    command.getDescription(), command.getDurationMinutes())
                    .requiresApproval(command.getRequiresApproval()), current);
            operations.put(key, new SavedCommand(request, result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(request, null, failure));
            throw failure;
        }
    }

    private void requireTypeVersionValue(Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 1) {
            throw new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте данные запроса.")
                    .fieldErrors(List.of(new FieldError("expectedVersion", "INVALID_VALUE",
                            "Укажите целую версию не меньше 1."))));
        }
    }

    private void requireTypeVersion(MeetingType current, long expectedVersion) {
        if (current.getVersion() != expectedVersion) {
            throw new ApiFailure(409, new ApiError("RESOURCE_CHANGED",
                    "Тип встречи изменился. Проверьте актуальные данные.").currentMeetingType(current));
        }
        if (current.getArchived()) {
            throw new ApiFailure(409, new ApiError("ACTION_NOT_ALLOWED",
                    "Архивный тип нельзя редактировать или восстановить.").currentMeetingType(current));
        }
    }

    private MeetingType newMeetingType(CreateMeetingTypeCommand command, MeetingType current) {
        var title = normalize(command.getTitle());
        var rawDescription = command.getDescription() == null ? "" : command.getDescription().replace("\r\n", "\n");
        var description = java.util.Arrays.stream(rawDescription.split("\n", -1))
                .map(line -> line.replaceAll("\\p{Zs}+", " ").replaceAll("^ +| +$", ""))
                .collect(java.util.stream.Collectors.joining("\n")).replaceAll("^\\n+|\\n+$", "");
        var errors = new ArrayList<FieldError>();
        if (command.getId() == null || !command.getId().matches("[A-Za-z0-9-]{1,64}")) {
            errors.add(new FieldError("id", "INVALID_VALUE", "Введите 1–64 латинских букв, цифр или дефисов."));
        }
        if (!printable(command.getTitle()) || title.isEmpty()) {
            errors.add(new FieldError("title", "INVALID_VALUE", "Введите непустое печатное название."));
        }
        long descriptionLength = 0;
        for (var line : description.split("\n", -1)) {
            var visible = new StringBuilder();
            line.codePoints().filter(OwnerState::printable).forEach(visible::appendCodePoint);
            var counted = normalize(visible.toString());
            descriptionLength += counted.codePointCount(0, counted.length());
        }
        if (descriptionLength < 20) {
            errors.add(new FieldError("description", "INVALID_VALUE",
                    "Введите минимум 20 символов без учёта переносов."));
        }
        if (command.getDurationMinutes() == null || command.getDurationMinutes() < 1
                || command.getDurationMinutes() > 480) {
            errors.add(new FieldError("durationMinutes", "INVALID_VALUE", "Введите целое число от 1 до 480 минут."));
        }
        if (command.getRequiresApproval() == null) {
            errors.add(new FieldError("requiresApproval", "INVALID_VALUE", "Укажите true или false."));
        }
        if (!errors.isEmpty()) {
            throw new ApiFailure(422,
                    new ApiError("VALIDATION_FAILED", "Проверьте данные запроса.").fieldErrors(errors));
        }
        if (current == null && meetingTypes.containsKey(command.getId())
                || meetingTypes.values().stream().anyMatch(type ->
                !type.getId().equals(command.getId()) && !type.getArchived() && type.getTitle().equalsIgnoreCase(title)
                && type.getDurationMinutes().equals(command.getDurationMinutes()))) {
            throw new ApiFailure(409, new ApiError("DUPLICATE_MEETING_TYPE",
                    "Тип с таким ID или названием и длительностью уже существует."));
        }
        var result = new MeetingType(command.getId(), title, description,
                command.getDurationMinutes(), command.getRequiresApproval(),
                current == null ? profile().getTimeZone() : current.getTimeZone(),
                current == null ? 1L : current.getVersion() + 1, false,
                current == null ? java.util.Arrays.stream(DayOfWeek.values()).map(day -> new WorkingDay(
                        WorkingDay.DayEnum.valueOf(day.name()), initial.workingDays().contains(day)
                         ? List.of(new WorkingInterval(initial.workStart(), initial.workEnd())) : List.of())).toList()
                        : current.getDays(),
                current == null ? List.of() : current.getDateOverrides());
        meetingTypes.put(command.getId(), result);
        return result;
    }

    public synchronized List<MeetingType> meetingTypes() {
        return List.copyOf(meetingTypes.values());
    }

    public synchronized MeetingType meetingType(String id) {
        var type = meetingTypes.get(id);
        if (type == null) {
            throw new ApiFailure(404, new ApiError("PAGE_NOT_FOUND", "Страница не найдена"));
        }
        return type;
    }

    public synchronized PublicBookingPage publicPage(String requestedUsername) {
        requireUsername(requestedUsername);
        var page = new PublicBookingPage(name, profile().getTimeZone(), bookingEnabled
                ? PublicBookingPage.StateEnum.OPEN : PublicBookingPage.StateEnum.BOOKING_DISABLED);
        page.setItems(bookingEnabled ? meetingTypes.values().stream().filter(type -> !type.getArchived())
                .map(this::publicType).toList() : null);
        return page;
    }

    public synchronized PublicMeetingTypePage publicMeetingType(String requestedUsername, String id) {
        requireUsername(requestedUsername);
        var type = meetingType(id);
        var page = new PublicMeetingTypePage(name, profile().getTimeZone(), bookingEnabled
                ? type.getArchived() ? PublicMeetingTypePage.StateEnum.TYPE_ARCHIVED
                    : PublicMeetingTypePage.StateEnum.OPEN : PublicMeetingTypePage.StateEnum.BOOKING_DISABLED);
        if (bookingEnabled && !type.getArchived()) {
            page.setMeetingType(publicType(type));
        }
        return page;
    }

    public synchronized PublicSlots publicSlots(String requestedUsername, String id, SlotAvailability availability) {
        requireUsername(requestedUsername);
        var type = meetingType(id);
        if (!bookingEnabled) {
            throw new ApiFailure(409, new ApiError("BOOKING_DISABLED", "Запись временно недоступна"));
        }
        if (type.getArchived()) {
            throw new ApiFailure(409, new ApiError("TYPE_ARCHIVED", "Запись на этот тип встречи прекращена"));
        }
        var slots = availability.slots(type, profile().getTimeZone(), conditionsToken(type));
        slots.setItems(slots.getItems().stream().filter(slot -> !vacationClosed(
                slot.getStartsAt().toInstant(), slot.getEndsAt().toInstant(), vacations.values()))
                .filter(slot -> !occupied(
                slot.getStartsAt().toInstant(), slot.getEndsAt().toInstant())).toList());
        return slots;
    }

    private void requireUsername(String requestedUsername) {
        if (!username.equals(requestedUsername)) {
            throw new ApiFailure(404, new ApiError("PAGE_NOT_FOUND", "Страница не найдена"));
        }
    }

    private PublicMeetingType publicType(MeetingType type) {
        return new PublicMeetingType(type.getId(), type.getTitle(), type.getDescription(), type.getDurationMinutes(),
                type.getRequiresApproval(), type.getTimeZone(), conditionsToken(type));
    }

    private String conditionsToken(MeetingType type) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String field : List.of(type.getTitle(), type.getDescription(), type.getDurationMinutes().toString(),
                    type.getRequiresApproval().toString())) {
                var bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    public synchronized OwnerProfile updateProfile(String key, ProfileCommand command) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(command)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return saved.profile();
        }
        var normalizedName = normalize(command.getName());
        var trimmedUsername = normalize(command.getUsername());
        var normalizedUsername = trimmedUsername.toLowerCase(Locale.ROOT);
        var errors = new ArrayList<FieldError>();
        if (!printable(command.getName()) || normalizedName.codePointCount(0, normalizedName.length()) < 1
                || normalizedName.codePointCount(0, normalizedName.length()) > 100) {
            errors.add(new FieldError("name", "INVALID_VALUE",
                    "Введите 1–100 печатных символов без управляющих и невидимых символов."));
        }
        if (!printable(command.getUsername()) || !trimmedUsername.matches("[A-Za-z0-9][A-Za-z0-9-]{1,28}[A-Za-z0-9]")) {
            errors.add(new FieldError("username", "INVALID_VALUE",
                    "Введите 3–30 латинских букв, цифр или дефисов; края — буква или цифра."));
        }
        if (command.getExpectedVersion() == null || command.getExpectedVersion() < 1) {
            errors.add(new FieldError("expectedVersion", "INVALID_VALUE", "Укажите целую версию не меньше 1."));
        }
        if (!errors.isEmpty()) {
            var failure = new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте данные запроса.")
                    .fieldErrors(errors));
            operations.put(key, new SavedCommand(command, null, failure));
            throw failure;
        }
        if (version != command.getExpectedVersion()) {
            var failure = new ApiFailure(409,
                    new ApiError("RESOURCE_CHANGED", "Профиль изменился. Проверьте актуальные данные.")
                    .currentProfile(profile()));
            operations.put(key, new SavedCommand(command, null, failure));
            throw failure;
        }
        name = normalizedName;
        username = normalizedUsername;
        version++;
        var result = profile();
        operations.put(key, new SavedCommand(command, result, null));
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\p{Zs}+", " ").strip();
    }

    private static boolean printable(String value) {
        return value != null && value.codePoints().allMatch(OwnerState::printable);
    }

    private static boolean printable(int code) {
        return !Character.isISOControl(code) && Character.getType(code) != Character.FORMAT && !invisible(code)
                && Character.getType(code) != Character.SURROGATE
                && Character.getType(code) != Character.UNASSIGNED
                && Character.getType(code) != Character.LINE_SEPARATOR
                && Character.getType(code) != Character.PARAGRAPH_SEPARATOR;
    }

    // Default-ignorable characters that are not already format or unassigned characters.
    private static boolean invisible(int code) {
        return code == 0x034f || code >= 0x115f && code <= 0x1160
                || code >= 0x17b4 && code <= 0x17b5 || code >= 0x180b && code <= 0x180d
                || code == 0x180f || code == 0x3164 || code >= 0xfe00 && code <= 0xfe0f
                || code == 0xffa0 || code >= 0xe0100 && code <= 0xe01ef;
    }

    public synchronized OwnerProfile setPublicBooking(String key, PublicBookingCommand command) {
        if (!validKey(key)) {
            throw invalidKey();
        }
        var saved = operations.get(key);
        if (saved != null) {
            if (!saved.request().equals(command)) {
                throw new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                        "Этот ключ уже использован для другого запроса."));
            }
            if (saved.failure() != null) {
                throw saved.failure();
            }
            return saved.profile();
        }
        var errors = new ArrayList<FieldError>();
        if (command.getEnabled() == null) {
            errors.add(new FieldError("enabled", "INVALID_VALUE", "Укажите true или false."));
        }
        if (command.getExpectedVersion() == null || command.getExpectedVersion() < 1) {
            errors.add(new FieldError("expectedVersion", "INVALID_VALUE", "Укажите целую версию не меньше 1."));
        }
        if (!errors.isEmpty()) {
            var failure = new ApiFailure(422, new ApiError("VALIDATION_FAILED", "Проверьте данные запроса.")
                    .fieldErrors(errors));
            operations.put(key, new SavedCommand(command, null, failure));
            throw failure;
        }
        if (version != command.getExpectedVersion()) {
            var failure = new ApiFailure(409,
                    new ApiError("RESOURCE_CHANGED", "Профиль изменился. Проверьте актуальные данные.")
                    .currentProfile(profile()));
            operations.put(key, new SavedCommand(command, null, failure));
            throw failure;
        }
        bookingEnabled = command.getEnabled();
        version++;
        var result = profile();
        operations.put(key, new SavedCommand(command, result, null));
        return result;
    }

    public synchronized OwnerOperationResult operation(String key) {
        var saved = operations.get(key);
        if (saved == null) {
            throw new ApiFailure(410, new ApiError("OPERATION_UNAVAILABLE",
                    "Результат операции недоступен. Возможен сброс данных."));
        }
        if (saved.failure() != null) {
            return new OwnerOperationResult(saved.failure().status()).error(saved.failure().error());
        }
        if (saved.result() instanceof MeetingType type) {
            return new OwnerOperationResult(saved.request() instanceof CreateMeetingTypeCommand ? 201 : 200)
                    .meetingType(type);
        }
        if (saved.result() instanceof OutOfOffice value) {
            return new OwnerOperationResult(((VacationCommand) saved.request()).operation().equals("POST") ? 201 : 200)
                    .outOfOffice(value);
        }
        if (saved.result() instanceof TimeBlockResult result) {
            return new OwnerOperationResult(((BlockCommand) saved.request()).operation().equals("POST") ? 201 : 200)
                    .timeBlockResult(result);
        }
        if (saved.result() instanceof DeletedBlock) {
            return new OwnerOperationResult(204);
        }
        if (saved.result() instanceof DeletedVacation) {
            return new OwnerOperationResult(204);
        }
        if (saved.result() instanceof BookingDetails booking) {
            return new OwnerOperationResult(200).booking(bookingSnapshot(booking));
        }
        return new OwnerOperationResult(200).profile(saved.profile());
    }

    public synchronized ApiFailure malformedResult(String key, String path, String body) {
        var malformed = new ApiFailure(400, new ApiError("MALFORMED_REQUEST", "Некорректный формат запроса."));
        if (!validKey(key)) {
            return invalidKey();
        }
        var saved = operations.get(key);
        var command = new MalformedCommand(path, body);
        if (saved != null) {
            if (saved.request().equals(command)) {
                return saved.failure();
            }
            return new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                    "Этот ключ уже использован для другого запроса."));
        }
        operations.put(key, new SavedCommand(command, null, malformed));
        return malformed;
    }

    private boolean validKey(String key) {
        return key != null && key.matches("[A-Za-z0-9_-]{1,128}");
    }

    private ApiFailure invalidKey() {
        return new ApiFailure(400, new ApiError("MALFORMED_REQUEST", "Некорректный Idempotency-Key."));
    }
}
