package com.animegraph.localgraph;
import static com.animegraph.localgraph.LocalGraphModels.*;
import com.animegraph.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LocalGraphRulesTest {
    static String nodeId(){return "local:entity:"+UUID.randomUUID();}
    static String linkId(){return "local:link:"+UUID.randomUUID();}
    static Entity node(String id,String kind){return new Entity(id,kind,"同名","",List.of(),"","","","");}
    static Link link(String a,String b,String type,String name,boolean directed){return new Link(linkId(),a,b,type,name,"","",false,directed,"");}
    static Document doc(List<Entity> nodes,List<Link> edges){return new Document(0,1,"anime:bangumi:1",nodes,edges);}
    @Test void supportsMixedEndpointsMultipleTypesAndDistinctSameNameEntities() {
        String a=nodeId(),b=nodeId();var edges=List.of(link(a,"character:bangumi:7","OTHER","父亲",true),link(a,"character:bangumi:7","FRIEND","朋友",false),link(a,b,"OTHER","队友",false));
        assertDoesNotThrow(()->LocalGraphRules.validate(1,doc(List.of(node(a,"CHARACTER"),node(b,"CHARACTER")),edges),Map.of("character:bangumi:7",1)));
    }
    @Test void rejectsSelfLinksForeignEndpointsOfficialEditsAndUndirectedReverseDuplicates() {
        String a=nodeId(),b=nodeId();var nodes=List.of(node(a,"CHARACTER"),node(b,"CHARACTER"));
        for(var edges:List.of(List.of(link(a,a,"FRIEND","朋友",false)),List.of(link(a,"character:bangumi:8","FRIEND","朋友",false)),
                List.of(link(a,b,"FRIEND","朋友",false),link(b,a,"FRIEND","朋友",false))))assertThrows(ApiException.class,()->LocalGraphRules.validate(1,doc(nodes,edges),Map.of()));
        assertThrows(ApiException.class,()->LocalGraphRules.validate(1,doc(List.of(node("character:bangumi:8","CHARACTER")),List.of()),Map.of()));
        assertThrows(ApiException.class,()->LocalGraphRules.validate(2,doc(nodes,List.of()),Map.of()));
    }
    @Test void membershipRequiresCharacterToLocalOrganizationButAllowsMultipleMemberships() {
        String a=nodeId(),b=nodeId(),c=nodeId();var nodes=List.of(node(a,"CHARACTER"),node(b,"ORGANIZATION"),node(c,"ORGANIZATION"));
        assertDoesNotThrow(()->LocalGraphRules.validate(1,doc(nodes,List.of(link(a,b,"MEMBER","队长",true),link(a,c,"MEMBER","成员",true))),Map.of()));
        assertThrows(ApiException.class,()->LocalGraphRules.validate(1,doc(nodes,List.of(link(b,a,"MEMBER","成员",true))),Map.of()));
        assertThrows(ApiException.class,()->LocalGraphRules.validate(1,doc(nodes,List.of(link(a,b,"OTHER","朋友",false))),Map.of()));
    }
    @Test void directionsRemainDistinctAndImagesRejectWrongTypesOrOversizedContent() {
        String a=nodeId(),b=nodeId();assertDoesNotThrow(()->LocalGraphRules.validate(1,doc(List.of(node(a,"CHARACTER"),node(b,"CHARACTER")),List.of(link(a,b,"OTHER","老师",true),link(b,a,"OTHER","老师",true))),Map.of()));
        assertThrows(ApiException.class,()->LocalGraphRules.validate(1,doc(List.of(node(a,"CHARACTER"),node(b,"CHARACTER")),List.of(link(a,b,"OTHER","父亲",false))),Map.of()));
        assertThrows(ApiException.class,()->LocalGraphRules.image("data:image/svg+xml;base64,PHN2Zz4="));
        assertThrows(ApiException.class,()->LocalGraphRules.image("data:image/png;base64,"+Base64.getEncoder().encodeToString(new byte[600000])));
        assertThrows(ApiException.class,()->LocalGraphRules.image("https://example.com/a.png"));
        assertDoesNotThrow(()->LocalGraphRules.image("https://lain.bgm.tv/pic/crt/a.jpg"));
    }
}
