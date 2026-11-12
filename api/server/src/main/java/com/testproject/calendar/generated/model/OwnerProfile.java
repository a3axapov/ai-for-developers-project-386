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
 * The single calendar owner&#39;s current profile.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class OwnerProfile {

  private String name;

  private String username;

  private String timeZone;

  private Boolean bookingEnabled;

  private Long version;

  private String bookingUrl;

  public OwnerProfile() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public OwnerProfile(String name, String username, String timeZone, Boolean bookingEnabled, Long version, String bookingUrl) {
    this.name = name;
    this.username = username;
    this.timeZone = timeZone;
    this.bookingEnabled = bookingEnabled;
    this.version = version;
    this.bookingUrl = bookingUrl;
  }

  public OwnerProfile name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
   */
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @JsonProperty("name")
  public void setName(String name) {
    this.name = name;
  }

  public OwnerProfile username(String username) {
    this.username = username;
    return this;
  }

  /**
   * Get username
   * @return username
   */
  @JsonProperty("username")
  public String getUsername() {
    return username;
  }

  @JsonProperty("username")
  public void setUsername(String username) {
    this.username = username;
  }

  public OwnerProfile timeZone(String timeZone) {
    this.timeZone = timeZone;
    return this;
  }

  /**
   * Get timeZone
   * @return timeZone
   */
  @JsonProperty("timeZone")
  public String getTimeZone() {
    return timeZone;
  }

  @JsonProperty("timeZone")
  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public OwnerProfile bookingEnabled(Boolean bookingEnabled) {
    this.bookingEnabled = bookingEnabled;
    return this;
  }

  /**
   * Get bookingEnabled
   * @return bookingEnabled
   */
  @JsonProperty("bookingEnabled")
  public Boolean getBookingEnabled() {
    return bookingEnabled;
  }

  @JsonProperty("bookingEnabled")
  public void setBookingEnabled(Boolean bookingEnabled) {
    this.bookingEnabled = bookingEnabled;
  }

  public OwnerProfile version(Long version) {
    this.version = version;
    return this;
  }

  /**
   * Get version
   * minimum: 1
   * @return version
   */
  @JsonProperty("version")
  public Long getVersion() {
    return version;
  }

  @JsonProperty("version")
  public void setVersion(Long version) {
    this.version = version;
  }

  public OwnerProfile bookingUrl(String bookingUrl) {
    this.bookingUrl = bookingUrl;
    return this;
  }

  /**
   * Same-origin public booking page path.
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
    OwnerProfile ownerProfile = (OwnerProfile) o;
    return Objects.equals(this.name, ownerProfile.name) &&
        Objects.equals(this.username, ownerProfile.username) &&
        Objects.equals(this.timeZone, ownerProfile.timeZone) &&
        Objects.equals(this.bookingEnabled, ownerProfile.bookingEnabled) &&
        Objects.equals(this.version, ownerProfile.version) &&
        Objects.equals(this.bookingUrl, ownerProfile.bookingUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, username, timeZone, bookingEnabled, version, bookingUrl);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OwnerProfile {\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    timeZone: ").append(toIndentedString(timeZone)).append("\n");
    sb.append("    bookingEnabled: ").append(toIndentedString(bookingEnabled)).append("\n");
    sb.append("    version: ").append(toIndentedString(version)).append("\n");
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

