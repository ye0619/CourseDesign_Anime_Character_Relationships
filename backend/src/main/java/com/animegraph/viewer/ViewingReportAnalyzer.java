package com.animegraph.viewer;

import java.time.Instant;
import java.util.*;
import static com.animegraph.viewer.ViewingReportService.*;

final class ViewingReportAnalyzer {
    private record Key(String uid, String role) {}

    static Report analyze(List<ViewerModels.Collection> completed, Map<Long,ReportCredits.Work> inputs) {
        var unique = new TreeMap<Long,ViewerModels.Collection>();
        completed.forEach(work -> unique.put(work.subjectId(),work));
        var all = unique.values().stream().filter(work->work.status()==2).toList();
        var coverage = all.stream().map(work -> {
            var credits=inputs.get(work.subjectId());
            return credits == null
                    ? new WorkCoverage(work.subjectId(),"PUBLIC_SNAPSHOT",
                        null,ReportCredits.missing(),ReportCredits.missing())
                    : new WorkCoverage(work.subjectId(),credits.source(),credits.version(),credits.staff(),credits.cast());
        }).toList();
        var full = scope(all,inputs,coverage);
        return new Report("viewing-report/5",Instant.now(),full.completed(),full.rated(),full.metadataConsidered(),full.metadataCovered(),
                full.personnel(),List.of(
                    "人员与正向评分偏好仅统计当前范围内已看动画；按全片单实际部数统计，接触频次不代表喜爱。",
                    "仅自然人；来源明确为组织或未知类型不自动归入人员。姓名相同不合并，身份使用稳定UID。",
                    "人员总体按人员+作品去重，职务视角按人员+职务+作品去重；多个配音角色不增加作品次数。",
                    "未评分不当0分；校正评分至少3部已评分作品，使用当前范围已评分均分的3个先验样本。",
                    "已保存作品复用Neo4j当前有效制作/明确本作配音；其采集完整性与来源新鲜度未核验，不借用导入时间判断。",
                    "作品优先使用已保存公共名册；完整性和6小时新鲜度分别显示，过期不抹掉已采集事实。",
                    "缺失资料通过Bangumi公共名册补充，每批最多100部；本地与在线作品使用相同统计口径。",
                    "覆盖状态为报告生成时的状态；展开作品可核对来源与输入版本。每种视角最多展示500项；发行年不是观看年。",
                    "配音角色仅使用本作明确配音关系，声优+作品+角色去重；作品数和不同角色数分别统计。",
                    "制作机构按Bangumi机构ID归并，只纳入明确动画制作职责；联合制作各计一部。均分使用已看且有个人评分作品。",
                    "观看状态与搁置/抛弃标签、形式、公司使用全同步片单，和已看正向偏好分开；未评分不按0分计算，不从抛弃推断厌恶题材。"),
                full.overall(),coverage,full.coverageSummary(),full.personnelTotal(),full.overallTotal(),
                ProfileDetails.voices(all,inputs),ProfileDetails.companies(completed,inputs),ProfileDetails.behaviors(completed,inputs));
    }

    private static ScopeReport scope(List<ViewerModels.Collection> completed,Map<Long,ReportCredits.Work> inputs,List<WorkCoverage> coverage) {
        var people = new HashMap<Key,ReportCredits.Participation>();
        var works = new HashMap<Key,Map<Long,ViewerModels.Collection>>();
        var roles = new HashMap<Key,Set<String>>();
        for (var work : completed) {
            var input=inputs.get(work.subjectId());if(input==null)continue;
            for(var participation:input.participation()) {
                for(String role:List.of(participation.role(),"人员总体")) {
                    var key=new Key(participation.uid(),role);people.put(key,participation);
                    works.computeIfAbsent(key,k->new TreeMap<>()).put(work.subjectId(),work);
                    roles.computeIfAbsent(key,k->new TreeSet<>()).add(participation.role());
                }
            }
        }
        double baseline=completed.stream().filter(c->c.personalRating()>0).mapToInt(ViewerModels.Collection::personalRating).average().orElse(0);
        var statistics = new ArrayList<PersonStat>();
        works.forEach((key,items)->{
            var rated=items.values().stream().filter(c->c.personalRating()>0).toList();
            double sum=rated.stream().mapToInt(ViewerModels.Collection::personalRating).sum();var person=people.get(key);
            statistics.add(new PersonStat(key.uid(),person.name(),key.role(),person.providerType(),items.size(),rated.size(),
                    rated.isEmpty()?null:round(sum/rated.size()),rated.size()<3?null:round((sum+3*baseline)/(rated.size()+3)),
                    List.copyOf(items.values()),List.copyOf(roles.get(key))));
        });
        statistics.sort(Comparator.comparingInt(PersonStat::workCount).reversed().thenComparing(PersonStat::name)
                .thenComparing(PersonStat::uid).thenComparing(PersonStat::role));
        var overall=statistics.stream().filter(person->person.role().equals("人员总体")).toList();
        var personnel=statistics.stream().filter(person->!person.role().equals("人员总体")).toList();
        int considered=(int)coverage.stream().filter(work->available(work.staff())||available(work.cast())).count();
        int covered=(int)coverage.stream().filter(work->work.staff().collectionStatus().equals("COMPLETE")&&work.cast().collectionStatus().equals("COMPLETE")).count();
        return new ScopeReport(completed.size(),(int)completed.stream().filter(work->work.personalRating()>0).count(),considered,covered,
                personnel.stream().limit(500).toList(),overall.stream().limit(500).toList(),summary(coverage),
                personnel.size(),overall.size());
    }
    private static boolean available(ReportCredits.Section section) {
        return section.records()>0||section.collectionStatus().equals("COMPLETE");
    }
    private static CoverageSummary summary(List<WorkCoverage> coverage) {
        return new CoverageSummary(counts(coverage.stream().map(WorkCoverage::staff).toList()),
                counts(coverage.stream().map(WorkCoverage::cast).toList()));
    }
    private static SectionCounts counts(List<ReportCredits.Section> sections) {
        var collection=new TreeMap<String,Integer>();var freshness=new TreeMap<String,Integer>();
        for(String key:List.of("COMPLETE","PARTIAL","NOT_FETCHED","NOT_VERIFIED"))collection.put(key,0);
        for(String key:List.of("FRESH","STALE","NOT_VERIFIED"))freshness.put(key,0);
        sections.forEach(section->{collection.merge(section.collectionStatus(),1,Integer::sum);freshness.merge(section.freshness(),1,Integer::sum);});
        return new SectionCounts(collection,freshness);
    }
    private static double round(double number) {return Math.round(number*100)/100.0;}
}
