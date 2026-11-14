package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.WorkingDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * WeeklyHoursCommand
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class WeeklyHoursCommand {

  private List<WorkingDay> days;

  private Long expectedVersion;

  private @Nullable String confirmationToken;

  public WeeklyHoursCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public WeeklyHoursCommand(List<WorkingDay> days, Long expectedVersion) {
    this.days = days;
    this.expectedVersion = expectedVersion;
  }

  public WeeklyHoursCommand days(List<WorkingDay> days) {
    this.days = days;
    return this;
  }

  public WeeklyHoursCommand addDaysItem(WorkingDay daysItem) {
    if (this.days == null) {
      this.days = new ArrayList<>();
    }
    this.days.add(daysItem);
    return this;
  }

  /**
   * Complete replacement; omitted weekdays are closed. Overlapping/touching minute intervals are merged.
   * @return days
   */
  @JsonProperty("days")
  public List<WorkingDay> getDays() {
    return days;
  }

  @JsonProperty("days")
  public void setDays(List<WorkingDay> days) {
    this.days = days;
  }

  public WeeklyHoursCommand expectedVersion(Long expectedVersion) {
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

  public WeeklyHoursCommand confirmationToken(@Nullable String confirmationToken) {
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
    WeeklyHoursCommand weeklyHoursCommand = (WeeklyHoursCommand) o;
    return Objects.equals(this.days, weeklyHoursCommand.days) &&
        Objects.equals(this.expectedVersion, weeklyHoursCommand.expectedVersion) &&
        Objects.equals(this.confirmationToken, weeklyHoursCommand.confirmationToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(days, expectedVersion, confirmationToken);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class WeeklyHoursCommand {\n");
    sb.append("    days: ").append(toIndentedString(days)).append("\n");
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

