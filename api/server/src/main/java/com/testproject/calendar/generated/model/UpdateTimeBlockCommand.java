package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * UpdateTimeBlockCommand
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class UpdateTimeBlockCommand {

  private String title;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startsAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endsAt;

  private @Nullable String confirmationToken;

  private Long expectedVersion;

  public UpdateTimeBlockCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public UpdateTimeBlockCommand(String title, OffsetDateTime startsAt, OffsetDateTime endsAt, Long expectedVersion) {
    this.title = title;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.expectedVersion = expectedVersion;
  }

  public UpdateTimeBlockCommand title(String title) {
    this.title = title;
    return this;
  }

  /**
   * Get title
   * @return title
   */
  @JsonProperty("title")
  public String getTitle() {
    return title;
  }

  @JsonProperty("title")
  public void setTitle(String title) {
    this.title = title;
  }

  public UpdateTimeBlockCommand startsAt(OffsetDateTime startsAt) {
    this.startsAt = startsAt;
    return this;
  }

  /**
   * Literal local date-time and explicit UTC offset must be valid together in the CURRENT owner zone. DST gaps and offsets not offered by that zone are rejected, never shifted.
   * @return startsAt
   */
  @JsonProperty("startsAt")
  public OffsetDateTime getStartsAt() {
    return startsAt;
  }

  @JsonProperty("startsAt")
  public void setStartsAt(OffsetDateTime startsAt) {
    this.startsAt = startsAt;
  }

  public UpdateTimeBlockCommand endsAt(OffsetDateTime endsAt) {
    this.endsAt = endsAt;
    return this;
  }

  /**
   * Get endsAt
   * @return endsAt
   */
  @JsonProperty("endsAt")
  public OffsetDateTime getEndsAt() {
    return endsAt;
  }

  @JsonProperty("endsAt")
  public void setEndsAt(OffsetDateTime endsAt) {
    this.endsAt = endsAt;
  }

  public UpdateTimeBlockCommand confirmationToken(@Nullable String confirmationToken) {
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

  public UpdateTimeBlockCommand expectedVersion(Long expectedVersion) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UpdateTimeBlockCommand updateTimeBlockCommand = (UpdateTimeBlockCommand) o;
    return Objects.equals(this.title, updateTimeBlockCommand.title) &&
        Objects.equals(this.startsAt, updateTimeBlockCommand.startsAt) &&
        Objects.equals(this.endsAt, updateTimeBlockCommand.endsAt) &&
        Objects.equals(this.confirmationToken, updateTimeBlockCommand.confirmationToken) &&
        Objects.equals(this.expectedVersion, updateTimeBlockCommand.expectedVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(title, startsAt, endsAt, confirmationToken, expectedVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateTimeBlockCommand {\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    startsAt: ").append(toIndentedString(startsAt)).append("\n");
    sb.append("    endsAt: ").append(toIndentedString(endsAt)).append("\n");
    sb.append("    confirmationToken: ").append(toIndentedString(confirmationToken)).append("\n");
    sb.append("    expectedVersion: ").append(toIndentedString(expectedVersion)).append("\n");
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

