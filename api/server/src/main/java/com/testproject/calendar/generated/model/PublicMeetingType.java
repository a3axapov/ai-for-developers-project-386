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
 * PublicMeetingType
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class PublicMeetingType {

  private String id;

  private String title;

  private String description;

  private Integer durationMinutes;

  private Boolean requiresApproval;

  private String timeZone;

  private String conditionsToken;

  public PublicMeetingType() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PublicMeetingType(String id, String title, String description, Integer durationMinutes, Boolean requiresApproval, String timeZone, String conditionsToken) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.durationMinutes = durationMinutes;
    this.requiresApproval = requiresApproval;
    this.timeZone = timeZone;
    this.conditionsToken = conditionsToken;
  }

  public PublicMeetingType id(String id) {
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

  public PublicMeetingType title(String title) {
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

  public PublicMeetingType description(String description) {
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

  public PublicMeetingType durationMinutes(Integer durationMinutes) {
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

  public PublicMeetingType requiresApproval(Boolean requiresApproval) {
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

  public PublicMeetingType timeZone(String timeZone) {
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

  public PublicMeetingType conditionsToken(String conditionsToken) {
    this.conditionsToken = conditionsToken;
    return this;
  }

  /**
   * Content-derived token over title, description, durationMinutes and requiresApproval only.
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PublicMeetingType publicMeetingType = (PublicMeetingType) o;
    return Objects.equals(this.id, publicMeetingType.id) &&
        Objects.equals(this.title, publicMeetingType.title) &&
        Objects.equals(this.description, publicMeetingType.description) &&
        Objects.equals(this.durationMinutes, publicMeetingType.durationMinutes) &&
        Objects.equals(this.requiresApproval, publicMeetingType.requiresApproval) &&
        Objects.equals(this.timeZone, publicMeetingType.timeZone) &&
        Objects.equals(this.conditionsToken, publicMeetingType.conditionsToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, description, durationMinutes, requiresApproval, timeZone, conditionsToken);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PublicMeetingType {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    durationMinutes: ").append(toIndentedString(durationMinutes)).append("\n");
    sb.append("    requiresApproval: ").append(toIndentedString(requiresApproval)).append("\n");
    sb.append("    timeZone: ").append(toIndentedString(timeZone)).append("\n");
    sb.append("    conditionsToken: ").append(toIndentedString(conditionsToken)).append("\n");
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

