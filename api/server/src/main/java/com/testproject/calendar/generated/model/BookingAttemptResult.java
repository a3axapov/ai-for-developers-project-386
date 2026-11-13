package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.testproject.calendar.generated.model.ApiError;
import com.testproject.calendar.generated.model.BookingDetails;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Read-only saved attempt outcome. PREPARED has no booking; SUCCEEDED includes booking and resultUrl; FAILED includes the saved definite error.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class BookingAttemptResult {

  /**
   * Gets or Sets state
   */
  public enum StateEnum {
    PREPARED("PREPARED"),
    
    SUCCEEDED("SUCCEEDED"),
    
    FAILED("FAILED");

    private final String value;

    StateEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static StateEnum fromValue(String value) {
      for (StateEnum b : StateEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  private StateEnum state;

  private @Nullable BookingDetails booking;

  private @Nullable String resultUrl;

  private @Nullable ApiError error;

  public BookingAttemptResult() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public BookingAttemptResult(StateEnum state) {
    this.state = state;
  }

  public BookingAttemptResult state(StateEnum state) {
    this.state = state;
    return this;
  }

  /**
   * Get state
   * @return state
   */
  @JsonProperty("state")
  public StateEnum getState() {
    return state;
  }

  @JsonProperty("state")
  public void setState(StateEnum state) {
    this.state = state;
  }

  public BookingAttemptResult booking(@Nullable BookingDetails booking) {
    this.booking = booking;
    return this;
  }

  /**
   * Get booking
   * @return booking
   */
  @JsonProperty("booking")
  public @Nullable BookingDetails getBooking() {
    return booking;
  }

  @JsonProperty("booking")
  public void setBooking(@Nullable BookingDetails booking) {
    this.booking = booking;
  }

  public BookingAttemptResult resultUrl(@Nullable String resultUrl) {
    this.resultUrl = resultUrl;
    return this;
  }

  /**
   * Get resultUrl
   * @return resultUrl
   */
  @JsonProperty("resultUrl")
  public @Nullable String getResultUrl() {
    return resultUrl;
  }

  @JsonProperty("resultUrl")
  public void setResultUrl(@Nullable String resultUrl) {
    this.resultUrl = resultUrl;
  }

  public BookingAttemptResult error(@Nullable ApiError error) {
    this.error = error;
    return this;
  }

  /**
   * Get error
   * @return error
   */
  @JsonProperty("error")
  public @Nullable ApiError getError() {
    return error;
  }

  @JsonProperty("error")
  public void setError(@Nullable ApiError error) {
    this.error = error;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BookingAttemptResult bookingAttemptResult = (BookingAttemptResult) o;
    return Objects.equals(this.state, bookingAttemptResult.state) &&
        Objects.equals(this.booking, bookingAttemptResult.booking) &&
        Objects.equals(this.resultUrl, bookingAttemptResult.resultUrl) &&
        Objects.equals(this.error, bookingAttemptResult.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, booking, resultUrl, error);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class BookingAttemptResult {\n");
    sb.append("    state: ").append(toIndentedString(state)).append("\n");
    sb.append("    booking: ").append(toIndentedString(booking)).append("\n");
    sb.append("    resultUrl: ").append(toIndentedString(resultUrl)).append("\n");
    sb.append("    error: ").append(toIndentedString(error)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(@Nullable Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
}

