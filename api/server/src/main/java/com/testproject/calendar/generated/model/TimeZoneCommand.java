package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * TimeZoneCommand
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class TimeZoneCommand {

  private String timeZone;

  private Boolean applyToActiveTypes;

  private Long expectedVersion;

  private @Nullable String confirmationToken;

  public TimeZoneCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public TimeZoneCommand(String timeZone, Boolean applyToActiveTypes, Long expectedVersion) {
    this.timeZone = timeZone;
    this.applyToActiveTypes = applyToActiveTypes;
    this.expectedVersion = expectedVersion;
  }

  public TimeZoneCommand timeZone(String timeZone) {
    this.timeZone = timeZone;
    return this;
  }

  /**
   * IANA zone ID, using the same policy as initial configuration.
   * @return timeZone
   */
  @JsonProperty("timeZone")
  public String getTimeZone() {
    return timeZone;
  }

  @JsonProperty("timeZone")
  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public TimeZoneCommand applyToActiveTypes(Boolean applyToActiveTypes) {
    this.applyToActiveTypes = applyToActiveTypes;
    return this;
  }

  /**
   * Explicit choice: retain existing type zones, or preserve active types' local hours and exception dates in the new zone. Archived types and vacations never change.
   * @return applyToActiveTypes
   */
  @JsonProperty("applyToActiveTypes")
  public Boolean getApplyToActiveTypes() {
    return applyToActiveTypes;
  }

  @JsonProperty("applyToActiveTypes")
  public void setApplyToActiveTypes(Boolean applyToActiveTypes) {
    this.applyToActiveTypes = applyToActiveTypes;
  }

  public TimeZoneCommand expectedVersion(Long expectedVersion) {
    this.expectedVersion = expectedVersion;
    return this;
  }

  /**
   * Get expectedVersion
   * @return expectedVersion
   */
  @JsonProperty("expectedVersion")
  public Long getExpectedVersion() {
    return expectedVersion;
  }

  @JsonProperty("expectedVersion")
  public void setExpectedVersion(Long expectedVersion) {
    this.expectedVersion = expectedVersion;
  }

  public TimeZoneCommand confirmationToken(@Nullable String confirmationToken) {
    this.confirmationToken = confirmationToken;
    return this;
  }

  /**
   * Get confirmationToken
   * @return confirmationToken
   */
  @JsonProperty("confirmationToken")
  public @Nullable String getConfirmationToken() {
    return confirmationToken;
  }

  @JsonProperty("confirmationToken")
  public void setConfirmationToken(@Nullable String confirmationToken) {
    this.confirmationToken = confirmationToken;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TimeZoneCommand timeZoneCommand = (TimeZoneCommand) o;
    return Objects.equals(this.timeZone, timeZoneCommand.timeZone) &&
        Objects.equals(this.applyToActiveTypes, timeZoneCommand.applyToActiveTypes) &&
        Objects.equals(this.expectedVersion, timeZoneCommand.expectedVersion) &&
        Objects.equals(this.confirmationToken, timeZoneCommand.confirmationToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(timeZone, applyToActiveTypes, expectedVersion, confirmationToken);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TimeZoneCommand {\n");
    sb.append("    timeZone: ").append(toIndentedString(timeZone)).append("\n");
    sb.append("    applyToActiveTypes: ").append(toIndentedString(applyToActiveTypes)).append("\n");
    sb.append("    expectedVersion: ").append(toIndentedString(expectedVersion)).append("\n");
    sb.append("    confirmationToken: ").append(toIndentedString(confirmationToken)).append("\n");
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

