package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.BookingDetails;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Owner-only booking snapshots. No result URLs, access tokens or attempt secrets.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class OwnerBookingList {

  private List<BookingDetails> items;

  public OwnerBookingList() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public OwnerBookingList(List<BookingDetails> items) {
    this.items = items;
  }

  public OwnerBookingList items(List<BookingDetails> items) {
    this.items = items;
    return this;
  }

  public OwnerBookingList addItemsItem(BookingDetails itemsItem) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OwnerBookingList ownerBookingList = (OwnerBookingList) o;
    return Objects.equals(this.items, ownerBookingList.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OwnerBookingList {\n");
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

