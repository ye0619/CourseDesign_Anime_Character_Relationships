package com.animegraph.viewer;

import java.util.*;
import java.util.function.Function;
import static com.animegraph.viewer.ViewerModels.Collection;

/** Pure report projections; identities and subject scopes are never inferred from names. */
public final class ProfileDetails {
    private ProfileDetails() {}
    public record Scores(int ratedCount,Double averageRating,Map<Integer,Long> distribution) {}
    public record Role(long subjectId,String title,int personalRating,String characterUid,String characterName,String imageUrl) {}
    public record Voice(String uid,String name,String imageUrl,int workCount,int characterCount,Scores scores,List<Role> roles) {}
    public record CompanyCredit(long subjectId,String role,String sourceUrl,String revision) {}
    public record Company(String uid,String name,String imageUrl,int completed,int allCount,Scores scores,
                          Map<Integer,Long> statuses,List<Collection> representatives,List<Collection> works,List<CompanyCredit> credits) {}
    public record Group(String id,String label,int count,List<Collection> works) {}
    public record Behavior(int status,String label,int count,double share,Scores scores,int tagged,int companyCovered,
                           List<Group> tags,List<Group> formats,List<Group> companies,List<Collection> works) {}
    static List<Collection> unique(List<Collection> items) {
        var result=new TreeMap<Long,Collection>();items.forEach(work->result.put(work.subjectId(),work));return List.copyOf(result.values());
    }
    static Scores scores(List<Collection> works) {
        var ratings=works.stream().filter(work->work.personalRating()>0 && work.personalRating()<=10).toList();
        var distribution=new TreeMap<Integer,Long>();for(int i=1;i<=10;i++)distribution.put(i,0L);
        ratings.forEach(work->distribution.merge(work.personalRating(),1L,Long::sum));
        return new Scores(ratings.size(),ratings.isEmpty()?null:Math.round(ratings.stream().mapToInt(Collection::personalRating).average().orElseThrow()*100)/100.0,distribution);
    }
    static List<Voice> voices(List<Collection> works,Map<Long,ReportCredits.Work> inputs) {
        var roles=new TreeMap<String,Map<String,Role>>();var people=new HashMap<String,ReportCredits.Performance>();
        var byId=new HashMap<Long,Collection>();works.forEach(work->byId.put(work.subjectId(),work));
        for(var work:unique(works)) {
            var input=inputs.get(work.subjectId());if(input==null)continue;
            for(var p:input.performances()) {
                people.put(p.personUid(),p);
                roles.computeIfAbsent(p.personUid(),key->new TreeMap<>()).put(work.subjectId()+"|"+p.characterUid(),
                        new Role(work.subjectId(),work.title(),work.personalRating(),p.characterUid(),p.characterName(),p.characterImage()));
            }
        }
        return roles.entrySet().stream().map(entry->{
            var person=people.get(entry.getKey());var values=List.copyOf(entry.getValue().values());
            var subjects=values.stream().map(Role::subjectId).distinct().map(byId::get).toList();
            return new Voice(entry.getKey(),person.personName(),person.personImage(),subjects.size(),
                    (int)values.stream().map(Role::characterUid).distinct().count(),scores(subjects),values);
        }).sorted(Comparator.comparingInt(Voice::workCount).reversed().thenComparing(Voice::uid)).toList();
    }
    static List<Company> companies(List<Collection> works,Map<Long,ReportCredits.Work> inputs) {
        var byStudio=new TreeMap<String,Map<Long,Collection>>();var studios=new HashMap<String,ReportCredits.Studio>();
        for(var work:unique(works)) {
            var input=inputs.get(work.subjectId());if(input==null)continue;
            for(var studio:input.studios()) {
                studios.merge(studio.uid(),studio,(old,next)->{int a=old.namePriority()==null?2:old.namePriority(),b=next.namePriority()==null?2:next.namePriority();if(a!=b)return a<b?old:next;return old.retrievedAt()==null?next:next.retrievedAt()!=null&&next.retrievedAt().isAfter(old.retrievedAt())?next:old;});byStudio.computeIfAbsent(studio.uid(),key->new TreeMap<>()).put(work.subjectId(),work);
            }
        }
        return byStudio.entrySet().stream().map(entry->{
            var studio=studios.get(entry.getKey());var all=List.copyOf(entry.getValue().values());
            var completed=all.stream().filter(work->work.status()==2).toList();
            var statuses=new TreeMap<Integer,Long>();for(int i=1;i<=5;i++)statuses.put(i,0L);
            all.forEach(work->statuses.merge(work.status(),1L,Long::sum));
            return new Company(studio.uid(),studio.name(),studio.imageUrl(),completed.size(),all.size(),scores(completed),statuses,
                    completed.stream().filter(work->work.personalRating()>=8).sorted(Comparator.comparingInt(Collection::personalRating).reversed().thenComparingLong(Collection::subjectId)).limit(6).toList(),all,all.stream().flatMap(work->inputs.get(work.subjectId()).studios().stream().filter(s->s.uid().equals(studio.uid())).map(s->new CompanyCredit(work.subjectId(),s.role(),s.sourceUrl(),s.revision()))).distinct().toList());
        }).sorted(Comparator.comparingInt(Company::completed).reversed().thenComparing(Company::uid)).toList();
    }
    static List<Behavior> behaviors(List<Collection> works,Map<Long,ReportCredits.Work> inputs) {
        var all=unique(works);var result=new ArrayList<Behavior>();
        var labels=Map.of(1,"想看",2,"看过",3,"在看",4,"搁置",5,"抛弃");
        for(int status=1;status<=5;status++) {
            final int selected=status;var items=all.stream().filter(work->work.status()==selected).toList();
            var companyGroups=companies(items,inputs).stream().map(company->new Group(company.uid(),company.name(),company.allCount(),company.works())).toList();
            result.add(new Behavior(status,labels.get(status),items.size(),all.isEmpty()?0:Math.round(items.size()*10000.0/all.size())/100.0,
                    scores(items),(int)items.stream().filter(work->!ViewingMetadata.tags(work,"COMMUNITY").isEmpty()).count(),
                    (int)items.stream().filter(work->inputs.containsKey(work.subjectId())&&!inputs.get(work.subjectId()).studios().isEmpty()).count(),
                    groups(items,work->ViewingMetadata.tags(work,"COMMUNITY")),groups(items,work->List.of(ViewingMetadata.format(work))),companyGroups,items));
        }
        return result;
    }
    private static List<Group> groups(List<Collection> works,Function<Collection,List<String>> labels) {
        var groups=new TreeMap<String,List<Collection>>();
        for(var work:works)for(var label:new LinkedHashSet<>(labels.apply(work)))groups.computeIfAbsent(label,key->new ArrayList<>()).add(work);
        return groups.entrySet().stream().map(entry->new Group(entry.getKey(),entry.getKey(),entry.getValue().size(),List.copyOf(entry.getValue())))
                .sorted(Comparator.comparingInt(Group::count).reversed().thenComparing(Group::label)).toList();
    }
}
