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
 * CreateMeetingTypeCommand
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class CreateMeetingTypeCommand {

  private String id;

  private String title;

  private String description;

  private Integer durationMinutes;

  private Boolean requiresApproval = false;

  public CreateMeetingTypeCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CreateMeetingTypeCommand(String id, String title, String description, Integer durationMinutes) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.durationMinutes = durationMinutes;
  }

  public CreateMeetingTypeCommand id(String id) {
    this.id = id;
    return this;
  }

  /**
   * Immutable, case-sensitive ID: 1–64 ASCII letters, digits or hyphens.
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

  public CreateMeetingTypeCommand title(String title) {
    this.title = title;
    return this;
  }

  /**
   * Nonempty title after collapsing Unicode space separators and trimming. Nonprintable characters are rejected.
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

  public CreateMeetingTypeCommand description(String description) {
    this.description = description;
    return this;
  }

  /**
   * At least 20 counted Unicode code points after normalizing spaces per line. Internal newlines remain; newlines, control and invisible characters do not count toward the minimum.
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

  public CreateMeetingTypeCommand durationMinutes(Integer durationMinutes) {
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

  public CreateMeetingTypeCommand requiresApproval(Boolean requiresApproval) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreateMeetingTypeCommand createMeetingTypeCommand = (CreateMeetingTypeCommand) o;
    return Objects.equals(this.id, createMeetingTypeCommand.id) &&
        Objects.equals(this.title, createMeetingTypeCommand.title) &&
        Objects.equals(this.description, createMeetingTypeCommand.description) &&
        Objects.equals(this.durationMinutes, createMeetingTypeCommand.durationMinutes) &&
        Objects.equals(this.requiresApproval, createMeetingTypeCommand.requiresApproval);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, description, durationMinutes, requiresApproval);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateMeetingTypeCommand {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    durationMinutes: ").append(toIndentedString(durationMinutes)).append("\n");
    sb.append("    requiresApproval: ").append(toIndentedString(requiresApproval)).append("\n");
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

