import "@typespec/http";
import "@typespec/openapi";
import "@typespec/openapi3";

using TypeSpec.Http;
using TypeSpec.OpenAPI;

@service(#{ title: "Calendar API" })
@route("/api")
namespace Calendar;

@doc("Application health response.")
model Health {
  @doc("The application is ready to accept requests.")
  status: "UP";
}

@get
@route("/health")
@operationId("getHealth")
@doc("Check that the application is running.")
op getHealth(): Health;

@doc("The single calendar owner's current profile.")
model OwnerProfile {
  name: string;
  username: string;
  timeZone: string;
  bookingEnabled: boolean;

  @minValue(1)
  version: int64;

  @doc("Same-origin public booking page path.")
  bookingUrl: string;
}

@get
@route("/owner/profile")
@tag("Owner")
@operationId("getOwnerProfile")
@doc("Read the calendar owner's profile.")
op getOwnerProfile(): {
  @header("Cache-Control") cacheControl: "no-store";
  @body body: OwnerProfile;
};

model BookingEntry {
  bookingUrl: string;
}

model CreateMeetingTypeCommand {
  @doc("Immutable, case-sensitive ID: 1–64 ASCII letters, digits or hyphens.")
  id: string;

  @doc("Nonempty title after collapsing Unicode space separators and trimming. Nonprintable characters are rejected.")
  title: string;

  @doc("At least 20 counted Unicode code points after normalizing spaces per line. Internal newlines remain; newlines, control and invisible characters do not count toward the minimum.")
  description: string;

  durationMinutes: int32;
  requiresApproval?: boolean = false;
}

model WorkingInterval {
  start: string;
  end: string;
}

model WorkingDay {
  day:
    | "MONDAY"
    | "TUESDAY"
    | "WEDNESDAY"
    | "THURSDAY"
    | "FRIDAY"
    | "SATURDAY"
    | "SUNDAY";
  intervals: WorkingInterval[];
}

model DateOverride {
  date: string;
  intervals: WorkingInterval[];
}

model MeetingType {
  id: string;
  title: string;
  description: string;
  durationMinutes: int32;
  requiresApproval: boolean;
  timeZone: string;
  version: int64;
  archived: boolean;
  days: WorkingDay[];
  dateOverrides: DateOverride[];
}

model MeetingTypeList {
  items: MeetingType[];
}

@post
@route("/owner/meeting-types")
@tag("Owner")
@operationId("createMeetingType")
op createMeetingType(
  @header("Idempotency-Key") idempotencyKey: IdempotencyKey,
  @body command: CreateMeetingTypeCommand,
):
  | {
      @statusCode statusCode: 201;
      @header("Cache-Control") cacheControl: "no-store";
      @body body: MeetingType;
    }
  | StateConflict
  | InvalidData
  | MalformedRequest;

@get
@route("/owner/meeting-types")
@tag("Owner")
@operationId("listMeetingTypes")
op listMeetingTypes(): {
  @header("Cache-Control") cacheControl: "no-store";
  @body body: MeetingTypeList;
};

@get
@route("/owner/meeting-types/{id}")
@tag("Owner")
@operationId("getMeetingType")
op getMeetingType(@path id: string):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: MeetingType;
    }
  | PageNotFound;

model ApiError {
  code: string;
  message: string;
  currentProfile?: OwnerProfile;
  fieldErrors?: FieldError[];
}

model FieldError {
  field: string;
  code: string;
  message: string;
}

@error
model InvalidData {
  @statusCode statusCode: 422;
  @body body: ApiError;
}

@error
model MalformedRequest {
  @statusCode statusCode: 400;
  @body body: ApiError;
}

@error
model StateConflict {
  @statusCode statusCode: 409;
  @body body: ApiError;
}

@error
model OperationUnavailable {
  @statusCode statusCode: 410;
  @body body: ApiError;
}

@error
model PageNotFound {
  @statusCode statusCode: 404;
  @body body: ApiError;
}

@get
@route("/public/booking-entry/current")
@tag("Public")
@operationId("getBookingEntry")
@doc("Find the current general booking page, including when booking is disabled.")
op getBookingEntry(): {
  @header("Cache-Control") cacheControl: "no-store";
  @body body: BookingEntry;
};

model PublicBookingPage {
  ownerName: string;
  ownerTimeZone: string;
  state: "BOOKING_DISABLED" | "OPEN";

  @doc("Present only when OPEN; choosing even one item requires an explicit action.")
  items?: PublicMeetingType[];
}

model PublicMeetingType {
  id: string;
  title: string;
  description: string;
  durationMinutes: int32;
  requiresApproval: boolean;
  timeZone: string;

  @doc("Content-derived token over title, description, durationMinutes and requiresApproval only.")
  conditionsToken: string;
}

