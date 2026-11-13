package com.testproject.calendar.owner;

import com.testproject.calendar.api.ApiFailure;
import com.testproject.calendar.generated.model.ApiError;
import com.testproject.calendar.generated.model.FieldError;
import com.testproject.calendar.generated.model.OwnerProfile;
import com.testproject.calendar.generated.model.OwnerOperationResult;
import com.testproject.calendar.generated.model.PublicBookingCommand;
import com.testproject.calendar.generated.model.ProfileCommand;
import java.util.Locale;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OwnerState {

    private final InitialCalendarSettings initial;
    private boolean bookingEnabled;
    private String name;
    private String username;
    private long version = 1;
    private final Map<String, SavedCommand> operations = new HashMap<>();

    private record SavedCommand(Object request, OwnerProfile profile, ApiFailure failure) { }
    private record MalformedCommand(String path, String body) { }

    public OwnerState(InitialCalendarSettings initial) {
        this.initial = initial;
        bookingEnabled = initial.bookingEnabled();
        name = initial.name();
        username = initial.username();
    }

    public synchronized OwnerProfile profile() {
        return new OwnerProfile(name, username, initial.timeZone(),
                bookingEnabled, version, "/booking/" + username);
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
        return value != null && value.codePoints().noneMatch(code -> Character.isISOControl(code)
                || Character.getType(code) == Character.FORMAT || invisible(code)
                || Character.getType(code) == Character.SURROGATE
                || Character.getType(code) == Character.UNASSIGNED
                || Character.getType(code) == Character.LINE_SEPARATOR
                || Character.getType(code) == Character.PARAGRAPH_SEPARATOR);
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
