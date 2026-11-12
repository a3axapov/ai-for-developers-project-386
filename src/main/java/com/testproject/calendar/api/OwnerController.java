package com.testproject.calendar.api;

import com.testproject.calendar.generated.api.OwnerApi;
import com.testproject.calendar.generated.model.OwnerProfile;
import com.testproject.calendar.owner.InitialCalendarSettings;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OwnerController implements OwnerApi {

    private final InitialCalendarSettings settings;

    public OwnerController(InitialCalendarSettings settings) {
        this.settings = settings;
    }

    @Override
    public ResponseEntity<OwnerProfile> getOwnerProfile() {
        var profile = new OwnerProfile(settings.name(), settings.username(), settings.timeZone(),
                settings.bookingEnabled(), 1L, "/booking/" + settings.username());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile);
    }
}
