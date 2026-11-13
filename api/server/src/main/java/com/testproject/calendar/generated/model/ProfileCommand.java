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
 * ProfileCommand
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class ProfileCommand {

  private String name;

  private String username;

  private Long expectedVersion;

  public ProfileCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ProfileCommand(String name, String username, Long expectedVersion) {
    this.name = name;
    this.username = username;
    this.expectedVersion = expectedVersion;
  }

  public ProfileCommand name(String name) {
    this.name = name;
    return this;
  }

  /**
   * 1–100 Unicode code points after trimming and collapsing Unicode space separators. Control, nonprintable and invisible control characters are rejected before normalization.
   * @return name
   */
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @JsonProperty("name")
  public void setName(String name) {
    this.name = name;
  }

  public ProfileCommand username(String username) {
    this.username = username;
    return this;
  }

  /**
   * Trim edge space separators and lowercase; 3–30 ASCII letters, digits or hyphens, with alphanumeric boundaries.
   * @return username
   */
  @JsonProperty("username")
  public String getUsername() {
    return username;
  }

  @JsonProperty("username")
  public void setUsername(String username) {
    this.username = username;
  }

  public ProfileCommand expectedVersion(Long expectedVersion) {
    this.expectedVersion = expectedVersion;
    return this;
  }

  /**
   * Get expectedVersion
   * minimum: 1
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
    ProfileCommand profileCommand = (ProfileCommand) o;
    return Objects.equals(this.name, profileCommand.name) &&
        Objects.equals(this.username, profileCommand.username) &&
        Objects.equals(this.expectedVersion, profileCommand.expectedVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, username, expectedVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ProfileCommand {\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
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

