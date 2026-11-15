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
 * TimeBlock
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class TimeBlock {

  private String id;

  private String title;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startsAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endsAt;

  private Long version;

  public TimeBlock() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public TimeBlock(String id, String title, OffsetDateTime startsAt, OffsetDateTime endsAt, Long version) {
    this.id = id;
    this.title = title;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.version = version;
  }

  public TimeBlock id(String id) {
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

  public TimeBlock title(String title) {
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

  public TimeBlock startsAt(OffsetDateTime startsAt) {
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

  public TimeBlock endsAt(OffsetDateTime endsAt) {
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

  public TimeBlock version(Long version) {
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
    TimeBlock timeBlock = (TimeBlock) o;
    return Objects.equals(this.id, timeBlock.id) &&
        Objects.equals(this.title, timeBlock.title) &&
        Objects.equals(this.startsAt, timeBlock.startsAt) &&
        Objects.equals(this.endsAt, timeBlock.endsAt) &&
        Objects.equals(this.version, timeBlock.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, startsAt, endsAt, version);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TimeBlock {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    startsAt: ").append(toIndentedString(startsAt)).append("\n");
    sb.append("    endsAt: ").append(toIndentedString(endsAt)).append("\n");
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

