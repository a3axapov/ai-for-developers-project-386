package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Immutable future booking data. Preparation creates no booking and reserves no time.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class PrepareBookingAttemptCommand {

  private String meetingTypeId;

  private String conditionsToken;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startsAt;

  private String guestName;

  private String guestEmail;

  private @Nullable String comment;

  public PrepareBookingAttemptCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PrepareBookingAttemptCommand(String meetingTypeId, String conditionsToken, OffsetDateTime startsAt, String guestName, String guestEmail) {
    this.meetingTypeId = meetingTypeId;
    this.conditionsToken = conditionsToken;
    this.startsAt = startsAt;
    this.guestName = guestName;
    this.guestEmail = guestEmail;
  }

  public PrepareBookingAttemptCommand meetingTypeId(String meetingTypeId) {
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

  public PrepareBookingAttemptCommand conditionsToken(String conditionsToken) {
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

  public PrepareBookingAttemptCommand startsAt(OffsetDateTime startsAt) {
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

  public PrepareBookingAttemptCommand guestName(String guestName) {
    this.guestName = guestName;
    return this;
  }

  /**
   * 1–100 Unicode code points after trimming and collapsing spaces; controls and invisible characters are rejected.
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

  public PrepareBookingAttemptCommand guestEmail(String guestEmail) {
    this.guestEmail = guestEmail;
    return this;
  }

  /**
   * One syntactically valid address, at most 254 Unicode code points after trimming edge spaces.
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

  public PrepareBookingAttemptCommand comment(@Nullable String comment) {
    this.comment = comment;
    return this;
  }

  /**
   * At most 2000 Unicode code points. Newlines are allowed, other controls and invisible characters are rejected.
   * @return comment
   */
  @JsonProperty("comment")
  public @Nullable String getComment() {
    return comment;
  }

  @JsonProperty("comment")
  public void setComment(@Nullable String comment) {
    this.comment = comment;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PrepareBookingAttemptCommand prepareBookingAttemptCommand = (PrepareBookingAttemptCommand) o;
    return Objects.equals(this.meetingTypeId, prepareBookingAttemptCommand.meetingTypeId) &&
        Objects.equals(this.conditionsToken, prepareBookingAttemptCommand.conditionsToken) &&
        Objects.equals(this.startsAt, prepareBookingAttemptCommand.startsAt) &&
        Objects.equals(this.guestName, prepareBookingAttemptCommand.guestName) &&
        Objects.equals(this.guestEmail, prepareBookingAttemptCommand.guestEmail) &&
        Objects.equals(this.comment, prepareBookingAttemptCommand.comment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(meetingTypeId, conditionsToken, startsAt, guestName, guestEmail, comment);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PrepareBookingAttemptCommand {\n");
    sb.append("    meetingTypeId: ").append(toIndentedString(meetingTypeId)).append("\n");
    sb.append("    conditionsToken: ").append(toIndentedString(conditionsToken)).append("\n");
    sb.append("    startsAt: ").append(toIndentedString(startsAt)).append("\n");
    sb.append("    guestName: ").append(toIndentedString(guestName)).append("\n");
    sb.append("    guestEmail: ").append(toIndentedString(guestEmail)).append("\n");
    sb.append("    comment: ").append(toIndentedString(comment)).append("\n");
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

