package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * PublicBookingPage
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class PublicBookingPage {

  private String ownerName;

  private String ownerTimeZone;

  /**
   * Gets or Sets state
   */
  public enum StateEnum {
    BOOKING_DISABLED("BOOKING_DISABLED"),
    
    OPEN("OPEN");

    private final String value;

    StateEnum(String value) {
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
    public static StateEnum fromValue(String value) {
      for (StateEnum b : StateEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  private StateEnum state;

  private @Nullable List<Object> items;

  public PublicBookingPage() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PublicBookingPage(String ownerName, String ownerTimeZone, StateEnum state) {
    this.ownerName = ownerName;
    this.ownerTimeZone = ownerTimeZone;
    this.state = state;
  }

  public PublicBookingPage ownerName(String ownerName) {
    this.ownerName = ownerName;
    return this;
  }

  /**
   * Get ownerName
   * @return ownerName
   */
  @JsonProperty("ownerName")
  public String getOwnerName() {
    return ownerName;
  }

  @JsonProperty("ownerName")
  public void setOwnerName(String ownerName) {
    this.ownerName = ownerName;
  }

  public PublicBookingPage ownerTimeZone(String ownerTimeZone) {
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

  public PublicBookingPage state(StateEnum state) {
    this.state = state;
    return this;
  }

  /**
   * Get state
   * @return state
   */
  @JsonProperty("state")
  public StateEnum getState() {
    return state;
  }

  @JsonProperty("state")
  public void setState(StateEnum state) {
    this.state = state;
  }

  public PublicBookingPage items(@Nullable List<Object> items) {
    this.items = items;
    return this;
  }

  public PublicBookingPage addItemsItem(Object itemsItem) {
    if (this.items == null) {
      this.items = new ArrayList<>();
    }
    this.items.add(itemsItem);
    return this;
  }

  /**
   * Present only when OPEN. No meeting types exist in this slice.
   * @return items
   */
  @JsonProperty("items")
  public @Nullable List<Object> getItems() {
    return items;
  }

  @JsonProperty("items")
  public void setItems(@Nullable List<Object> items) {
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
    PublicBookingPage publicBookingPage = (PublicBookingPage) o;
    return Objects.equals(this.ownerName, publicBookingPage.ownerName) &&
        Objects.equals(this.ownerTimeZone, publicBookingPage.ownerTimeZone) &&
        Objects.equals(this.state, publicBookingPage.state) &&
        Objects.equals(this.items, publicBookingPage.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ownerName, ownerTimeZone, state, items);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PublicBookingPage {\n");
    sb.append("    ownerName: ").append(toIndentedString(ownerName)).append("\n");
    sb.append("    ownerTimeZone: ").append(toIndentedString(ownerTimeZone)).append("\n");
    sb.append("    state: ").append(toIndentedString(state)).append("\n");
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

