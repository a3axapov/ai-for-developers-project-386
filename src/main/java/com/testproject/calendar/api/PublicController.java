package com.testproject.calendar.api;

import com.testproject.calendar.generated.api.PublicApi;
import com.testproject.calendar.generated.model.BookingEntry;
import com.testproject.calendar.generated.model.ApiError;
import com.testproject.calendar.generated.model.PublicBookingPage;
import com.testproject.calendar.owner.OwnerState;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicController implements PublicApi {

    private final OwnerState owner;

    public PublicController(OwnerState owner) {
        this.owner = owner;
    }

    @Override
    public ResponseEntity<BookingEntry> getBookingEntry() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new BookingEntry(owner.profile().getBookingUrl()));
    }

    @Override
    public ResponseEntity<PublicBookingPage> getPublicBookingPage(String username) {
        var profile = owner.profile();
        if (!profile.getUsername().equals(username)) {
            throw new ApiFailure(404, new ApiError("PAGE_NOT_FOUND", "Страница не найдена"));
        }
        var page = new PublicBookingPage(profile.getName(), profile.getTimeZone(),
                profile.getBookingEnabled() ? PublicBookingPage.StateEnum.OPEN
                        : PublicBookingPage.StateEnum.BOOKING_DISABLED);
        if (profile.getBookingEnabled()) {
            page.setItems(List.of());
        } else {
            page.setItems(null);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
    }
}
