package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * BookingEntry
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class BookingEntry {

  private String bookingUrl;

  public BookingEntry() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public BookingEntry(String bookingUrl) {
    this.bookingUrl = bookingUrl;
  }

  public BookingEntry bookingUrl(String bookingUrl) {
    this.bookingUrl = bookingUrl;
    return this;
  }

  /**
   * Get bookingUrl
   * @return bookingUrl
   */
  @JsonProperty("bookingUrl")
  public String getBookingUrl() {
    return bookingUrl;
  }

  @JsonProperty("bookingUrl")
  public void setBookingUrl(String bookingUrl) {
    this.bookingUrl = bookingUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BookingEntry bookingEntry = (BookingEntry) o;
    return Objects.equals(this.bookingUrl, bookingEntry.bookingUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bookingUrl);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class BookingEntry {\n");
    sb.append("    bookingUrl: ").append(toIndentedString(bookingUrl)).append("\n");
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

