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
 * UpdateMeetingTypeCommand
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class UpdateMeetingTypeCommand {

  private String title;

  private String description;

  private Integer durationMinutes;

  private Boolean requiresApproval;

  private Long expectedVersion;

  public UpdateMeetingTypeCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public UpdateMeetingTypeCommand(String title, String description, Integer durationMinutes, Boolean requiresApproval, Long expectedVersion) {
    this.title = title;
    this.description = description;
    this.durationMinutes = durationMinutes;
    this.requiresApproval = requiresApproval;
    this.expectedVersion = expectedVersion;
  }

  public UpdateMeetingTypeCommand title(String title) {
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

  public UpdateMeetingTypeCommand description(String description) {
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

  public UpdateMeetingTypeCommand durationMinutes(Integer durationMinutes) {
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

  public UpdateMeetingTypeCommand requiresApproval(Boolean requiresApproval) {
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

  public UpdateMeetingTypeCommand expectedVersion(Long expectedVersion) {
    this.expectedVersion = expectedVersion;
    return this;
  }

  /**
   * Get expectedVersion
   * @return expectedVersion
   */
  @JsonProperty("expectedVersion")
  public Long getExpectedVersion() {
    return expectedVersion;
  }

  @JsonProperty("expectedVersion")
  public void setExpectedVersion(Long expectedVersion) {
    this.expectedVersion = expectedVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UpdateMeetingTypeCommand updateMeetingTypeCommand = (UpdateMeetingTypeCommand) o;
    return Objects.equals(this.title, updateMeetingTypeCommand.title) &&
        Objects.equals(this.description, updateMeetingTypeCommand.description) &&
        Objects.equals(this.durationMinutes, updateMeetingTypeCommand.durationMinutes) &&
        Objects.equals(this.requiresApproval, updateMeetingTypeCommand.requiresApproval) &&
        Objects.equals(this.expectedVersion, updateMeetingTypeCommand.expectedVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(title, description, durationMinutes, requiresApproval, expectedVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateMeetingTypeCommand {\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    durationMinutes: ").append(toIndentedString(durationMinutes)).append("\n");
    sb.append("    requiresApproval: ").append(toIndentedString(requiresApproval)).append("\n");
    sb.append("    expectedVersion: ").append(toIndentedString(expectedVersion)).append("\n");
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

