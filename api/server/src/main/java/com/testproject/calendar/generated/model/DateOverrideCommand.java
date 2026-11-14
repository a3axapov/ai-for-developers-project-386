package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.WorkingInterval;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * DateOverrideCommand
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class DateOverrideCommand {

  private List<WorkingInterval> intervals;

  private Long expectedVersion;

  private @Nullable String confirmationToken;

  public DateOverrideCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DateOverrideCommand(List<WorkingInterval> intervals, Long expectedVersion) {
    this.intervals = intervals;
    this.expectedVersion = expectedVersion;
  }

  public DateOverrideCommand intervals(List<WorkingInterval> intervals) {
    this.intervals = intervals;
    return this;
  }

  public DateOverrideCommand addIntervalsItem(WorkingInterval intervalsItem) {
    if (this.intervals == null) {
      this.intervals = new ArrayList<>();
    }
    this.intervals.add(intervalsItem);
    return this;
  }

  /**
   * Complete replacement for this local date in the type's time zone. Empty means closed, not deletion.
   * @return intervals
   */
  @JsonProperty("intervals")
  public List<WorkingInterval> getIntervals() {
    return intervals;
  }

  @JsonProperty("intervals")
  public void setIntervals(List<WorkingInterval> intervals) {
    this.intervals = intervals;
  }

  public DateOverrideCommand expectedVersion(Long expectedVersion) {
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

  public DateOverrideCommand confirmationToken(@Nullable String confirmationToken) {
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
    DateOverrideCommand dateOverrideCommand = (DateOverrideCommand) o;
    return Objects.equals(this.intervals, dateOverrideCommand.intervals) &&
        Objects.equals(this.expectedVersion, dateOverrideCommand.expectedVersion) &&
        Objects.equals(this.confirmationToken, dateOverrideCommand.confirmationToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(intervals, expectedVersion, confirmationToken);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DateOverrideCommand {\n");
    sb.append("    intervals: ").append(toIndentedString(intervals)).append("\n");
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

