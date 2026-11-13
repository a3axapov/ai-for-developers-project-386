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
 * DateOverride
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class DateOverride {

  private String date;

  private List<WorkingInterval> intervals;

  public DateOverride() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DateOverride(String date, List<WorkingInterval> intervals) {
    this.date = date;
    this.intervals = intervals;
  }

  public DateOverride date(String date) {
    this.date = date;
    return this;
  }

  /**
   * Get date
   * @return date
   */
  @JsonProperty("date")
  public String getDate() {
    return date;
  }

  @JsonProperty("date")
  public void setDate(String date) {
    this.date = date;
  }

  public DateOverride intervals(List<WorkingInterval> intervals) {
    this.intervals = intervals;
    return this;
  }

  public DateOverride addIntervalsItem(WorkingInterval intervalsItem) {
    if (this.intervals == null) {
      this.intervals = new ArrayList<>();
    }
    this.intervals.add(intervalsItem);
    return this;
  }

  /**
   * Get intervals
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DateOverride dateOverride = (DateOverride) o;
    return Objects.equals(this.date, dateOverride.date) &&
        Objects.equals(this.intervals, dateOverride.intervals);
  }

  @Override
  public int hashCode() {
    return Objects.hash(date, intervals);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DateOverride {\n");
    sb.append("    date: ").append(toIndentedString(date)).append("\n");
    sb.append("    intervals: ").append(toIndentedString(intervals)).append("\n");
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

