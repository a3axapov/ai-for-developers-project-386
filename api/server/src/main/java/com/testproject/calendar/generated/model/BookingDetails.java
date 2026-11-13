package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
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
 * Saved booking conditions and contacts with current time, status and version.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class BookingDetails {

  private String id;

  private String meetingTypeId;

  private String ownerName;

  private String ownerTimeZone;

  private String title;

  private String description;

  private Integer durationMinutes;

  private Boolean requiresApproval;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startsAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endsAt;

  /**
   * Gets or Sets status
   */
  public enum StatusEnum {
    CONFIRMED("CONFIRMED"),
    
    PENDING("PENDING"),
    
    REJECTED("REJECTED"),
    
    CANCELLED("CANCELLED");

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

  /**
   * Gets or Sets rejectionReason
   */
  public enum RejectionReasonEnum {
    APPROVAL_EXPIRED("APPROVAL_EXPIRED");

    private final String value;

    RejectionReasonEnum(String value) {
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
    public static RejectionReasonEnum fromValue(String value) {
      for (RejectionReasonEnum b : RejectionReasonEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  private @Nullable RejectionReasonEnum rejectionReason;

  private String guestName;

  private String guestEmail;

  private String comment;

  private Long version;

  private List<String> allowedActions;

  public BookingDetails() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public BookingDetails(String id, String meetingTypeId, String ownerName, String ownerTimeZone, String title, String description, Integer durationMinutes, Boolean requiresApproval, OffsetDateTime startsAt, OffsetDateTime endsAt, StatusEnum status, String guestName, String guestEmail, String comment, Long version, List<String> allowedActions) {
    this.id = id;
    this.meetingTypeId = meetingTypeId;
    this.ownerName = ownerName;
    this.ownerTimeZone = ownerTimeZone;
    this.title = title;
    this.description = description;
    this.durationMinutes = durationMinutes;
    this.requiresApproval = requiresApproval;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.status = status;
    this.guestName = guestName;
    this.guestEmail = guestEmail;
    this.comment = comment;
    this.version = version;
    this.allowedActions = allowedActions;
  }

  public BookingDetails id(String id) {
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

  public BookingDetails meetingTypeId(String meetingTypeId) {
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

  public BookingDetails ownerName(String ownerName) {
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

  public BookingDetails ownerTimeZone(String ownerTimeZone) {
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

  public BookingDetails title(String title) {
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

  public BookingDetails description(String description) {
    this.description = description;
    return this;
  }

  /**
   * Get description
   * @return description
   */
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }

  @JsonProperty("description")
  public void setDescription(String description) {
    this.description = description;
  }

  public BookingDetails durationMinutes(Integer durationMinutes) {
    this.durationMinutes = durationMinutes;
    return this;
  }

  /**
   * Get durationMinutes
   * @return durationMinutes
   */
  @JsonProperty("durationMinutes")
  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  @JsonProperty("durationMinutes")
  public void setDurationMinutes(Integer durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  public BookingDetails requiresApproval(Boolean requiresApproval) {
    this.requiresApproval = requiresApproval;
    return this;
  }

  /**
   * Get requiresApproval
   * @return requiresApproval
   */
  @JsonProperty("requiresApproval")
  public Boolean getRequiresApproval() {
    return requiresApproval;
  }

  @JsonProperty("requiresApproval")
  public void setRequiresApproval(Boolean requiresApproval) {
    this.requiresApproval = requiresApproval;
  }

  public BookingDetails startsAt(OffsetDateTime startsAt) {
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

  public BookingDetails endsAt(OffsetDateTime endsAt) {
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

  public BookingDetails status(StatusEnum status) {
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

  public BookingDetails rejectionReason(@Nullable RejectionReasonEnum rejectionReason) {
    this.rejectionReason = rejectionReason;
    return this;
  }

  /**
   * Get rejectionReason
   * @return rejectionReason
   */
  @JsonProperty("rejectionReason")
  public @Nullable RejectionReasonEnum getRejectionReason() {
    return rejectionReason;
  }

  @JsonProperty("rejectionReason")
  public void setRejectionReason(@Nullable RejectionReasonEnum rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public BookingDetails guestName(String guestName) {
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

  public BookingDetails guestEmail(String guestEmail) {
    this.guestEmail = guestEmail;
    return this;
  }

  /**
   * Get guestEmail
   * @return guestEmail
   */
  @JsonProperty("guestEmail")
  public String getGuestEmail() {
    return guestEmail;
  }

  @JsonProperty("guestEmail")
  public void setGuestEmail(String guestEmail) {
    this.guestEmail = guestEmail;
  }

  public BookingDetails comment(String comment) {
    this.comment = comment;
    return this;
  }

  /**
   * Get comment
   * @return comment
   */
  @JsonProperty("comment")
  public String getComment() {
    return comment;
  }

  @JsonProperty("comment")
  public void setComment(String comment) {
    this.comment = comment;
  }

  public BookingDetails version(Long version) {
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

  public BookingDetails allowedActions(List<String> allowedActions) {
    this.allowedActions = allowedActions;
    return this;
  }

  public BookingDetails addAllowedActionsItem(String allowedActionsItem) {
    if (this.allowedActions == null) {
      this.allowedActions = new ArrayList<>();
    }
    this.allowedActions.add(allowedActionsItem);
    return this;
  }

  /**
   * Get allowedActions
   * @return allowedActions
   */
  @JsonProperty("allowedActions")
  public List<String> getAllowedActions() {
    return allowedActions;
  }

  @JsonProperty("allowedActions")
  public void setAllowedActions(List<String> allowedActions) {
    this.allowedActions = allowedActions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BookingDetails bookingDetails = (BookingDetails) o;
    return Objects.equals(this.id, bookingDetails.id) &&
        Objects.equals(this.meetingTypeId, bookingDetails.meetingTypeId) &&
        Objects.equals(this.ownerName, bookingDetails.ownerName) &&
        Objects.equals(this.ownerTimeZone, bookingDetails.ownerTimeZone) &&
        Objects.equals(this.title, bookingDetails.title) &&
        Objects.equals(this.description, bookingDetails.description) &&
        Objects.equals(this.durationMinutes, bookingDetails.durationMinutes) &&
        Objects.equals(this.requiresApproval, bookingDetails.requiresApproval) &&
        Objects.equals(this.startsAt, bookingDetails.startsAt) &&
        Objects.equals(this.endsAt, bookingDetails.endsAt) &&
        Objects.equals(this.status, bookingDetails.status) &&
        Objects.equals(this.rejectionReason, bookingDetails.rejectionReason) &&
        Objects.equals(this.guestName, bookingDetails.guestName) &&
        Objects.equals(this.guestEmail, bookingDetails.guestEmail) &&
        Objects.equals(this.comment, bookingDetails.comment) &&
        Objects.equals(this.version, bookingDetails.version) &&
        Objects.equals(this.allowedActions, bookingDetails.allowedActions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, meetingTypeId, ownerName, ownerTimeZone, title, description, durationMinutes, requiresApproval, startsAt, endsAt, status, rejectionReason, guestName, guestEmail, comment, version, allowedActions);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class BookingDetails {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    meetingTypeId: ").append(toIndentedString(meetingTypeId)).append("\n");
    sb.append("    ownerName: ").append(toIndentedString(ownerName)).append("\n");
    sb.append("    ownerTimeZone: ").append(toIndentedString(ownerTimeZone)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    durationMinutes: ").append(toIndentedString(durationMinutes)).append("\n");
    sb.append("    requiresApproval: ").append(toIndentedString(requiresApproval)).append("\n");
    sb.append("    startsAt: ").append(toIndentedString(startsAt)).append("\n");
    sb.append("    endsAt: ").append(toIndentedString(endsAt)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    rejectionReason: ").append(toIndentedString(rejectionReason)).append("\n");
    sb.append("    guestName: ").append(toIndentedString(guestName)).append("\n");
    sb.append("    guestEmail: ").append(toIndentedString(guestEmail)).append("\n");
    sb.append("    comment: ").append(toIndentedString(comment)).append("\n");
    sb.append("    version: ").append(toIndentedString(version)).append("\n");
    sb.append("    allowedActions: ").append(toIndentedString(allowedActions)).append("\n");
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

