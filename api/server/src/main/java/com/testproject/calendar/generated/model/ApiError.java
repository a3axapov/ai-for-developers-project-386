package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.AffectedBooking;
import com.testproject.calendar.generated.model.BookingDetails;
import com.testproject.calendar.generated.model.FieldError;
import com.testproject.calendar.generated.model.MeetingType;
import com.testproject.calendar.generated.model.OutOfOffice;
import com.testproject.calendar.generated.model.OwnerProfile;
import com.testproject.calendar.generated.model.PublicMeetingType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * ApiError
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class ApiError {

  private String code;

  private String message;

  private @Nullable OwnerProfile currentProfile;

  private @Nullable BookingDetails currentBooking;

  private @Nullable MeetingType currentMeetingType;

  private @Nullable OutOfOffice currentOutOfOffice;

  private @Nullable PublicMeetingType currentConditions;

  private @Nullable List<FieldError> fieldErrors;

  private @Nullable List<AffectedBooking> affectedBookings;

  private @Nullable String confirmationToken;

  public ApiError() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ApiError(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public ApiError code(String code) {
    this.code = code;
    return this;
  }

  /**
   * Get code
   * @return code
   */
  @JsonProperty("code")
  public String getCode() {
    return code;
  }

  @JsonProperty("code")
  public void setCode(String code) {
    this.code = code;
  }

  public ApiError message(String message) {
    this.message = message;
    return this;
  }

  /**
   * Get message
   * @return message
   */
  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  @JsonProperty("message")
  public void setMessage(String message) {
    this.message = message;
  }

  public ApiError currentProfile(@Nullable OwnerProfile currentProfile) {
    this.currentProfile = currentProfile;
    return this;
  }

  /**
   * Get currentProfile
   * @return currentProfile
   */
  @JsonProperty("currentProfile")
  public @Nullable OwnerProfile getCurrentProfile() {
    return currentProfile;
  }

  @JsonProperty("currentProfile")
  public void setCurrentProfile(@Nullable OwnerProfile currentProfile) {
    this.currentProfile = currentProfile;
  }

  public ApiError currentBooking(@Nullable BookingDetails currentBooking) {
    this.currentBooking = currentBooking;
    return this;
  }

  /**
   * Get currentBooking
   * @return currentBooking
   */
  @JsonProperty("currentBooking")
  public @Nullable BookingDetails getCurrentBooking() {
    return currentBooking;
  }

  @JsonProperty("currentBooking")
  public void setCurrentBooking(@Nullable BookingDetails currentBooking) {
    this.currentBooking = currentBooking;
  }

  public ApiError currentMeetingType(@Nullable MeetingType currentMeetingType) {
    this.currentMeetingType = currentMeetingType;
    return this;
  }

  /**
   * Get currentMeetingType
   * @return currentMeetingType
   */
  @JsonProperty("currentMeetingType")
  public @Nullable MeetingType getCurrentMeetingType() {
    return currentMeetingType;
  }

  @JsonProperty("currentMeetingType")
  public void setCurrentMeetingType(@Nullable MeetingType currentMeetingType) {
    this.currentMeetingType = currentMeetingType;
  }

  public ApiError currentOutOfOffice(@Nullable OutOfOffice currentOutOfOffice) {
    this.currentOutOfOffice = currentOutOfOffice;
    return this;
  }

  /**
   * Get currentOutOfOffice
   * @return currentOutOfOffice
   */
  @JsonProperty("currentOutOfOffice")
  public @Nullable OutOfOffice getCurrentOutOfOffice() {
    return currentOutOfOffice;
  }

  @JsonProperty("currentOutOfOffice")
  public void setCurrentOutOfOffice(@Nullable OutOfOffice currentOutOfOffice) {
    this.currentOutOfOffice = currentOutOfOffice;
  }

  public ApiError currentConditions(@Nullable PublicMeetingType currentConditions) {
    this.currentConditions = currentConditions;
    return this;
  }

  /**
   * Get currentConditions
   * @return currentConditions
   */
  @JsonProperty("currentConditions")
  public @Nullable PublicMeetingType getCurrentConditions() {
    return currentConditions;
  }

  @JsonProperty("currentConditions")
  public void setCurrentConditions(@Nullable PublicMeetingType currentConditions) {
    this.currentConditions = currentConditions;
  }

  public ApiError fieldErrors(@Nullable List<FieldError> fieldErrors) {
    this.fieldErrors = fieldErrors;
    return this;
  }

  public ApiError addFieldErrorsItem(FieldError fieldErrorsItem) {
    if (this.fieldErrors == null) {
      this.fieldErrors = new ArrayList<>();
    }
    this.fieldErrors.add(fieldErrorsItem);
    return this;
  }

  /**
   * Get fieldErrors
   * @return fieldErrors
   */
  @JsonProperty("fieldErrors")
  public @Nullable List<FieldError> getFieldErrors() {
    return fieldErrors;
  }

  @JsonProperty("fieldErrors")
  public void setFieldErrors(@Nullable List<FieldError> fieldErrors) {
    this.fieldErrors = fieldErrors;
  }

  public ApiError affectedBookings(@Nullable List<AffectedBooking> affectedBookings) {
    this.affectedBookings = affectedBookings;
    return this;
  }

  public ApiError addAffectedBookingsItem(AffectedBooking affectedBookingsItem) {
    if (this.affectedBookings == null) {
      this.affectedBookings = new ArrayList<>();
    }
    this.affectedBookings.add(affectedBookingsItem);
    return this;
  }

  /**
   * Get affectedBookings
   * @return affectedBookings
   */
  @JsonProperty("affectedBookings")
  public @Nullable List<AffectedBooking> getAffectedBookings() {
    return affectedBookings;
  }

  @JsonProperty("affectedBookings")
  public void setAffectedBookings(@Nullable List<AffectedBooking> affectedBookings) {
    this.affectedBookings = affectedBookings;
  }

  public ApiError confirmationToken(@Nullable String confirmationToken) {
    this.confirmationToken = confirmationToken;
    return this;
  }

  /**
   * Get confirmationToken
   * @return confirmationToken
   */
  @JsonProperty("confirmationToken")
  public @Nullable String getConfirmationToken() {
    return confirmationToken;
  }

  @JsonProperty("confirmationToken")
  public void setConfirmationToken(@Nullable String confirmationToken) {
    this.confirmationToken = confirmationToken;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ApiError apiError = (ApiError) o;
    return Objects.equals(this.code, apiError.code) &&
        Objects.equals(this.message, apiError.message) &&
        Objects.equals(this.currentProfile, apiError.currentProfile) &&
        Objects.equals(this.currentBooking, apiError.currentBooking) &&
        Objects.equals(this.currentMeetingType, apiError.currentMeetingType) &&
        Objects.equals(this.currentOutOfOffice, apiError.currentOutOfOffice) &&
        Objects.equals(this.currentConditions, apiError.currentConditions) &&
        Objects.equals(this.fieldErrors, apiError.fieldErrors) &&
        Objects.equals(this.affectedBookings, apiError.affectedBookings) &&
        Objects.equals(this.confirmationToken, apiError.confirmationToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, currentProfile, currentBooking, currentMeetingType, currentOutOfOffice, currentConditions, fieldErrors, affectedBookings, confirmationToken);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ApiError {\n");
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    currentProfile: ").append(toIndentedString(currentProfile)).append("\n");
    sb.append("    currentBooking: ").append(toIndentedString(currentBooking)).append("\n");
    sb.append("    currentMeetingType: ").append(toIndentedString(currentMeetingType)).append("\n");
    sb.append("    currentOutOfOffice: ").append(toIndentedString(currentOutOfOffice)).append("\n");
    sb.append("    currentConditions: ").append(toIndentedString(currentConditions)).append("\n");
    sb.append("    fieldErrors: ").append(toIndentedString(fieldErrors)).append("\n");
    sb.append("    affectedBookings: ").append(toIndentedString(affectedBookings)).append("\n");
    sb.append("    confirmationToken: ").append(toIndentedString(confirmationToken)).append("\n");
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

