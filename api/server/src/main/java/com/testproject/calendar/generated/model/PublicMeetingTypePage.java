package com.testproject.calendar.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.testproject.calendar.generated.model.PublicMeetingType;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * PublicMeetingTypePage
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.27.0")
public class PublicMeetingTypePage {

  private String ownerName;

  private String ownerTimeZone;

  /**
   * Gets or Sets state
   */
  public enum StateEnum {
    BOOKING_DISABLED("BOOKING_DISABLED"),
    
    OPEN("OPEN");

    private final String value;

    StateEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static StateEnum fromValue(String value) {
      for (StateEnum b : StateEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  private StateEnum state;

  private @Nullable PublicMeetingType meetingType;

  public PublicMeetingTypePage() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PublicMeetingTypePage(String ownerName, String ownerTimeZone, StateEnum state) {
    this.ownerName = ownerName;
    this.ownerTimeZone = ownerTimeZone;
    this.state = state;
  }

  public PublicMeetingTypePage ownerName(String ownerName) {
    this.ownerName = ownerName;
    return this;
  }

  /**
   * Get ownerName
   * @return ownerName
   */
  @JsonProperty("ownerName")
  public String getOwnerName() {
    return ownerName;
  }

  @JsonProperty("ownerName")
  public void setOwnerName(String ownerName) {
    this.ownerName = ownerName;
  }

  public PublicMeetingTypePage ownerTimeZone(String ownerTimeZone) {
    this.ownerTimeZone = ownerTimeZone;
    return this;
  }

  /**
   * Get ownerTimeZone
   * @return ownerTimeZone
   */
  @JsonProperty("ownerTimeZone")
  public String getOwnerTimeZone() {
    return ownerTimeZone;
  }

  @JsonProperty("ownerTimeZone")
  public void setOwnerTimeZone(String ownerTimeZone) {
    this.ownerTimeZone = ownerTimeZone;
  }

  public PublicMeetingTypePage state(StateEnum state) {
    this.state = state;
    return this;
  }

  /**
   * Get state
   * @return state
   */
  @JsonProperty("state")
  public StateEnum getState() {
    return state;
  }

  @JsonProperty("state")
  public void setState(StateEnum state) {
    this.state = state;
  }

  public PublicMeetingTypePage meetingType(@Nullable PublicMeetingType meetingType) {
    this.meetingType = meetingType;
    return this;
  }

  /**
   * Present only when OPEN.
   * @return meetingType
   */
  @JsonProperty("meetingType")
  public @Nullable PublicMeetingType getMeetingType() {
    return meetingType;
  }

  @JsonProperty("meetingType")
  public void setMeetingType(@Nullable PublicMeetingType meetingType) {
    this.meetingType = meetingType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PublicMeetingTypePage publicMeetingTypePage = (PublicMeetingTypePage) o;
    return Objects.equals(this.ownerName, publicMeetingTypePage.ownerName) &&
        Objects.equals(this.ownerTimeZone, publicMeetingTypePage.ownerTimeZone) &&
        Objects.equals(this.state, publicMeetingTypePage.state) &&
        Objects.equals(this.meetingType, publicMeetingTypePage.meetingType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ownerName, ownerTimeZone, state, meetingType);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PublicMeetingTypePage {\n");
    sb.append("    ownerName: ").append(toIndentedString(ownerName)).append("\n");
    sb.append("    ownerTimeZone: ").append(toIndentedString(ownerTimeZone)).append("\n");
    sb.append("    state: ").append(toIndentedString(state)).append("\n");
    sb.append("    meetingType: ").append(toIndentedString(meetingType)).append("\n");
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

