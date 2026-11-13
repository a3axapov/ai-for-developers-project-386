package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.testproject.calendar.generated.model.WorkingInterval;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * WorkingDay
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class WorkingDay {

  /**
   * Gets or Sets day
   */
  public enum DayEnum {
    MONDAY("MONDAY"),
    
    TUESDAY("TUESDAY"),
    
    WEDNESDAY("WEDNESDAY"),
    
    THURSDAY("THURSDAY"),
    
    FRIDAY("FRIDAY"),
    
    SATURDAY("SATURDAY"),
    
    SUNDAY("SUNDAY");

    private final String value;

    DayEnum(String value) {
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
    public static DayEnum fromValue(String value) {
      for (DayEnum b : DayEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  private DayEnum day;

  private List<WorkingInterval> intervals;

  public WorkingDay() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public WorkingDay(DayEnum day, List<WorkingInterval> intervals) {
    this.day = day;
    this.intervals = intervals;
  }

  public WorkingDay day(DayEnum day) {
    this.day = day;
    return this;
  }

  /**
   * Get day
   * @return day
   */
  @JsonProperty("day")
  public DayEnum getDay() {
    return day;
  }

  @JsonProperty("day")
  public void setDay(DayEnum day) {
    this.day = day;
  }

  public WorkingDay intervals(List<WorkingInterval> intervals) {
    this.intervals = intervals;
    return this;
  }

  public WorkingDay addIntervalsItem(WorkingInterval intervalsItem) {
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
    WorkingDay workingDay = (WorkingDay) o;
    return Objects.equals(this.day, workingDay.day) &&
        Objects.equals(this.intervals, workingDay.intervals);
  }

  @Override
  public int hashCode() {
    return Objects.hash(day, intervals);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class WorkingDay {\n");
    sb.append("    day: ").append(toIndentedString(day)).append("\n");
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

