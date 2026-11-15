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
 * DeleteTimeBlockCommand
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class DeleteTimeBlockCommand {

  private Long expectedVersion;

  public DeleteTimeBlockCommand() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeleteTimeBlockCommand(Long expectedVersion) {
    this.expectedVersion = expectedVersion;
  }

  public DeleteTimeBlockCommand expectedVersion(Long expectedVersion) {
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
    DeleteTimeBlockCommand deleteTimeBlockCommand = (DeleteTimeBlockCommand) o;
    return Objects.equals(this.expectedVersion, deleteTimeBlockCommand.expectedVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(expectedVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeleteTimeBlockCommand {\n");
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

