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
