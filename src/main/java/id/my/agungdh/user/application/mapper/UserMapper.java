package id.my.agungdh.user.application.mapper;

import id.my.agungdh.user.application.dto.UserCreateRequest;
import id.my.agungdh.user.application.dto.UserResponse;
import id.my.agungdh.user.application.dto.UserUpdateRequest;
import id.my.agungdh.user.domain.model.User;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper untuk User — sama seperti di Spring Boot:
 * componentModel = "cdi" biar jadi bean @ApplicationScoped yang bisa @Inject di Quarkus.
 */
@Mapper(
        componentModel = "cdi",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {

    // Entity -> DTO
    UserResponse toResponse(User user);

    List<UserResponse> toResponses(List<User> users);

    // DTO -> Entity (create) — ignore audit fields + password (di-hash manual di service)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    User fromCreateRequest(UserCreateRequest req);

    // Update existing entity — hanya field non-null dari request yang di-apply
    // password di-ignore (hash manual), audit fields di-ignore
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    void updateFromRequest(UserUpdateRequest req, @MappingTarget User user);
}