model PublicMeetingTypePage {
  ownerName: string;
  ownerTimeZone: string;
  state: "BOOKING_DISABLED" | "OPEN";

  @doc("Present only when OPEN.")
  meetingType?: PublicMeetingType;
}

model Slot {
  @doc("An actual start on the type-zone 15-minute grid; nonexistent local starts are skipped and repeated starts are returned separately with offsets.")
  startsAt: offsetDateTime;

  @doc("Start plus the type's elapsed duration, covered by continuous working time without crossing breaks or bridging repeated working sections.")
  endsAt: offsetDateTime;
}

@doc("The complete, guest-independent actual-time window, from tomorrow 00:00 in the current owner zone for 14 calendar days. Every slot fits entirely; ending exactly at windowEnd is allowed.")
model PublicSlots {
  windowStart: offsetDateTime;
  windowEnd: offsetDateTime;
  ownerTimeZone: string;
  meetingTypeTimeZone: string;
  conditionsToken: string;
  items: Slot[];
}

@get
@route("/public/{username}/meeting-types/{id}/slots")
@tag("Public")
@operationId("getPublicSlots")
@doc("Read all available slots. Unknown addresses return 404 before public-state checks; disabled booking or an archived type returns 409. Guest time zone is not an input.")
op getPublicSlots(@path username: string, @path id: string):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: PublicSlots;
    }
  | PageNotFound
  | StateConflict;

@get
@route("/public/{username}")
@tag("Public")
@operationId("getPublicBookingPage")
@doc("Read the general public booking page.")
op getPublicBookingPage(@path username: string):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: PublicBookingPage;
    }
  | PageNotFound;

@get
@route("/public/{username}/meeting-types/{id}")
@tag("Public")
@operationId("getPublicMeetingType")
@doc("Read explicitly selected conditions. Unknown username/ID returns 404 even when booking is disabled.")
op getPublicMeetingType(@path username: string, @path id: string):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: PublicMeetingTypePage;
    }
  | PageNotFound;

model PublicBookingCommand {
  enabled: boolean;
  @minValue(1) expectedVersion: int64;
}

@doc("Immutable future booking data. Preparation creates no booking and reserves no time.")
model PrepareBookingAttemptCommand {
  meetingTypeId: string;
  conditionsToken: string;
  startsAt: offsetDateTime;

  @doc("1–100 Unicode code points after trimming and collapsing spaces; controls and invisible characters are rejected.")
  guestName: string;

  @doc("One syntactically valid address, at most 254 Unicode code points after trimming edge spaces.")
  guestEmail: string;

  @doc("At most 2000 Unicode code points. Newlines are allowed, other controls and invisible characters are rejected.")
  comment?: string;
}

model PreparedBookingAttempt {
  @doc("Random identifier; alone it grants no access.")
  attemptId: string;

  @doc("Independent cryptographically random secret of at least 256 bits. Never put in URLs or logs; keep only in page memory.")
  attemptSecret: string;

  state: "PREPARED";
}

@doc("Saved booking conditions and contacts with current time, status and version.")
model BookingDetails {
  id: string;
  meetingTypeId: string;
  ownerName: string;
  ownerTimeZone: string;
  title: string;
  description: string;
  durationMinutes: int32;
  requiresApproval: boolean;
  startsAt: offsetDateTime;
  endsAt: offsetDateTime;
  status: "CONFIRMED" | "PENDING" | "REJECTED" | "CANCELLED";
  rejectionReason?: "APPROVAL_EXPIRED";
  guestName: string;
  guestEmail: string;
  comment: string;
  version: int64;
  allowedActions: string[];
}

model SubmittedBookingAttempt {
  state: "SUCCEEDED";
  booking: BookingDetails;
  resultUrl: string;
}

@doc("Owner-only booking snapshots. No result URLs, access tokens or attempt secrets.")
model OwnerBookingList {
  items: BookingDetails[];
}

@get
@route("/owner/calendar")
@tag("Owner")
@operationId("getOwnerCalendar")
@doc("Existing bookings overlapping the actual half-open range [from, to), ordered by start. Includes historical and terminal bookings; no separate event copies. From must precede to (422); missing/malformed moments give 400. Manual blocks are not yet implemented.")
op getOwnerCalendar(@query from: offsetDateTime, @query to: offsetDateTime):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: OwnerBookingList;
    }
  | InvalidData
  | MalformedRequest;

@get
@route("/owner/bookings/{id}")
@tag("Owner")
@operationId("getOwnerBooking")
@doc("Current owner-only details by booking ID, without result tokens. Reading refreshes approval expiry.")
op getOwnerBooking(@path id: string):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: BookingDetails;
    }
  | PageNotFound;

@get
@route("/owner/bookings")
@tag("Owner")
@operationId("listOwnerBookings")
@doc("All types in ascending actual start order. Upcoming is PENDING or CONFIRMED with start strictly after now; history is its complement. Status only narrows the selected view.")
op listOwnerBookings(@query view: string, @query status?: string):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: OwnerBookingList;
    }
  | InvalidData
  | MalformedRequest;

