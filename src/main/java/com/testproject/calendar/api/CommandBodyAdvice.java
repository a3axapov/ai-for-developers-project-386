package com.testproject.calendar.api;

import com.testproject.calendar.generated.model.PublicBookingCommand;
import com.testproject.calendar.generated.model.ProfileCommand;
import com.testproject.calendar.generated.model.CreateMeetingTypeCommand;
import com.testproject.calendar.generated.model.BookingDecisionCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Keep the original body for idempotent, definite format failures, before JSON binding can fail. */
@ControllerAdvice(assignableTypes = OwnerController.class)
public class CommandBodyAdvice extends RequestBodyAdviceAdapter {

    public static final String RAW_BODY = CommandBodyAdvice.class.getName() + ".rawBody";
    private final HttpServletRequest request;

    public CommandBodyAdvice(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == PublicBookingCommand.class || targetType == ProfileCommand.class
                || targetType == CreateMeetingTypeCommand.class || targetType == BookingDecisionCommand.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] bytes = input.getBody().readAllBytes();
        request.setAttribute(RAW_BODY, new String(bytes, StandardCharsets.UTF_8));
        return new BufferedInput(input.getHeaders(), bytes);
    }

    private record BufferedInput(HttpHeaders headers, byte[] bytes) implements HttpInputMessage {
        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
