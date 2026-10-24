package com.animegraph.charactergraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.core.io.ClassPathResource;

public enum RelationType {
    FORM, FRIEND, LOVER, CRUSH, RIVAL, FAMILY, MEMBER, FOUNDER, MASTER, PET,
    STUDENT, TEACHER, PILOT, SERVANT, SPOUSE, SUBORDINATE, SUPERIOR,
    ADAPTATION, PROTOTYPE, FORMER_PARTNER, COLLEAGUE, RELATIVE, CLASSMATE,
    FELLOW_DISCIPLE, EVOLUTION, PREDECESSOR, OWNER, OTHER,
    TEAMMATE, MENTOR;

    public record Definition(String code,String label,Integer bangumiId,String group,String groupLabel,
                             boolean directed,String inverseCode,String color,boolean legacy) {}
    private static final Map<String,Definition> DEFINITIONS=load();
    private static Map<String,Definition> load() {
        try(var stream=new ClassPathResource("fixtures/character-relation-types.json").getInputStream()) {
            var values=new ObjectMapper().readValue(stream,Definition[].class);
            var map=new LinkedHashMap<String,Definition>();
            for(var value:values) map.put(value.code(),value);
            if(map.size()!=values().length) throw new IllegalStateException("Incomplete character relation dictionary");
            return Collections.unmodifiableMap(map);
        } catch(Exception exception) {throw new IllegalStateException("Cannot load character relation dictionary",exception);}
    }
    public Definition definition() {return DEFINITIONS.get(name());}
    public String label() {return definition().label();}
    public boolean directed() {return definition().directed();}
    public static Optional<RelationType> parse(String code) {
        try{return Optional.of(valueOf(code));}catch(Exception ignored){return Optional.empty();}
    }
}
