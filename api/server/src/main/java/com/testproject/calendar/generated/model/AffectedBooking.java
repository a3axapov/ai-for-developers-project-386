package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Impact preview only: no guest result link or attempt credentials. Only future PENDING/CONFIRMED bookings valid now and invalid under the proposal. Status/version/time changes require fresh consent.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class AffectedBooking {

  private String id;

  private String meetingTypeId;

  private String title;

  private String guestName;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startsAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endsAt;

  /**
   * Gets or Sets status
   */
  public enum StatusEnum {
    PENDING("PENDING"),
    
    CONFIRMED("CONFIRMED");

    private final String value;

    StatusEnum(String value) {
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
    public static StatusEnum fromValue(String value) {
      for (StatusEnum b : StatusEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  private StatusEnum status;

  private Long version;

  public AffectedBooking() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public AffectedBooking(String id, String meetingTypeId, String title, String guestName, OffsetDateTime startsAt, OffsetDateTime endsAt, StatusEnum status, Long version) {
    this.id = id;
    this.meetingTypeId = meetingTypeId;
    this.title = title;
    this.guestName = guestName;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.status = status;
    this.version = version;
  }

  public AffectedBooking id(String id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
   */
  @JsonProperty("id")
  public String getId() {
    return id;
  }

  @JsonProperty("id")
  public void setId(String id) {
    this.id = id;
  }

  public AffectedBooking meetingTypeId(String meetingTypeId) {
    this.meetingTypeId = meetingTypeId;
    return this;
  }

  /**
   * Get meetingTypeId
   * @return meetingTypeId
   */
  @JsonProperty("meetingTypeId")
  public String getMeetingTypeId() {
    return meetingTypeId;
  }

  @JsonProperty("meetingTypeId")
  public void setMeetingTypeId(String meetingTypeId) {
    this.meetingTypeId = meetingTypeId;
  }

  public AffectedBooking title(String title) {
    this.title = title;
    return this;
  }

  /**
   * Get title
   * @return title
   */
  @JsonProperty("title")
  public String getTitle() {
    return title;
  }

  @JsonProperty("title")
  public void setTitle(String title) {
    this.title = title;
  }

  public AffectedBooking guestName(String guestName) {
    this.guestName = guestName;
    return this;
  }

  /**
   * Get guestName
   * @return guestName
   */
  @JsonProperty("guestName")
  public String getGuestName() {
    return guestName;
  }

  @JsonProperty("guestName")
  public void setGuestName(String guestName) {
    this.guestName = guestName;
  }

  public AffectedBooking startsAt(OffsetDateTime startsAt) {
    this.startsAt = startsAt;
    return this;
  }

  /**
   * Get startsAt
   * @return startsAt
   */
  @JsonProperty("startsAt")
  public OffsetDateTime getStartsAt() {
    return startsAt;
  }

  @JsonProperty("startsAt")
  public void setStartsAt(OffsetDateTime startsAt) {
    this.startsAt = startsAt;
  }

  public AffectedBooking endsAt(OffsetDateTime endsAt) {
    this.endsAt = endsAt;
    return this;
  }

  /**
   * Get endsAt
   * @return endsAt
   */
  @JsonProperty("endsAt")
  public OffsetDateTime getEndsAt() {
    return endsAt;
  }

  @JsonProperty("endsAt")
  public void setEndsAt(OffsetDateTime endsAt) {
    this.endsAt = endsAt;
  }

  public AffectedBooking status(StatusEnum status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  @JsonProperty("status")
  public StatusEnum getStatus() {
    return status;
  }

  @JsonProperty("status")
  public void setStatus(StatusEnum status) {
    this.status = status;
  }

  public AffectedBooking version(Long version) {
    this.version = version;
    return this;
  }

  /**
   * Get version
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AffectedBooking affectedBooking = (AffectedBooking) o;
    return Objects.equals(this.id, affectedBooking.id) &&
        Objects.equals(this.meetingTypeId, affectedBooking.meetingTypeId) &&
        Objects.equals(this.title, affectedBooking.title) &&
        Objects.equals(this.guestName, affectedBooking.guestName) &&
        Objects.equals(this.startsAt, affectedBooking.startsAt) &&
        Objects.equals(this.endsAt, affectedBooking.endsAt) &&
        Objects.equals(this.status, affectedBooking.status) &&
        Objects.equals(this.version, affectedBooking.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, meetingTypeId, title, guestName, startsAt, endsAt, status, version);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AffectedBooking {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    meetingTypeId: ").append(toIndentedString(meetingTypeId)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    guestName: ").append(toIndentedString(guestName)).append("\n");
    sb.append("    startsAt: ").append(toIndentedString(startsAt)).append("\n");
    sb.append("    endsAt: ").append(toIndentedString(endsAt)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    version: ").append(toIndentedString(version)).append("\n");
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

