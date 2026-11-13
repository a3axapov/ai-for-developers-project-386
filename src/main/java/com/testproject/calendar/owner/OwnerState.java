package com.testproject.calendar.owner;

import com.testproject.calendar.api.ApiFailure;
import com.testproject.calendar.generated.model.PrepareBookingAttemptCommand;
import com.testproject.calendar.generated.model.PreparedBookingAttempt;
import com.testproject.calendar.generated.model.BookingDetails;
import com.testproject.calendar.generated.model.SubmittedBookingAttempt;
import com.testproject.calendar.availability.SlotAvailability;
import com.testproject.calendar.generated.model.PublicSlots;
import com.testproject.calendar.generated.model.ApiError;
import com.testproject.calendar.generated.model.FieldError;
import com.testproject.calendar.generated.model.OwnerProfile;
import com.testproject.calendar.generated.model.OwnerOperationResult;
import com.testproject.calendar.generated.model.PublicBookingCommand;
import com.testproject.calendar.generated.model.ProfileCommand;
import com.testproject.calendar.generated.model.MeetingType;
import com.testproject.calendar.generated.model.CreateMeetingTypeCommand;
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
    private long version = 1;
    private final Map<String, SavedCommand> operations = new HashMap<>();
    private final Map<String, MeetingType> meetingTypes = new LinkedHashMap<>();
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

    public synchronized Submission submitAttempt(String id, String authorization) {
        var attempt = attempts.get(id);
        if (attempt == null || authorization == null || !MessageDigest.isEqual(
                ("Bearer " + attempt.secret).getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiFailure(410, new ApiError("ATTEMPT_UNAVAILABLE",
                    "Попытка недоступна. Возможен сброс данных."));
        }
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
                    "Условия встречи изменились. Выберите время заново."));
        }
        var slots = availability.slots(type, profile().getTimeZone(), conditionsToken(type));
        var start = attempt.startsAt.toInstant();
        var end = start.plusSeconds(type.getDurationMinutes() * 60L);
        if (start.isBefore(slots.getWindowStart().toInstant()) || end.isAfter(slots.getWindowEnd().toInstant())) {
            throw new ApiFailure(409, new ApiError("OUTSIDE_BOOKING_WINDOW",
                    "Окно записи изменилось. Выберите время заново."));
        }
        if (slots.getItems().stream().noneMatch(slot -> slot.getStartsAt().toInstant().equals(start))) {
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
                attempt.guestName, attempt.guestEmail, attempt.comment, 1L, List.of());
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

    private void refreshBookings() {
        var now = clock.instant();
        for (var booking : bookings.values()) {
            booking.setOwnerName(name);
            booking.setOwnerTimeZone(profile().getTimeZone());
            if (booking.getStatus() == BookingDetails.StatusEnum.PENDING
                    && !now.isBefore(booking.getStartsAt().toInstant())) {
                booking.setStatus(BookingDetails.StatusEnum.REJECTED);
                booking.setRejectionReason(BookingDetails.RejectionReasonEnum.APPROVAL_EXPIRED);
                booking.setVersion(booking.getVersion() + 1);
            }
        }
    }

    private boolean occupied(java.time.Instant start, java.time.Instant end) {
        refreshBookings();
        return bookings.values().stream().anyMatch(booking ->
                (booking.getStatus() == BookingDetails.StatusEnum.PENDING
                || booking.getStatus() == BookingDetails.StatusEnum.CONFIRMED)
                && start.isBefore(booking.getEndsAt().toInstant()) && booking.getStartsAt().toInstant().isBefore(end));
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
                    "Условия встречи изменились. Выберите время заново."));
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
    }

    public synchronized OwnerProfile profile() {
        return new OwnerProfile(name, username, initial.timeZone(),
                bookingEnabled, version, "/booking/" + username);
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
            var result = newMeetingType(command);
            operations.put(key, new SavedCommand(command, result, null));
            return result;
        } catch (ApiFailure failure) {
            operations.put(key, new SavedCommand(command, null, failure));
            throw failure;
        }
    }

    private MeetingType newMeetingType(CreateMeetingTypeCommand command) {
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
        if (meetingTypes.containsKey(command.getId()) || meetingTypes.values().stream().anyMatch(type ->
                !type.getArchived() && type.getTitle().equalsIgnoreCase(title)
                && type.getDurationMinutes().equals(command.getDurationMinutes()))) {
            throw new ApiFailure(409, new ApiError("DUPLICATE_MEETING_TYPE",
                    "Тип с таким ID или названием и длительностью уже существует."));
        }
        var result = new MeetingType(command.getId(), title, description,
                command.getDurationMinutes(), command.getRequiresApproval(), profile().getTimeZone(), 1L, false,
                java.util.Arrays.stream(DayOfWeek.values()).map(day -> new WorkingDay(
                        WorkingDay.DayEnum.valueOf(day.name()), initial.workingDays().contains(day)
                        ? List.of(new WorkingInterval(initial.workStart(), initial.workEnd())) : List.of())).toList(),
                List.of());
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
                ? PublicMeetingTypePage.StateEnum.OPEN : PublicMeetingTypePage.StateEnum.BOOKING_DISABLED);
        if (bookingEnabled) {
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
        slots.setItems(slots.getItems().stream().filter(slot -> !occupied(
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
            return new OwnerOperationResult(201).meetingType(type);
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
