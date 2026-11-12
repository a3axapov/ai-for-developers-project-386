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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "calendar.initial.name=Анна Петрова", "calendar.initial.username=anna-1"})
class PublicBookingHttpTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    @Test
    void unknownBookingEntryUsernameIsNotTheTechnicalEntry() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + port + "/api/public/booking-entry")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
            assertEquals("PAGE_NOT_FOUND", json.readTree(response.body()).get("code").asString());
        }
    }

    @Test
    void bookingEntryUsesCurrentConfiguredUsername() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + port + "/api/public/booking-entry/current")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(json.readTree("{\"bookingUrl\":\"/booking/anna-1\"}"), json.readTree(response.body()));
        }
    }

    @Test
    void disabledPageShowsOwnerWithoutMeetingTypes() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + port + "/api/public/anna-1")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(json.readTree("""
                    {"ownerName":"Анна Петрова","ownerTimeZone":"Europe/Moscow","state":"BOOKING_DISABLED"}
                    """), json.readTree(response.body()));
        }
    }

    @Test
    void unknownUsernameIsNotRedirectedEvenWhenBookingIsDisabled() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + port + "/api/public/owner")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
            assertEquals(json.readTree("""
                    {"code":"PAGE_NOT_FOUND","message":"Страница не найдена"}
                    """), json.readTree(response.body()));
            assertEquals(false, response.headers().firstValue("Location").isPresent());
        }
    }
}
