package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.testproject.calendar.generated.model.DateOverride;
import com.testproject.calendar.generated.model.WorkingDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * MeetingType
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class MeetingType {

  private String id;

  private String title;

  private String description;

  private Integer durationMinutes;

  private Boolean requiresApproval;

  private String timeZone;

  private Long version;

  private Boolean archived;

  private List<WorkingDay> days;

  private List<DateOverride> dateOverrides;

  public MeetingType() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public MeetingType(String id, String title, String description, Integer durationMinutes, Boolean requiresApproval, String timeZone, Long version, Boolean archived, List<WorkingDay> days, List<DateOverride> dateOverrides) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.durationMinutes = durationMinutes;
    this.requiresApproval = requiresApproval;
    this.timeZone = timeZone;
    this.version = version;
    this.archived = archived;
    this.days = days;
    this.dateOverrides = dateOverrides;
  }

  public MeetingType id(String id) {
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

  public MeetingType title(String title) {
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

  public MeetingType description(String description) {
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

  public MeetingType durationMinutes(Integer durationMinutes) {
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

  public MeetingType requiresApproval(Boolean requiresApproval) {
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

  public MeetingType timeZone(String timeZone) {
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

  public MeetingType version(Long version) {
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

  public MeetingType archived(Boolean archived) {
    this.archived = archived;
    return this;
  }

  /**
   * Get archived
   * @return archived
   */
  @JsonProperty("archived")
  public Boolean getArchived() {
    return archived;
  }

  @JsonProperty("archived")
  public void setArchived(Boolean archived) {
    this.archived = archived;
  }

  public MeetingType days(List<WorkingDay> days) {
    this.days = days;
    return this;
  }

  public MeetingType addDaysItem(WorkingDay daysItem) {
    if (this.days == null) {
      this.days = new ArrayList<>();
    }
    this.days.add(daysItem);
    return this;
  }

  /**
   * Get days
   * @return days
   */
  @JsonProperty("days")
  public List<WorkingDay> getDays() {
    return days;
  }

  @JsonProperty("days")
  public void setDays(List<WorkingDay> days) {
    this.days = days;
  }

  public MeetingType dateOverrides(List<DateOverride> dateOverrides) {
    this.dateOverrides = dateOverrides;
    return this;
  }

  public MeetingType addDateOverridesItem(DateOverride dateOverridesItem) {
    if (this.dateOverrides == null) {
      this.dateOverrides = new ArrayList<>();
    }
    this.dateOverrides.add(dateOverridesItem);
    return this;
  }

  /**
   * Get dateOverrides
   * @return dateOverrides
   */
  @JsonProperty("dateOverrides")
  public List<DateOverride> getDateOverrides() {
    return dateOverrides;
  }

  @JsonProperty("dateOverrides")
  public void setDateOverrides(List<DateOverride> dateOverrides) {
    this.dateOverrides = dateOverrides;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MeetingType meetingType = (MeetingType) o;
    return Objects.equals(this.id, meetingType.id) &&
        Objects.equals(this.title, meetingType.title) &&
        Objects.equals(this.description, meetingType.description) &&
        Objects.equals(this.durationMinutes, meetingType.durationMinutes) &&
        Objects.equals(this.requiresApproval, meetingType.requiresApproval) &&
        Objects.equals(this.timeZone, meetingType.timeZone) &&
        Objects.equals(this.version, meetingType.version) &&
        Objects.equals(this.archived, meetingType.archived) &&
        Objects.equals(this.days, meetingType.days) &&
        Objects.equals(this.dateOverrides, meetingType.dateOverrides);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, description, durationMinutes, requiresApproval, timeZone, version, archived, days, dateOverrides);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class MeetingType {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    durationMinutes: ").append(toIndentedString(durationMinutes)).append("\n");
    sb.append("    requiresApproval: ").append(toIndentedString(requiresApproval)).append("\n");
    sb.append("    timeZone: ").append(toIndentedString(timeZone)).append("\n");
    sb.append("    version: ").append(toIndentedString(version)).append("\n");
    sb.append("    archived: ").append(toIndentedString(archived)).append("\n");
    sb.append("    days: ").append(toIndentedString(days)).append("\n");
    sb.append("    dateOverrides: ").append(toIndentedString(dateOverrides)).append("\n");
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

