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

  @doc("Present only when OPEN. No meeting types exist in this slice.")
  items?: [];
}

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

model PublicBookingCommand {
  enabled: boolean;
  @minValue(1) expectedVersion: int64;
}

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

@doc("The saved, definite result of an owner command. Exactly one of profile or error is present.")
model OwnerOperationResult {
  statusCode: int32;
  profile?: OwnerProfile;
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
