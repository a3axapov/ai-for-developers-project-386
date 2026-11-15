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
 * UpdateOutOfOfficeCommand
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class UpdateOutOfOfficeCommand {

  private String startDate;

  private String endDate;

  private @Nullable String confirmationToken;

  private Long expectedVersion;

  public UpdateOutOfOfficeCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public UpdateOutOfOfficeCommand(String startDate, String endDate, Long expectedVersion) {
    this.startDate = startDate;
    this.endDate = endDate;
    this.expectedVersion = expectedVersion;
  }

  public UpdateOutOfOfficeCommand startDate(String startDate) {
    this.startDate = startDate;
    return this;
  }

  /**
   * Get startDate
   * @return startDate
   */
  @JsonProperty("startDate")
  public String getStartDate() {
    return startDate;
  }

  @JsonProperty("startDate")
  public void setStartDate(String startDate) {
    this.startDate = startDate;
  }

  public UpdateOutOfOfficeCommand endDate(String endDate) {
    this.endDate = endDate;
    return this;
  }

  /**
   * Get endDate
   * @return endDate
   */
  @JsonProperty("endDate")
  public String getEndDate() {
    return endDate;
  }

  @JsonProperty("endDate")
  public void setEndDate(String endDate) {
    this.endDate = endDate;
  }

  public UpdateOutOfOfficeCommand confirmationToken(@Nullable String confirmationToken) {
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

  public UpdateOutOfOfficeCommand expectedVersion(Long expectedVersion) {
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
    UpdateOutOfOfficeCommand updateOutOfOfficeCommand = (UpdateOutOfOfficeCommand) o;
    return Objects.equals(this.startDate, updateOutOfOfficeCommand.startDate) &&
        Objects.equals(this.endDate, updateOutOfOfficeCommand.endDate) &&
        Objects.equals(this.confirmationToken, updateOutOfOfficeCommand.confirmationToken) &&
        Objects.equals(this.expectedVersion, updateOutOfOfficeCommand.expectedVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(startDate, endDate, confirmationToken, expectedVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateOutOfOfficeCommand {\n");
    sb.append("    startDate: ").append(toIndentedString(startDate)).append("\n");
    sb.append("    endDate: ").append(toIndentedString(endDate)).append("\n");
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

