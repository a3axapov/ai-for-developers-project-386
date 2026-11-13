package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.ApiError;
import com.testproject.calendar.generated.model.MeetingType;
import com.testproject.calendar.generated.model.OwnerProfile;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * The saved, definite result of an owner command. Exactly one of profile, meetingType or error is present.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class OwnerOperationResult {

  private Integer statusCode;

  private @Nullable OwnerProfile profile;

  private @Nullable MeetingType meetingType;

  private @Nullable ApiError error;

  public OwnerOperationResult() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public OwnerOperationResult(Integer statusCode) {
    this.statusCode = statusCode;
  }

  public OwnerOperationResult statusCode(Integer statusCode) {
    this.statusCode = statusCode;
    return this;
  }

  /**
   * Get statusCode
   * @return statusCode
   */
  @JsonProperty("statusCode")
  public Integer getStatusCode() {
    return statusCode;
  }

  @JsonProperty("statusCode")
  public void setStatusCode(Integer statusCode) {
    this.statusCode = statusCode;
  }

  public OwnerOperationResult profile(@Nullable OwnerProfile profile) {
    this.profile = profile;
    return this;
  }

  /**
   * Get profile
   * @return profile
   */
  @JsonProperty("profile")
  public @Nullable OwnerProfile getProfile() {
    return profile;
  }

  @JsonProperty("profile")
  public void setProfile(@Nullable OwnerProfile profile) {
    this.profile = profile;
  }

  public OwnerOperationResult meetingType(@Nullable MeetingType meetingType) {
    this.meetingType = meetingType;
    return this;
  }

  /**
   * Get meetingType
   * @return meetingType
   */
  @JsonProperty("meetingType")
  public @Nullable MeetingType getMeetingType() {
    return meetingType;
  }

  @JsonProperty("meetingType")
  public void setMeetingType(@Nullable MeetingType meetingType) {
    this.meetingType = meetingType;
  }

  public OwnerOperationResult error(@Nullable ApiError error) {
    this.error = error;
    return this;
  }

  /**
   * Get error
   * @return error
   */
  @JsonProperty("error")
  public @Nullable ApiError getError() {
    return error;
  }

  @JsonProperty("error")
  public void setError(@Nullable ApiError error) {
    this.error = error;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OwnerOperationResult ownerOperationResult = (OwnerOperationResult) o;
    return Objects.equals(this.statusCode, ownerOperationResult.statusCode) &&
        Objects.equals(this.profile, ownerOperationResult.profile) &&
        Objects.equals(this.meetingType, ownerOperationResult.meetingType) &&
        Objects.equals(this.error, ownerOperationResult.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(statusCode, profile, meetingType, error);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OwnerOperationResult {\n");
    sb.append("    statusCode: ").append(toIndentedString(statusCode)).append("\n");
    sb.append("    profile: ").append(toIndentedString(profile)).append("\n");
    sb.append("    meetingType: ").append(toIndentedString(meetingType)).append("\n");
    sb.append("    error: ").append(toIndentedString(error)).append("\n");
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

