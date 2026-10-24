package com.animegraph.viewer;

import java.time.Instant;
import java.util.List;

public final class ViewerModels {
    private ViewerModels() {}
    public record Profile(long id,String username,String nickname,String avatar) {}
    public record Collection(long subjectId,int status,int personalRating,String title,String originalName,
            String imageUrl,String releaseDate,int episodes,int watchedEpisodes,double communityScore,
            String updatedAt,List<String> personalTags,List<String> communityTags) {}
    public record Binding(String id,long bangumiId,String username,String nickname,String avatar,String bindingMode,
            String revision,String syncStatus,int syncProcessed,int remoteTotal,boolean truncated,String syncError,
            Instant lastSyncedAt) {}
    public record State(boolean bound,Profile profile,String mode,boolean verified,boolean requiresReauthorization,
            String syncStatus,int processed,int remoteTotal,boolean truncated,String error,Instant lastSyncedAt) {}
    public record RemotePage(int total,List<Collection> items) {}
    public record Facets(List<String> years,List<String> formats,List<String> tags) {}
    public record CollectionPage(List<Collection> items,int page,int pageSize,long total,Facets facets) {}
}
