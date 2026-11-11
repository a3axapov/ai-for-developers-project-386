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
