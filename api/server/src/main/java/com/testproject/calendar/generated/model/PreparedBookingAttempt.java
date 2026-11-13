package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * PreparedBookingAttempt
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class PreparedBookingAttempt {

  private String attemptId;

  private String attemptSecret;

  /**
   * Gets or Sets state
   */
  public enum StateEnum {
    PREPARED("PREPARED");

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

  public PreparedBookingAttempt() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PreparedBookingAttempt(String attemptId, String attemptSecret, StateEnum state) {
    this.attemptId = attemptId;
    this.attemptSecret = attemptSecret;
    this.state = state;
  }

  public PreparedBookingAttempt attemptId(String attemptId) {
    this.attemptId = attemptId;
    return this;
  }

  /**
   * Random identifier; alone it grants no access.
   * @return attemptId
   */
  @JsonProperty("attemptId")
  public String getAttemptId() {
    return attemptId;
  }

  @JsonProperty("attemptId")
  public void setAttemptId(String attemptId) {
    this.attemptId = attemptId;
  }

  public PreparedBookingAttempt attemptSecret(String attemptSecret) {
    this.attemptSecret = attemptSecret;
    return this;
  }

  /**
   * Independent cryptographically random secret of at least 256 bits. Never put in URLs or logs; keep only in page memory.
   * @return attemptSecret
   */
  @JsonProperty("attemptSecret")
  public String getAttemptSecret() {
    return attemptSecret;
  }

  @JsonProperty("attemptSecret")
  public void setAttemptSecret(String attemptSecret) {
    this.attemptSecret = attemptSecret;
  }

  public PreparedBookingAttempt state(StateEnum state) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PreparedBookingAttempt preparedBookingAttempt = (PreparedBookingAttempt) o;
    return Objects.equals(this.attemptId, preparedBookingAttempt.attemptId) &&
        Objects.equals(this.attemptSecret, preparedBookingAttempt.attemptSecret) &&
        Objects.equals(this.state, preparedBookingAttempt.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attemptId, attemptSecret, state);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PreparedBookingAttempt {\n");
    sb.append("    attemptId: ").append(toIndentedString(attemptId)).append("\n");
    sb.append("    attemptSecret: ").append(toIndentedString(attemptSecret)).append("\n");
    sb.append("    state: ").append(toIndentedString(state)).append("\n");
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

