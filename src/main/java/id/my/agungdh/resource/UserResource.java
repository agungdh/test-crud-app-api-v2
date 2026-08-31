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

import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class UserResource {

    @Inject
    UserService userService;

    @POST
    public Response create(@Valid UserCreateRequest req) {
        UserResponse created = userService.create(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    public PageResponse<UserResponse> list(
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("cursor") String cursor) {
        return userService.list(limit, cursor);
    }

    @GET
    @Path("/{uuid}")
    public UserResponse getByUuid(@PathParam("uuid") UUID uuid) {
        return userService.findByUuid(uuid);
    }

    @PUT
    @Path("/{uuid}")
    public UserResponse update(@PathParam("uuid") UUID uuid, @Valid UserUpdateRequest req) {
        if (req.username() == null && req.password() == null && req.name() == null) {
            throw new BadRequestException("At least one field must be provided");
        }
        return userService.update(uuid, req);
    }

    @DELETE
    @Path("/{uuid}")
    public Response delete(@PathParam("uuid") UUID uuid) {
        userService.delete(uuid);
        return Response.noContent().build();
    }
}
