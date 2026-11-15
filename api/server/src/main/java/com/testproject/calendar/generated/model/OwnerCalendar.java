package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.BookingDetails;
import com.testproject.calendar.generated.model.TimeBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * OwnerCalendar
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class OwnerCalendar {

  private List<BookingDetails> items;

  private List<TimeBlock> timeBlocks;

  public OwnerCalendar() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public OwnerCalendar(List<BookingDetails> items, List<TimeBlock> timeBlocks) {
    this.items = items;
    this.timeBlocks = timeBlocks;
  }

  public OwnerCalendar items(List<BookingDetails> items) {
    this.items = items;
    return this;
  }

  public OwnerCalendar addItemsItem(BookingDetails itemsItem) {
    if (this.items == null) {
      this.items = new ArrayList<>();
    }
    this.items.add(itemsItem);
    return this;
  }

  /**
   * Get items
   * @return items
   */
  @JsonProperty("items")
  public List<BookingDetails> getItems() {
    return items;
  }

  @JsonProperty("items")
  public void setItems(List<BookingDetails> items) {
    this.items = items;
  }

  public OwnerCalendar timeBlocks(List<TimeBlock> timeBlocks) {
    this.timeBlocks = timeBlocks;
    return this;
  }

  public OwnerCalendar addTimeBlocksItem(TimeBlock timeBlocksItem) {
    if (this.timeBlocks == null) {
      this.timeBlocks = new ArrayList<>();
    }
    this.timeBlocks.add(timeBlocksItem);
    return this;
  }

  /**
   * Get timeBlocks
   * @return timeBlocks
   */
  @JsonProperty("timeBlocks")
  public List<TimeBlock> getTimeBlocks() {
    return timeBlocks;
  }

  @JsonProperty("timeBlocks")
  public void setTimeBlocks(List<TimeBlock> timeBlocks) {
    this.timeBlocks = timeBlocks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OwnerCalendar ownerCalendar = (OwnerCalendar) o;
    return Objects.equals(this.items, ownerCalendar.items) &&
        Objects.equals(this.timeBlocks, ownerCalendar.timeBlocks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items, timeBlocks);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OwnerCalendar {\n");
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
    sb.append("    timeBlocks: ").append(toIndentedString(timeBlocks)).append("\n");
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

