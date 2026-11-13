package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.testproject.calendar.generated.model.BookingDetails;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * SubmittedBookingAttempt
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class SubmittedBookingAttempt {

  /**
   * Gets or Sets state
   */
  public enum StateEnum {
    SUCCEEDED("SUCCEEDED");

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

  private BookingDetails booking;

  private String resultUrl;

  public SubmittedBookingAttempt() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public SubmittedBookingAttempt(StateEnum state, BookingDetails booking, String resultUrl) {
    this.state = state;
    this.booking = booking;
    this.resultUrl = resultUrl;
  }

  public SubmittedBookingAttempt state(StateEnum state) {
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

  public SubmittedBookingAttempt booking(BookingDetails booking) {
    this.booking = booking;
    return this;
  }

  /**
   * Get booking
   * @return booking
   */
  @JsonProperty("booking")
  public BookingDetails getBooking() {
    return booking;
  }

  @JsonProperty("booking")
  public void setBooking(BookingDetails booking) {
    this.booking = booking;
  }

  public SubmittedBookingAttempt resultUrl(String resultUrl) {
    this.resultUrl = resultUrl;
    return this;
  }

  /**
   * Get resultUrl
   * @return resultUrl
   */
  @JsonProperty("resultUrl")
  public String getResultUrl() {
    return resultUrl;
  }

  @JsonProperty("resultUrl")
  public void setResultUrl(String resultUrl) {
    this.resultUrl = resultUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SubmittedBookingAttempt submittedBookingAttempt = (SubmittedBookingAttempt) o;
    return Objects.equals(this.state, submittedBookingAttempt.state) &&
        Objects.equals(this.booking, submittedBookingAttempt.booking) &&
        Objects.equals(this.resultUrl, submittedBookingAttempt.resultUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, booking, resultUrl);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SubmittedBookingAttempt {\n");
    sb.append("    state: ").append(toIndentedString(state)).append("\n");
    sb.append("    booking: ").append(toIndentedString(booking)).append("\n");
    sb.append("    resultUrl: ").append(toIndentedString(resultUrl)).append("\n");
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

