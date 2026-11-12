package com.testproject.calendar.api;

import com.testproject.calendar.generated.api.DefaultApi;
import com.testproject.calendar.generated.model.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController implements DefaultApi {

    @Override
    public ResponseEntity<Health> getHealth() {
        return ResponseEntity.ok(new Health(Health.StatusEnum.UP));
    }
}
