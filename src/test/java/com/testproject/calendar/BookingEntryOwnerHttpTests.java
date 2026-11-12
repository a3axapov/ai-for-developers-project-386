package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "calendar.initial.username=booking-entry")
class BookingEntryOwnerHttpTests {
    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    @Test
    void validOwnerHasPublicPageAndTechnicalEntry() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var base = "http://127.0.0.1:" + port + "/api/public/booking-entry";
            var page = client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertEquals("Владелец календаря", json.readTree(page.body()).get("ownerName").asString());
            assertEquals("BOOKING_DISABLED", json.readTree(page.body()).get("state").asString());
            var entry = client.send(HttpRequest.newBuilder(URI.create(base + "/current")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, entry.statusCode());
            assertEquals("/booking/booking-entry", json.readTree(entry.body()).get("bookingUrl").asString());
        }
    }
}
