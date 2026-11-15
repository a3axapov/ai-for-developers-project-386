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
 * OutOfOffice
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class OutOfOffice {

  private String id;

  private String startDate;

  private String endDate;

  private String timeZone;

  private Long version;

  public OutOfOffice() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public OutOfOffice(String id, String startDate, String endDate, String timeZone, Long version) {
    this.id = id;
    this.startDate = startDate;
    this.endDate = endDate;
    this.timeZone = timeZone;
    this.version = version;
  }

  public OutOfOffice id(String id) {
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

  public OutOfOffice startDate(String startDate) {
    this.startDate = startDate;
    return this;
  }

  /**
   * Get startDate
   * @return startDate
   */
  @JsonProperty("startDate")
  public String getStartDate() {
    return startDate;
  }

  @JsonProperty("startDate")
  public void setStartDate(String startDate) {
    this.startDate = startDate;
  }

  public OutOfOffice endDate(String endDate) {
    this.endDate = endDate;
    return this;
  }

  /**
   * Get endDate
   * @return endDate
   */
  @JsonProperty("endDate")
  public String getEndDate() {
    return endDate;
  }

  @JsonProperty("endDate")
  public void setEndDate(String endDate) {
    this.endDate = endDate;
  }

  public OutOfOffice timeZone(String timeZone) {
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

  public OutOfOffice version(Long version) {
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
    OutOfOffice outOfOffice = (OutOfOffice) o;
    return Objects.equals(this.id, outOfOffice.id) &&
        Objects.equals(this.startDate, outOfOffice.startDate) &&
        Objects.equals(this.endDate, outOfOffice.endDate) &&
        Objects.equals(this.timeZone, outOfOffice.timeZone) &&
        Objects.equals(this.version, outOfOffice.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, startDate, endDate, timeZone, version);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OutOfOffice {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    startDate: ").append(toIndentedString(startDate)).append("\n");
    sb.append("    endDate: ").append(toIndentedString(endDate)).append("\n");
    sb.append("    timeZone: ").append(toIndentedString(timeZone)).append("\n");
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

