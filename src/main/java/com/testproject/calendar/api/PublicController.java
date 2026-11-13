package com.testproject.calendar.api;

import com.testproject.calendar.generated.api.PublicApi;
import com.testproject.calendar.generated.model.BookingEntry;
import com.testproject.calendar.generated.model.PrepareBookingAttemptCommand;
import com.testproject.calendar.generated.model.PreparedBookingAttempt;
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

    public PublicController(OwnerState owner, SlotAvailability availability) {
        this.owner = owner;
        this.availability = availability;
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
