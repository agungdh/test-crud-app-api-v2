package id.my.agungdh.resource;

import id.my.agungdh.dto.PageResponse;
import id.my.agungdh.dto.UserCreateRequest;
import id.my.agungdh.dto.UserResponse;
import id.my.agungdh.dto.UserUpdateRequest;
import id.my.agungdh.service.UserService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@Tag(name = "Users", description = "User CRUD operations")
public class UserResource {

    @Inject
    UserService userService;

    @POST
    @Operation(summary = "Create user", description = "Create a new user")
    @APIResponse(responseCode = "201", description = "User created",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error")
    public Response create(@Valid UserCreateRequest req) {
        UserResponse created = userService.create(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Operation(summary = "List users", description = "Cursor-based pagination, excludes soft-deleted")
    @APIResponse(responseCode = "200", description = "Paginated users")
    public PageResponse<UserResponse> list(
            @Parameter(description = "Page size", schema = @Schema(examples = "20")) @QueryParam("limit") @DefaultValue("20") int limit,
            @Parameter(description = "Cursor for next page") @QueryParam("cursor") String cursor) {
        return userService.list(limit, cursor);
    }

    @GET
    @Path("/{uuid}")
    @Operation(summary = "Get user by UUID")
    @APIResponse(responseCode = "200", description = "User found",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "404", description = "User not found")
    public UserResponse getByUuid(@Parameter(description = "User UUID") @PathParam("uuid") UUID uuid) {
        return userService.findByUuid(uuid);
    }

    // Endpoint khusus — include soft-deleted (tanpa param boolean)
    @GET
    @Path("/all")
    @Operation(summary = "List users including soft-deleted")
    @APIResponse(responseCode = "200", description = "Paginated users including deleted")
    public PageResponse<UserResponse> listIncludingDeleted(
            @Parameter(description = "Page size", schema = @Schema(examples = "20")) @QueryParam("limit") @DefaultValue("20") int limit,
            @Parameter(description = "Cursor for next page") @QueryParam("cursor") String cursor) {
        return userService.listIncludingDeleted(limit, cursor);
    }

    @PUT
    @Path("/{uuid}")
    @Operation(summary = "Update user", description = "At least one field must be provided")
    @APIResponse(responseCode = "200", description = "User updated",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error")
    @APIResponse(responseCode = "404", description = "User not found")
    public UserResponse update(@Parameter(description = "User UUID") @PathParam("uuid") UUID uuid, @Valid UserUpdateRequest req) {
        if (req.username() == null && req.password() == null && req.name() == null) {
            throw new BadRequestException("At least one field must be provided");
        }
        return userService.update(uuid, req);
    }

    @DELETE
    @Path("/{uuid}")
    @Operation(summary = "Delete user", description = "Soft delete user by UUID")
    @APIResponse(responseCode = "204", description = "User deleted")
    @APIResponse(responseCode = "404", description = "User not found")
    public Response delete(@Parameter(description = "User UUID") @PathParam("uuid") UUID uuid) {
        userService.delete(uuid);
        return Response.noContent().build();
    }
}
