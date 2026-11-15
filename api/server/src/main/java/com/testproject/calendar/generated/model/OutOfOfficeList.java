package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.OutOfOffice;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * OutOfOfficeList
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class OutOfOfficeList {

  private List<OutOfOffice> items;

  public OutOfOfficeList() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public OutOfOfficeList(List<OutOfOffice> items) {
    this.items = items;
  }

  public OutOfOfficeList items(List<OutOfOffice> items) {
    this.items = items;
    return this;
  }

  public OutOfOfficeList addItemsItem(OutOfOffice itemsItem) {
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
  public List<OutOfOffice> getItems() {
    return items;
  }

  @JsonProperty("items")
  public void setItems(List<OutOfOffice> items) {
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
    OutOfOfficeList outOfOfficeList = (OutOfOfficeList) o;
    return Objects.equals(this.items, outOfOfficeList.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OutOfOfficeList {\n");
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

