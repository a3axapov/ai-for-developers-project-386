package com.testproject.calendar.api;

import com.testproject.calendar.generated.api.OwnerApi;
import com.testproject.calendar.generated.model.OwnerProfile;
import com.testproject.calendar.generated.model.OwnerOperationResult;
import com.testproject.calendar.generated.model.PublicBookingCommand;
import com.testproject.calendar.generated.model.ProfileCommand;
import com.testproject.calendar.generated.model.CreateMeetingTypeCommand;
import com.testproject.calendar.generated.model.UpdateMeetingTypeCommand;
import com.testproject.calendar.generated.model.ArchiveMeetingTypeCommand;
import com.testproject.calendar.generated.model.WeeklyHoursCommand;
import com.testproject.calendar.generated.model.DateOverrideCommand;
import com.testproject.calendar.generated.model.DeleteDateOverrideCommand;
import com.testproject.calendar.generated.model.OutOfOffice;
import com.testproject.calendar.generated.model.OutOfOfficeList;
import com.testproject.calendar.generated.model.CreateOutOfOfficeCommand;
import com.testproject.calendar.generated.model.UpdateOutOfOfficeCommand;
import com.testproject.calendar.generated.model.DeleteOutOfOfficeCommand;
import com.testproject.calendar.generated.model.MeetingType;
import com.testproject.calendar.generated.model.MeetingTypeList;
import com.testproject.calendar.generated.model.OwnerBookingList;
import com.testproject.calendar.generated.model.OwnerCalendar;
import com.testproject.calendar.generated.model.TimeBlockResult;
import com.testproject.calendar.generated.model.CreateTimeBlockCommand;
import com.testproject.calendar.generated.model.UpdateTimeBlockCommand;
import com.testproject.calendar.generated.model.DeleteTimeBlockCommand;
import com.testproject.calendar.generated.model.BookingDetails;
import com.testproject.calendar.generated.model.BookingDecisionCommand;
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
    public ResponseEntity<TimeBlockResult> createTimeBlock(String key, CreateTimeBlockCommand command) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(owner.createBlock(key, command));
    }

    @Override
    public ResponseEntity<TimeBlockResult> updateTimeBlock(String id, String key, UpdateTimeBlockCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.updateBlock(key, id, command));
    }

    @Override
    public ResponseEntity<Void> deleteTimeBlock(String id, String key, DeleteTimeBlockCommand command) {
        owner.deleteBlock(key, id, command);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @Override
    public ResponseEntity<OutOfOfficeList> listOutOfOffice() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new OutOfOfficeList(owner.vacations()));
    }

    @Override
    public ResponseEntity<OutOfOffice> createOutOfOffice(String key, CreateOutOfOfficeCommand command) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(owner.createVacation(key, command));
    }

    @Override
    public ResponseEntity<OutOfOffice> updateOutOfOffice(String id, String key, UpdateOutOfOfficeCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.updateVacation(key, id, command));
    }

    @Override
    public ResponseEntity<Void> deleteOutOfOffice(String id, String key, DeleteOutOfOfficeCommand command) {
        owner.deleteVacation(key, id, command);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @Override
    public ResponseEntity<OwnerBookingList> listOwnerBookings(String view, String status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new OwnerBookingList(owner.ownerBookings(view, status)));
    }

    @Override
    public ResponseEntity<BookingDetails> cancelOwnerBooking(
            String id, String idempotencyKey, BookingDecisionCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.decideBooking(idempotencyKey, id, "cancel", command));
    }

    @Override
    public ResponseEntity<BookingDetails> confirmOwnerBooking(
            String id, String idempotencyKey, BookingDecisionCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.decideBooking(idempotencyKey, id, "confirm", command));
    }

    @Override
    public ResponseEntity<BookingDetails> rejectOwnerBooking(
            String id, String idempotencyKey, BookingDecisionCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.decideBooking(idempotencyKey, id, "reject", command));
    }

    @Override
    public ResponseEntity<BookingDetails> getOwnerBooking(String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.ownerBooking(id));
    }

    @Override
    public ResponseEntity<OwnerCalendar> getOwnerCalendar(
            java.time.OffsetDateTime from, java.time.OffsetDateTime to) {
        synchronized (owner) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new OwnerCalendar(owner.ownerCalendar(from, to), owner.calendarBlocks(from, to)));
        }
    }

    @Override
    public ResponseEntity<MeetingType> createMeetingType(String idempotencyKey, CreateMeetingTypeCommand command) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(owner.createMeetingType(idempotencyKey, command));
    }

    @Override
    public ResponseEntity<MeetingType> archiveMeetingType(
            String id, String idempotencyKey, ArchiveMeetingTypeCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.archiveMeetingType(idempotencyKey, id, command));
    }

    @Override
    public ResponseEntity<MeetingType> updateMeetingType(
            String id, String idempotencyKey, UpdateMeetingTypeCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.updateMeetingType(idempotencyKey, id, command));
    }

    @Override
    public ResponseEntity<MeetingType> updateWeeklyHours(
            String id, String idempotencyKey, WeeklyHoursCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.updateWeeklyHours(idempotencyKey, id, command));
    }

    @Override
    public ResponseEntity<MeetingType> putDateOverride(
            String id, String date, String idempotencyKey, DateOverrideCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.editDateOverride(idempotencyKey, id, date, command));
    }

    @Override
    public ResponseEntity<MeetingType> deleteDateOverride(
            String id, String date, String idempotencyKey, DeleteDateOverrideCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.editDateOverride(idempotencyKey, id, date, command));
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
