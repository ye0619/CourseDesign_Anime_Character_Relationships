package com.animegraph.localgraph;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class LocalGraphModels {
    private LocalGraphModels() {}
    public record Entity(@NotBlank String id,@NotBlank String kind,@NotBlank @Size(max=200) String name,
                         @Size(max=720000) String imageUrl,@Size(max=20) List<@NotBlank @Size(max=100) String> aliases,
                         @Size(max=40) String gender,@Size(max=4000) String description,@Size(max=2000) String notes,
                         @Size(max=60) String organizationType,
                         List<com.animegraph.identity.NameResolutionService.LocalName> names,String preferredNameId,java.util.Map<String,com.fasterxml.jackson.databind.JsonNode> portraits) {
        public Entity(String id,String kind,String name,String imageUrl,List<String> aliases,String gender,String description,String notes,String organizationType) {
            this(id,kind,name,imageUrl,aliases,gender,description,notes,organizationType,null,null,null);
        }
    }
    public record Link(@NotBlank String id,@NotBlank String source,@NotBlank String target,@NotBlank String relationType,
                       @NotBlank @Size(max=60) String label,@Size(max=2000) String description,@Size(max=2000) String evidence,
                       boolean spoiler,boolean directed,@Size(max=2000) String notes) {}
    public record Document(@Min(0) long version,@Min(1) @Max(2) int schemaVersion,@NotBlank String workUid,
                           @NotNull @Size(max=300) List<@NotNull @Valid Entity> nodes,@NotNull @Size(max=800) List<@NotNull @Valid Link> edges) {}
}
