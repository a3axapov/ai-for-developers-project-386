package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * RescheduleSlots
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class RescheduleSlots {

  private String fromDate;

  private String toDate;

  private String ownerTimeZone;

  private String meetingTypeTimeZone;

  private List<Slot> items;

  public RescheduleSlots() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public RescheduleSlots(String fromDate, String toDate, String ownerTimeZone, String meetingTypeTimeZone, List<Slot> items) {
    this.fromDate = fromDate;
    this.toDate = toDate;
    this.ownerTimeZone = ownerTimeZone;
    this.meetingTypeTimeZone = meetingTypeTimeZone;
    this.items = items;
  }

  public RescheduleSlots fromDate(String fromDate) {
    this.fromDate = fromDate;
    return this;
  }

  /**
   * Inclusive local date in the current owner zone.
   * @return fromDate
   */
  @JsonProperty("fromDate")
  public String getFromDate() {
    return fromDate;
  }

  @JsonProperty("fromDate")
  public void setFromDate(String fromDate) {
    this.fromDate = fromDate;
  }

  public RescheduleSlots toDate(String toDate) {
    this.toDate = toDate;
    return this;
  }

  /**
   * Exclusive local date in the current owner zone. No product horizon limit.
   * @return toDate
   */
  @JsonProperty("toDate")
  public String getToDate() {
    return toDate;
  }

  @JsonProperty("toDate")
  public void setToDate(String toDate) {
    this.toDate = toDate;
  }

  public RescheduleSlots ownerTimeZone(String ownerTimeZone) {
    this.ownerTimeZone = ownerTimeZone;
    return this;
  }

  /**
   * Get ownerTimeZone
   * @return ownerTimeZone
   */
  @JsonProperty("ownerTimeZone")
  public String getOwnerTimeZone() {
    return ownerTimeZone;
  }

  @JsonProperty("ownerTimeZone")
  public void setOwnerTimeZone(String ownerTimeZone) {
    this.ownerTimeZone = ownerTimeZone;
  }

  public RescheduleSlots meetingTypeTimeZone(String meetingTypeTimeZone) {
    this.meetingTypeTimeZone = meetingTypeTimeZone;
    return this;
  }

  /**
   * Get meetingTypeTimeZone
   * @return meetingTypeTimeZone
   */
  @JsonProperty("meetingTypeTimeZone")
  public String getMeetingTypeTimeZone() {
    return meetingTypeTimeZone;
  }

  @JsonProperty("meetingTypeTimeZone")
  public void setMeetingTypeTimeZone(String meetingTypeTimeZone) {
    this.meetingTypeTimeZone = meetingTypeTimeZone;
  }

  public RescheduleSlots items(List<Slot> items) {
    this.items = items;
    return this;
  }

  public RescheduleSlots addItemsItem(Slot itemsItem) {
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
  public List<Slot> getItems() {
    return items;
  }

  @JsonProperty("items")
  public void setItems(List<Slot> items) {
    this.items = items;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RescheduleSlots rescheduleSlots = (RescheduleSlots) o;
    return Objects.equals(this.fromDate, rescheduleSlots.fromDate) &&
        Objects.equals(this.toDate, rescheduleSlots.toDate) &&
        Objects.equals(this.ownerTimeZone, rescheduleSlots.ownerTimeZone) &&
        Objects.equals(this.meetingTypeTimeZone, rescheduleSlots.meetingTypeTimeZone) &&
        Objects.equals(this.items, rescheduleSlots.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromDate, toDate, ownerTimeZone, meetingTypeTimeZone, items);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class RescheduleSlots {\n");
    sb.append("    fromDate: ").append(toIndentedString(fromDate)).append("\n");
    sb.append("    toDate: ").append(toIndentedString(toDate)).append("\n");
    sb.append("    ownerTimeZone: ").append(toIndentedString(ownerTimeZone)).append("\n");
    sb.append("    meetingTypeTimeZone: ").append(toIndentedString(meetingTypeTimeZone)).append("\n");
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
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

