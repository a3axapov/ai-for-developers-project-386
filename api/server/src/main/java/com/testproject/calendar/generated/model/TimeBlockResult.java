package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.TimeBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * TimeBlockResult
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class TimeBlockResult {

  private TimeBlock timeBlock;

  private List<String> rejectedBookingIds;

  public TimeBlockResult() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public TimeBlockResult(TimeBlock timeBlock, List<String> rejectedBookingIds) {
    this.timeBlock = timeBlock;
    this.rejectedBookingIds = rejectedBookingIds;
  }

  public TimeBlockResult timeBlock(TimeBlock timeBlock) {
    this.timeBlock = timeBlock;
    return this;
  }

  /**
   * Get timeBlock
   * @return timeBlock
   */
  @JsonProperty("timeBlock")
  public TimeBlock getTimeBlock() {
    return timeBlock;
  }

  @JsonProperty("timeBlock")
  public void setTimeBlock(TimeBlock timeBlock) {
    this.timeBlock = timeBlock;
  }

  public TimeBlockResult rejectedBookingIds(List<String> rejectedBookingIds) {
    this.rejectedBookingIds = rejectedBookingIds;
    return this;
  }

  public TimeBlockResult addRejectedBookingIdsItem(String rejectedBookingIdsItem) {
    if (this.rejectedBookingIds == null) {
      this.rejectedBookingIds = new ArrayList<>();
    }
    this.rejectedBookingIds.add(rejectedBookingIdsItem);
    return this;
  }

  /**
   * Get rejectedBookingIds
   * @return rejectedBookingIds
   */
  @JsonProperty("rejectedBookingIds")
  public List<String> getRejectedBookingIds() {
    return rejectedBookingIds;
  }

  @JsonProperty("rejectedBookingIds")
  public void setRejectedBookingIds(List<String> rejectedBookingIds) {
    this.rejectedBookingIds = rejectedBookingIds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TimeBlockResult timeBlockResult = (TimeBlockResult) o;
    return Objects.equals(this.timeBlock, timeBlockResult.timeBlock) &&
        Objects.equals(this.rejectedBookingIds, timeBlockResult.rejectedBookingIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(timeBlock, rejectedBookingIds);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TimeBlockResult {\n");
    sb.append("    timeBlock: ").append(toIndentedString(timeBlock)).append("\n");
    sb.append("    rejectedBookingIds: ").append(toIndentedString(rejectedBookingIds)).append("\n");
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

