package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.Slot;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * The complete, guest-independent actual-time window, from tomorrow 00:00 in the current owner zone for 14 calendar days. Every slot fits entirely; ending exactly at windowEnd is allowed.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class PublicSlots {

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime windowStart;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime windowEnd;

  private String ownerTimeZone;

  private String meetingTypeTimeZone;

  private String conditionsToken;

  private List<Slot> items;

  public PublicSlots() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PublicSlots(OffsetDateTime windowStart, OffsetDateTime windowEnd, String ownerTimeZone, String meetingTypeTimeZone, String conditionsToken, List<Slot> items) {
    this.windowStart = windowStart;
    this.windowEnd = windowEnd;
    this.ownerTimeZone = ownerTimeZone;
    this.meetingTypeTimeZone = meetingTypeTimeZone;
    this.conditionsToken = conditionsToken;
    this.items = items;
  }

  public PublicSlots windowStart(OffsetDateTime windowStart) {
    this.windowStart = windowStart;
    return this;
  }

  /**
   * Get windowStart
   * @return windowStart
   */
  @JsonProperty("windowStart")
  public OffsetDateTime getWindowStart() {
    return windowStart;
  }

  @JsonProperty("windowStart")
  public void setWindowStart(OffsetDateTime windowStart) {
    this.windowStart = windowStart;
  }

  public PublicSlots windowEnd(OffsetDateTime windowEnd) {
    this.windowEnd = windowEnd;
    return this;
  }

  /**
   * Get windowEnd
   * @return windowEnd
   */
  @JsonProperty("windowEnd")
  public OffsetDateTime getWindowEnd() {
    return windowEnd;
  }

  @JsonProperty("windowEnd")
  public void setWindowEnd(OffsetDateTime windowEnd) {
    this.windowEnd = windowEnd;
  }

  public PublicSlots ownerTimeZone(String ownerTimeZone) {
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

  public PublicSlots meetingTypeTimeZone(String meetingTypeTimeZone) {
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

  public PublicSlots conditionsToken(String conditionsToken) {
    this.conditionsToken = conditionsToken;
    return this;
  }

  /**
   * Get conditionsToken
   * @return conditionsToken
   */
  @JsonProperty("conditionsToken")
  public String getConditionsToken() {
    return conditionsToken;
  }

  @JsonProperty("conditionsToken")
  public void setConditionsToken(String conditionsToken) {
    this.conditionsToken = conditionsToken;
  }

  public PublicSlots items(List<Slot> items) {
    this.items = items;
    return this;
  }

  public PublicSlots addItemsItem(Slot itemsItem) {
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
    PublicSlots publicSlots = (PublicSlots) o;
    return Objects.equals(this.windowStart, publicSlots.windowStart) &&
        Objects.equals(this.windowEnd, publicSlots.windowEnd) &&
        Objects.equals(this.ownerTimeZone, publicSlots.ownerTimeZone) &&
        Objects.equals(this.meetingTypeTimeZone, publicSlots.meetingTypeTimeZone) &&
        Objects.equals(this.conditionsToken, publicSlots.conditionsToken) &&
        Objects.equals(this.items, publicSlots.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(windowStart, windowEnd, ownerTimeZone, meetingTypeTimeZone, conditionsToken, items);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PublicSlots {\n");
    sb.append("    windowStart: ").append(toIndentedString(windowStart)).append("\n");
    sb.append("    windowEnd: ").append(toIndentedString(windowEnd)).append("\n");
    sb.append("    ownerTimeZone: ").append(toIndentedString(ownerTimeZone)).append("\n");
    sb.append("    meetingTypeTimeZone: ").append(toIndentedString(meetingTypeTimeZone)).append("\n");
    sb.append("    conditionsToken: ").append(toIndentedString(conditionsToken)).append("\n");
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

