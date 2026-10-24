import com.animegraph.viewer.RecommendationEngine;
import com.animegraph.viewer.ViewerModels;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Offline random holdout of the intentionally public sai example, using the production ranker. */
class RecommendationEvaluation {
    public static void main(String[] args) throws Exception {
        var json=new ObjectMapper();
        List<ViewerModels.Collection> library=json.readValue(Files.readString(Path.of(args[0])),new TypeReference<>(){});
        List<Map<String,Object>> candidates=json.readValue(Files.readString(Path.of(args[1])),new TypeReference<>(){});
        var candidateIds=new HashSet<Long>();for(var c:candidates)candidateIds.add(Long.parseLong(c.get("sourceId").toString()));
        var positive=library.stream().filter(c->c.status()==2&&c.personalRating()>=8&&candidateIds.contains(c.subjectId())).map(ViewerModels.Collection::subjectId).distinct().sorted().toList();
        if(positive.size()<7)throw new IllegalStateException("At least 7 positive candidate works required; no metric is fabricated.");
        int holdoutSize=Math.max(1,(int)Math.floor(positive.size()*.2));
        var cases=new ArrayList<Map<String,Object>>();var summaries=new LinkedHashMap<String,Object>();
        for(var algorithm:List.of("POPULAR","CONTENT","HYBRID")) {
            var coverage=new HashSet<Long>();double p=0,r=0,n=0,d=0;
            for(long seed:new long[]{1021,1022,1023,1024,1025}) {
                var shuffled=new ArrayList<>(positive);Collections.shuffle(shuffled,new Random(seed));var held=new HashSet<>(shuffled.subList(0,holdoutSize));
                var train=library.stream().filter(c->!held.contains(c.subjectId())).toList();
                var result=RecommendationEngine.rank(train,candidates,Set.of(),0,algorithm);
                int hits=0;double dcg=0,idcg=0,diversity=0;var items=result.items();
                for(int i=0;i<items.size();i++){long id=Long.parseLong(items.get(i).entity().get("sourceId").toString());coverage.add(id);if(held.contains(id)){hits++;dcg+=1/(Math.log(i+2)/Math.log(2));}}
                for(int i=0;i<Math.min(10,held.size());i++)idcg+=1/(Math.log(i+2)/Math.log(2));
                int pairs=0;for(int i=0;i<items.size();i++)for(int j=i+1;j<items.size();j++){diversity+=1-RecommendationEngine.jaccard(RecommendationEngine.tags(items.get(i).entity()),RecommendationEngine.tags(items.get(j).entity()));pairs++;}
                double precision=hits/10.0,recall=hits/(double)held.size(),ndcg=dcg/idcg,ild=pairs==0?0:diversity/pairs;p+=precision;r+=recall;n+=ndcg;d+=ild;
                cases.add(Map.of("algorithm",algorithm,"seed",seed,"holdoutSize",held.size(),"returned",items.size(),"hits",hits,"precisionAt10",precision,"recallAt10",recall,"ndcgAt10",ndcg,"tagDiversity",ild,"mode",result.mode()));
            }
            summaries.put(algorithm,Map.of("precisionAt10",p/5,"recallAt10",r/5,"ndcgAt10",n/5,"tagDiversity",d/5,"candidateCoverage",coverage.size()/(double)candidates.size(),"distinctRecommended",coverage.size()));
        }
        var output=new LinkedHashMap<String,Object>();output.put("evaluatedAt",java.time.Instant.now().toString());output.put("modelVersion","content-signed-tfidf/1");output.put("sample","Public Bangumi example sai, one user only");output.put("protocol","Frozen candidate/library snapshots; random 20% positive holdout, seeds 1021..1025; remove held works from all profile records before ranking; identical candidates for three models; no temporal claim");output.put("collectionCount",library.size());output.put("candidateCount",candidates.size());output.put("positiveCandidateWorks",positive.size());output.put("holdoutPerSeed",holdoutSize);output.put("candidateVersions",candidates.stream().map(c->c.getOrDefault("datasetVersion","unknown")).distinct().toList());output.put("librarySha256",sha(args[0]));output.put("candidateSha256",sha(args[1]));output.put("summary",summaries);output.put("cases",cases);output.put("limitations",List.of("One user and popularity-biased candidate set; seeds are correlated, not five independent users.","Current public ratings/tags are not historical features; no future prediction or cross-user generalization.","Precision@10 denominator stays 10 even if fewer recommendations are available; no tuning against holdouts."));
        Files.writeString(Path.of(args[2]),json.writerWithDefaultPrettyPrinter().writeValueAsString(output)+"\n");System.out.println(json.writeValueAsString(summaries));
    }
    static String sha(String path) throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(path))));}
}
