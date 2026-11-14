package com.testproject.calendar.api;

import com.testproject.calendar.generated.api.OwnerApi;
import com.testproject.calendar.generated.model.OwnerProfile;
import com.testproject.calendar.generated.model.OwnerOperationResult;
import com.testproject.calendar.generated.model.PublicBookingCommand;
import com.testproject.calendar.generated.model.ProfileCommand;
import com.testproject.calendar.generated.model.CreateMeetingTypeCommand;
import com.testproject.calendar.generated.model.MeetingType;
import com.testproject.calendar.generated.model.MeetingTypeList;
import com.testproject.calendar.generated.model.OwnerBookingList;
import com.testproject.calendar.generated.model.BookingDetails;
import com.testproject.calendar.owner.OwnerState;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OwnerController implements OwnerApi {

    private final OwnerState owner;

    public OwnerController(OwnerState owner) {
        this.owner = owner;
    }

    @Override
    public ResponseEntity<OwnerBookingList> listOwnerBookings(String view, String status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new OwnerBookingList(owner.ownerBookings(view, status)));
    }

    @Override
    public ResponseEntity<BookingDetails> getOwnerBooking(String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.ownerBooking(id));
    }

    @Override
    public ResponseEntity<OwnerBookingList> getOwnerCalendar(
            java.time.OffsetDateTime from, java.time.OffsetDateTime to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new OwnerBookingList(owner.ownerCalendar(from, to)));
    }

    @Override
    public ResponseEntity<MeetingType> createMeetingType(String idempotencyKey, CreateMeetingTypeCommand command) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(owner.createMeetingType(idempotencyKey, command));
    }

    @Override
    public ResponseEntity<MeetingTypeList> listMeetingTypes() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MeetingTypeList(owner.meetingTypes()));
    }

    @Override
    public ResponseEntity<MeetingType> getMeetingType(String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.meetingType(id));
    }

    @Override
    public ResponseEntity<OwnerProfile> getOwnerProfile() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.profile());
    }

    @Override
    public ResponseEntity<OwnerProfile> setPublicBooking(String idempotencyKey, PublicBookingCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.setPublicBooking(idempotencyKey, command));
    }

    @Override
    public ResponseEntity<OwnerProfile> updateOwnerProfile(String idempotencyKey, ProfileCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.updateProfile(idempotencyKey, command));
    }

    @Override
    public ResponseEntity<OwnerOperationResult> getOwnerOperation(String key) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.operation(key));
    }
}
