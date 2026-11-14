package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.BookingDetails;
import com.testproject.calendar.generated.model.FieldError;
import com.testproject.calendar.generated.model.OwnerProfile;
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

  private @Nullable List<FieldError> fieldErrors;

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
        Objects.equals(this.fieldErrors, apiError.fieldErrors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, currentProfile, currentBooking, fieldErrors);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ApiError {\n");
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    currentProfile: ").append(toIndentedString(currentProfile)).append("\n");
    sb.append("    currentBooking: ").append(toIndentedString(currentBooking)).append("\n");
    sb.append("    fieldErrors: ").append(toIndentedString(fieldErrors)).append("\n");
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

