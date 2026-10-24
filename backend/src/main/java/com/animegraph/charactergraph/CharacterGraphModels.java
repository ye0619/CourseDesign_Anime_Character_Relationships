package com.animegraph.charactergraph;

import java.util.List;

public final class CharacterGraphModels {
    private CharacterGraphModels() {}

    public enum DataSource { DEMO, NEO4J, ONLINE }
    public record Work(String uid, String title, String subtitle, int characterCount, int relationCount) {}
    public record CharacterNode(String id, String label, String group, String role, String description,
                                String originalName,String imageUrl,Integer characterType,String nameCnSource) {
        public CharacterNode(String id,String label,String group,String role,String description) {this(id,label,group,role,description,null,null,null,null);}
    }
    public record CharacterEdge(String id, String source, String target, RelationType relationType,
                                String label, String description, String evidence, String sourceName,
                                boolean directed,boolean spoiler,boolean ended,String sourceUrl,Integer sourceRelationId,String archiveRevision) {
        public CharacterEdge(String id,String source,String target,RelationType relationType,String label,String description,String evidence,String sourceName,boolean directed,boolean spoiler,boolean ended,String sourceUrl) {
            this(id,source,target,relationType,label,description,evidence,sourceName,directed,spoiler,ended,sourceUrl,null,null);
        }
        public CharacterEdge(String id,String source,String target,RelationType relationType,String label,String description,String evidence,String sourceName,boolean directed) {this(id,source,target,relationType,label,description,evidence,sourceName,directed,false,false,null);}
    }
    public record GraphData(Work work, List<CharacterNode> nodes, List<CharacterEdge> edges, boolean truncated) {}
    public record DemoWork(Work work, List<CharacterNode> nodes, List<CharacterEdge> edges) {}
    public record DemoDataset(List<DemoWork> works) {}
    public record Meta(DataSource dataSource, boolean demo, boolean truncated, String notice,
                       int maxNodes, int maxEdges,String relationStatus,String archiveRevision) {
        public Meta(DataSource dataSource,boolean demo,boolean truncated,String notice,int maxNodes,int maxEdges) {
            this(dataSource,demo,truncated,notice,maxNodes,maxEdges,null,null);
        }
    }
    public record Catalog(List<Work> items, Meta meta) {}
    public record GraphResponse(Work work, List<CharacterNode> nodes, List<CharacterEdge> edges,
                                List<RelationType.Definition> relationTypes, Meta meta) {}
}