@doc("Read-only saved attempt outcome. PREPARED has no booking; SUCCEEDED includes booking and resultUrl; FAILED includes the saved definite error.")
model BookingAttemptResult {
  state: "PREPARED" | "SUCCEEDED" | "FAILED";
  booking?: BookingDetails;
  resultUrl?: string;
  error?: ApiError;
}

@get
@route("/booking-attempts/{attemptId}")
@tag("Public")
@operationId("getBookingAttempt")
@doc("Safely read an attempt with its original Bearer secret, without submitting it. Missing attempts and invalid secrets give the same neutral 410; saved outcomes precede public booking restrictions.")
op getBookingAttempt(
  @path attemptId: string,
  @header("Authorization") authorization?: string,
):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: BookingAttemptResult;
    }
  | OperationUnavailable;

@get
@route("/bookings/{accessToken}")
@tag("Public")
@operationId("getBookingResult")
@doc("Read the current booking using its independent secret link. Unknown links return a neutral BOOKING_NOT_FOUND. Public booking settings do not restrict existing results.")
op getBookingResult(@path accessToken: string):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: BookingDetails;
    }
  | PageNotFound;

@post
@route("/booking-attempts/{attemptId}/submit")
@tag("Public")
@operationId("submitBookingAttempt")
@doc("Submit immutable prepared data with its Bearer secret. First creation returns 201, successful replay 200 with the current booking. Definite refusals are saved. No request body is accepted.")
op submitBookingAttempt(
  @path attemptId: string,
  @header("Authorization") authorization?: string,
):
  | {
      @statusCode statusCode: 200 | 201;
      @header("Cache-Control") cacheControl: "no-store";
      @body body: SubmittedBookingAttempt;
    }
  | OperationUnavailable
  | PageNotFound
  | StateConflict
  | InvalidData
  | MalformedRequest;

@post
@route("/public/{username}/booking-attempts")
@tag("Public")
@operationId("prepareBookingAttempt")
@doc("Prepare immutable data without reserving time. Losing this response cannot leave a hidden booking. Availability must be rechecked on later submission.")
op prepareBookingAttempt(
  @path username: string,
  @body command: PrepareBookingAttemptCommand,
):
  | {
      @statusCode statusCode: 201;
      @header("Cache-Control") cacheControl: "no-store";
      @body body: PreparedBookingAttempt;
    }
  | InvalidData
  | MalformedRequest
  | PageNotFound
  | StateConflict;

model ProfileCommand {
  @doc("1–100 Unicode code points after trimming and collapsing Unicode space separators. Control, nonprintable and invisible control characters are rejected before normalization.")
  name: string;

  @doc("Trim edge space separators and lowercase; 3–30 ASCII letters, digits or hyphens, with alphanumeric boundaries.")
  username: string;

  @minValue(1) expectedVersion: int64;
}

@put
@route("/owner/profile")
@tag("Owner")
@operationId("updateOwnerProfile")
@doc("Edit name and username without changing booking state. The UI must explicitly confirm the published-link warning before a username change. Uses the shared owner operation key space.")
op updateOwnerProfile(
  @header("Idempotency-Key") idempotencyKey: IdempotencyKey,
  @body command: ProfileCommand,
):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: OwnerProfile;
    }
  | StateConflict
  | InvalidData
  | MalformedRequest;

@doc("Transport-safe operation key: 1 to 128 ASCII letters, digits, underscores or hyphens.")
@minLength(1)
@maxLength(128)
@pattern("^[A-Za-z0-9_-]+$")
scalar IdempotencyKey extends string;

@put
@route("/owner/public-booking")
@tag("Owner")
@operationId("setPublicBooking")
@doc("Explicitly enable or disable public booking, using the shown profile version. An invalid Idempotency-Key returns 400 MALFORMED_REQUEST without changing state or saving an outcome.")
op setPublicBooking(
  @header("Idempotency-Key")
  idempotencyKey: IdempotencyKey,

  @body command: PublicBookingCommand,
):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: OwnerProfile;
    }
  | StateConflict
  | InvalidData
  | MalformedRequest;

@doc("The saved, definite result of an owner command. Exactly one of profile, meetingType or error is present.")
model OwnerOperationResult {
  statusCode: int32;
  profile?: OwnerProfile;
  meetingType?: MeetingType;
  error?: ApiError;
}

@get
@route("/owner/operations/{key}")
@tag("Owner")
@operationId("getOwnerOperation")
@doc("Recover the saved result without executing the command again.")
op getOwnerOperation(@path key: IdempotencyKey):
  | {
      @header("Cache-Control") cacheControl: "no-store";
      @body body: OwnerOperationResult;
    }
  | OperationUnavailable;
