package com.testproject.calendar.api;

import com.testproject.calendar.generated.api.PublicApi;
import com.testproject.calendar.generated.model.BookingEntry;
import com.testproject.calendar.generated.model.PrepareBookingAttemptCommand;
import com.testproject.calendar.generated.model.PreparedBookingAttempt;
import com.testproject.calendar.generated.model.SubmittedBookingAttempt;
import com.testproject.calendar.generated.model.BookingDetails;
import com.testproject.calendar.generated.model.PublicBookingPage;
import com.testproject.calendar.generated.model.PublicMeetingTypePage;
import com.testproject.calendar.generated.model.PublicSlots;
import com.testproject.calendar.availability.SlotAvailability;
import com.testproject.calendar.owner.OwnerState;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicController implements PublicApi {

    private final OwnerState owner;
    private final SlotAvailability availability;
    private final jakarta.servlet.http.HttpServletRequest request;

    public PublicController(OwnerState owner, SlotAvailability availability,
            jakarta.servlet.http.HttpServletRequest request) {
        this.owner = owner;
        this.availability = availability;
        this.request = request;
    }

    @Override
    public ResponseEntity<BookingDetails> getBookingResult(String accessToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.bookingResult(accessToken));
    }

    @Override
    public ResponseEntity<SubmittedBookingAttempt> submitBookingAttempt(String attemptId, String authorization) {
        try {
            if (request.getInputStream().read() != -1) {
                throw new ApiFailure(400, new com.testproject.calendar.generated.model.ApiError(
                        "MALFORMED_REQUEST", "Отправка попытки не принимает тело запроса."));
            }
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        var submission = owner.submitAttempt(attemptId, authorization);
        return ResponseEntity.status(submission.created() ? 201 : 200).cacheControl(CacheControl.noStore())
                .body(submission.result());
    }

    @Override
    public ResponseEntity<PreparedBookingAttempt> prepareBookingAttempt(String username,
            PrepareBookingAttemptCommand command) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(owner.prepareAttempt(username, command));
    }

    @Override
    public ResponseEntity<BookingEntry> getBookingEntry() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new BookingEntry(owner.profile().getBookingUrl()));
    }

    @Override
    public ResponseEntity<PublicBookingPage> getPublicBookingPage(String username) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.publicPage(username));
    }

    @Override
    public ResponseEntity<PublicMeetingTypePage> getPublicMeetingType(String username, String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.publicMeetingType(username, id));
    }

    @Override
    public ResponseEntity<PublicSlots> getPublicSlots(String username, String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.publicSlots(username, id, availability));
    }
}
