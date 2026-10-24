package com.animegraph.viewer;

import com.animegraph.explore.*;
import java.time.Instant;
import java.util.*;

/** A report input preserves collection completeness independently of age. */
final class ReportCredits {
    record Section(String collectionStatus, String freshness, Instant fetchedAt, int records) {}
    record Participation(String uid, String name, Integer providerType, String role) {}
    record Performance(String personUid, String personName, String personImage, String characterUid,
                       String characterName, String characterImage) {}
    record Studio(String uid, String name, String imageUrl, String role, String sourceUrl, String revision, Integer namePriority, Instant retrievedAt) {
        Studio(String uid,String name,String imageUrl){this(uid,name,imageUrl,"动画制作","","",2,null);}
        Studio(String uid,String name,String imageUrl,String role,String sourceUrl,String revision){this(uid,name,imageUrl,role,sourceUrl,revision,2,null);}
    }
    record Work(long subjectId, String source, String version, Section staff, Section cast,
                List<Participation> participation, List<Performance> performances, List<Studio> studios) {
        Work(long subjectId,String source,String version,Section staff,Section cast,List<Participation> participation) {
            this(subjectId,source,version,staff,cast,participation,List.of(),List.of());
        }
    }
    // Exact source duties only; committees, publishers and planning are excluded.
    static boolean animationProduction(String role) { return role!=null && Set.of("动画制作","アニメーション制作").contains(role); }

    static Section missing() { return new Section("NOT_FETCHED", "NOT_VERIFIED", null, 0); }

    static Work merge(Work snapshot, Work local) {
        var people=new LinkedHashMap<String,Participation>();java.util.stream.Stream.concat(snapshot.participation().stream(),local.participation().stream()).forEach(p->people.putIfAbsent(p.uid()+"|"+p.role(),p));
        var performances=new LinkedHashMap<String,Performance>();java.util.stream.Stream.concat(snapshot.performances().stream(),local.performances().stream()).forEach(p->performances.putIfAbsent(p.personUid()+"|"+p.characterUid(),p));
        return new Work(snapshot.subjectId(),"SAVED_CREDITS",snapshot.version()+"|"+local.version(),
                snapshot.staff().collectionStatus().equals("NOT_FETCHED")?local.staff():snapshot.staff(),
                snapshot.cast().collectionStatus().equals("NOT_FETCHED")?local.cast():snapshot.cast(),List.copyOf(people.values()),
                List.copyOf(performances.values()),
                java.util.stream.Stream.concat(snapshot.studios().stream(),local.studios().stream()).distinct().toList());
    }

    static Work fromSnapshot(SubjectSnapshotService.Snapshot snapshot) {
        var people = new ArrayList<Participation>();
        var performances = new LinkedHashSet<Performance>();
        var studios = new LinkedHashSet<Studio>();
        for (var section : List.of(snapshot.staff(), snapshot.cast())) for (var page : section.pages()) {
            if(!page.subjectUid().equals("anime:bangumi:"+snapshot.subjectId())||!page.kind().equals(section.kind()))continue;
            var entities = new HashMap<String,CreditModels.Entity>();
            page.people().forEach(person -> entities.put(person.id(), person));
            page.staff().forEach(credit -> credit.positions().forEach(position ->
                    add(people, entities.get(credit.personUid()), position.label())));
            page.staff().forEach(credit -> {
                var entity=entities.get(credit.personUid());
                if(section.kind().equals("STAFF") && entity!=null && Objects.equals(entity.providerType(),2)
                        && credit.positions().stream().anyMatch(position->animationProduction(position.label())))
                    studios.add(new Studio(entity.id(),entity.label(),entity.imageUrl(),credit.positions().stream().map(CreditModels.Position::label).filter(ReportCredits::animationProduction).findFirst().orElseThrow(),page.source().sourceUrl(),page.snapshotVersion(),entity.nameAssertions()!=null&&entity.nameAssertions().stream().anyMatch(n->n.locale().startsWith("zh")&&n.name().equals(entity.label()))?0:1,page.source().fetchedAt()));
            });
            var characters=new HashMap<String,CreditModels.Entity>();
            page.characters().forEach(character->characters.put(character.id(),character));
            page.cast().stream().filter(credit -> "VOICE".equals(credit.performanceType()) && page.subjectUid().equals(credit.subjectUid()))
                    .forEach(credit -> {
                        var person=entities.get(credit.personUid()); var character=characters.get(credit.characterUid());
                        add(people,person,"配音");
                        if(person!=null && Objects.equals(person.providerType(),1) && character!=null)
                            performances.add(new Performance(person.id(),person.label(),person.imageUrl(),
                                    character.id(),character.label(),character.imageUrl()));
                    });
        }
        String version = java.util.stream.Stream.concat(snapshot.staff().pages().stream(), snapshot.cast().pages().stream())
                .map(CreditModels.Page::snapshotVersion).sorted().collect(java.util.stream.Collectors.joining("|"));
        return new Work(snapshot.subjectId(), "PUBLIC_SNAPSHOT", version,
                section(snapshot.subjectId(),snapshot.staff()), section(snapshot.subjectId(),snapshot.cast()), List.copyOf(people), List.copyOf(performances), List.copyOf(studios));
    }

    private static void add(List<Participation> people, CreditModels.Entity person, String role) {
        if (person != null && Objects.equals(person.providerType(), 1) && role != null && !role.isBlank())
            people.add(new Participation(person.id(), person.label(), person.providerType(), role));
    }

    private static Section section(long id,SubjectSnapshotService.Section section) {
        if (section.pages().isEmpty()) return missing();
        Instant oldest = section.pages().stream().map(page -> page.source().fetchedAt()).min(Instant::compareTo).orElseThrow();
        boolean stale = oldest.isBefore(Instant.now().minusSeconds(21600))
                || section.pages().stream().anyMatch(page -> page.source().stale());
        boolean subjectMatches=section.pages().stream().allMatch(page->page.subjectUid().equals("anime:bangumi:"+id));
        return new Section(subjectMatches&&SubjectSnapshotService.collected(section.kind(), section.pages()) ? "COMPLETE" : "PARTIAL",
                stale ? "STALE" : "FRESH", oldest, section.loadedRows());
    }
}
