package com.testproject.calendar.owner;

import com.testproject.calendar.api.ApiFailure;
import com.testproject.calendar.generated.model.ApiError;
import com.testproject.calendar.generated.model.FieldError;
import com.testproject.calendar.generated.model.OwnerProfile;
import com.testproject.calendar.generated.model.OwnerOperationResult;
import com.testproject.calendar.generated.model.PublicBookingCommand;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OwnerState {

    private final InitialCalendarSettings initial;
    private boolean bookingEnabled;
    private long version = 1;
    private final Map<String, SavedCommand> operations = new HashMap<>();

    private record SavedCommand(Object request, OwnerProfile profile, ApiFailure failure) { }

    public OwnerState(InitialCalendarSettings initial) {
        this.initial = initial;
        bookingEnabled = initial.bookingEnabled();
    }

    public synchronized OwnerProfile profile() {
        return new OwnerProfile(initial.name(), initial.username(), initial.timeZone(),
                bookingEnabled, version, "/booking/" + initial.username());
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

    public synchronized ApiFailure malformedResult(String key, String body) {
        var malformed = new ApiFailure(400, new ApiError("MALFORMED_REQUEST", "Некорректный формат запроса."));
        if (!validKey(key)) {
            return invalidKey();
        }
        var saved = operations.get(key);
        if (saved != null) {
            if (saved.request().equals(body)) {
                return saved.failure();
            }
            return new ApiFailure(409, new ApiError("IDEMPOTENCY_KEY_REUSED",
                    "Этот ключ уже использован для другого запроса."));
        }
        operations.put(key, new SavedCommand(body, null, malformed));
        return malformed;
    }

    private boolean validKey(String key) {
        return key != null && key.matches("[A-Za-z0-9_-]{1,128}");
    }

    private ApiFailure invalidKey() {
        return new ApiFailure(400, new ApiError("MALFORMED_REQUEST", "Некорректный Idempotency-Key."));
    }
}
