package com.testproject.calendar.api;

import com.testproject.calendar.generated.api.OwnerApi;
import com.testproject.calendar.generated.model.OwnerProfile;
import com.testproject.calendar.generated.model.OwnerOperationResult;
import com.testproject.calendar.generated.model.PublicBookingCommand;
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
    public ResponseEntity<OwnerProfile> getOwnerProfile() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.profile());
    }

    @Override
    public ResponseEntity<OwnerProfile> setPublicBooking(String idempotencyKey, PublicBookingCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(owner.setPublicBooking(idempotencyKey, command));
    }

    @Override
    public ResponseEntity<OwnerOperationResult> getOwnerOperation(String key) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(owner.operation(key));
    }
}
