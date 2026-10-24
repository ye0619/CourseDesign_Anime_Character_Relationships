import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.driver.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

/** Private logical backup/isolated recovery for the current community graph. Never opens the source store directory. */
public final class GraphRecovery {
    static final ObjectMapper JSON=new ObjectMapper();
    static final int MAX_NODES=50000,MAX_RELATIONSHIPS=150000;
    record Property(String type,Object value) {}
    record SavedNode(String key,List<String> labels,Map<String,Property> properties) {}
    record SavedRelationship(String key,String source,String target,String type,Map<String,Property> properties) {}
    record SchemaItem(String name,String type,String entityType,List<String> labelsOrTypes,List<String> properties) {}
    record Snapshot(int schemaVersion,String capturedAt,List<SavedNode> nodes,List<SavedRelationship> relationships,
                    List<SchemaItem> constraints,List<SchemaItem> indexes) {}
    record Mapping(Map<String,String> nodes,Map<String,String> relationships) {}

    public static void main(String[] args)throws Exception{
        if(args.length==2&&args[0].equals("export")){
            var target=Path.of(args[1]).toAbsolutePath().normalize();
            if(Files.exists(target))throw new IllegalArgumentException("Refusing to overwrite a graph backup");
            var snapshot=exportSnapshot();
            Files.write(target,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(snapshot),StandardOpenOption.CREATE_NEW);
            System.out.println(JSON.writeValueAsString(Map.of("graphBackup",true,"nodes",snapshot.nodes.size(),"relationships",snapshot.relationships.size(),
                    "constraints",snapshot.constraints.size(),"indexes",snapshot.indexes.size())));
        }else if(args.length==2&&args[0].equals("compare-source")){
            var before=JSON.readValue(Files.readAllBytes(Path.of(args[1])),Snapshot.class);
            check(logical(before).equals(logical(exportSnapshot())),"Source graph changed during recovery exercise");
            System.out.println("{\"sourceUnchanged\":true}");
        }else if(args.length==4&&args[0].equals("restore")){
            var allowed=Path.of(args[3]).toRealPath();
            var home=Path.of(args[2]).toAbsolutePath().normalize();
            if(!home.getParent().toRealPath().startsWith(allowed)||!home.getFileName().toString().equals("neo4j-restored")
                    ||Files.exists(home))throw new IllegalArgumentException("Recovery requires a new dedicated directory below the verified test root");
            var snapshot=JSON.readValue(Files.readAllBytes(Path.of(args[1])),Snapshot.class);
            validate(snapshot);Files.createDirectory(home);
            var mapping=restore(snapshot,home);
            // Reopen the persisted copy and compare every property, endpoint, label and schema definition again.
            var dbms=open(home);
            try{verify(snapshot,mapping,dbms.database("neo4j"));}
            finally{dbms.shutdown();}
            var record=Map.of("passed",true,"reopened",true,"nodes",snapshot.nodes.size(),"relationships",snapshot.relationships.size(),
                    "constraints",snapshot.constraints.size(),"indexes",snapshot.indexes.size(),
                    "scope","All logical records and supported schema compared in an isolated embedded community database; source store never opened");
            Files.write(home.getParent().resolve("graph-restore-result.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(record),StandardOpenOption.CREATE_NEW);
            System.out.println(JSON.writeValueAsString(record));
        }else throw new IllegalArgumentException("Use export <new-file>, compare-source <snapshot>, or restore <snapshot> <new-home> <verified-test-root>");
    }
    static Snapshot exportSnapshot(){
        var uri=java.net.URI.create(Objects.requireNonNull(System.getenv("NEO4J_URI")));
        if(!"bolt".equals(uri.getScheme())||!Set.of("127.0.0.1","localhost").contains(uri.getHost())||uri.getRawUserInfo()!=null)
            throw new IllegalArgumentException("Only the configured loopback graph is accepted");
        try(var driver=GraphDatabase.driver(uri,AuthTokens.basic(System.getenv("NEO4J_USERNAME"),System.getenv("NEO4J_PASSWORD")));
            var session=driver.session(SessionConfig.forDatabase(System.getenv("NEO4J_DATABASE")))){
            return session.executeRead(tx->{
                var nodes=tx.run("MATCH (n) RETURN n LIMIT "+(MAX_NODES+1)).list(row->{
                    var node=row.get("n").asNode();var properties=new TreeMap<String,Property>();
                    node.keys().forEach(key->properties.put(key,encode(node.get(key).asObject())));
                    var labels=new ArrayList<String>();node.labels().forEach(labels::add);Collections.sort(labels);
                    return new SavedNode(node.elementId(),labels,properties);
                });
                var relationships=tx.run("MATCH (s)-[r]->(t) RETURN elementId(s) AS source,elementId(t) AS target,r LIMIT "+(MAX_RELATIONSHIPS+1)).list(row->{
                    var relation=row.get("r").asRelationship();var properties=new TreeMap<String,Property>();
                    relation.keys().forEach(key->properties.put(key,encode(relation.get(key).asObject())));
                    return new SavedRelationship(relation.elementId(),row.get("source").asString(),row.get("target").asString(),relation.type(),properties);
                });
                var constraints=tx.run("SHOW CONSTRAINTS YIELD name,type,entityType,labelsOrTypes,properties RETURN name,type,entityType,labelsOrTypes,properties")
                        .list(GraphRecovery::schema);
                var indexes=tx.run("SHOW INDEXES YIELD name,type,entityType,labelsOrTypes,properties,owningConstraint WHERE owningConstraint IS NULL AND type <> 'LOOKUP' RETURN name,type,entityType,labelsOrTypes,properties")
                        .list(GraphRecovery::schema);
                var snapshot=new Snapshot(1,Instant.now().toString(),nodes,relationships,constraints,indexes);validate(snapshot);return snapshot;
            },TransactionConfig.builder().withTimeout(Duration.ofSeconds(90)).build());
        }
    }
    static SchemaItem schema(org.neo4j.driver.Record row){
        return new SchemaItem(row.get("name").asString(),row.get("type").asString(),row.get("entityType").asString(),
                row.get("labelsOrTypes").asList(Value::asString),row.get("properties").asList(Value::asString));
    }
    static void validate(Snapshot snapshot){
        if(snapshot.schemaVersion!=1||snapshot.nodes.size()>MAX_NODES||snapshot.relationships.size()>MAX_RELATIONSHIPS)throw new IllegalArgumentException("Invalid graph backup limits");
        var nodes=new HashSet<String>();var relations=new HashSet<String>();
        for(var node:snapshot.nodes){
            if(!nodes.add(node.key)||node.labels.size()>30||node.labels.isEmpty())throw new IllegalArgumentException("Invalid graph node identity");
            node.labels.forEach(GraphRecovery::identifier);node.properties.keySet().forEach(GraphRecovery::identifier);
        }
        for(var relation:snapshot.relationships){
            if(!relations.add(relation.key)||!nodes.contains(relation.source)||!nodes.contains(relation.target))throw new IllegalArgumentException("Invalid relationship endpoint");
            identifier(relation.type);relation.properties.keySet().forEach(GraphRecovery::identifier);
        }
        for(var constraint:snapshot.constraints)
            if(!constraint.type.equals("UNIQUENESS")||!constraint.entityType.equals("NODE")||constraint.labelsOrTypes.size()!=1||constraint.properties.size()!=1)
                throw new IllegalArgumentException("Unsupported constraint in this community recovery tool");
        for(var index:snapshot.indexes)
            if(!index.type.equals("RANGE")||!Set.of("NODE","RELATIONSHIP").contains(index.entityType)||index.labelsOrTypes.size()!=1||index.properties.isEmpty())
                throw new IllegalArgumentException("Unsupported index in this community recovery tool");
    }
    static void identifier(String text){if(text==null||!text.matches("[A-Za-z_][A-Za-z0-9_]{0,199}"))throw new IllegalArgumentException("Unsupported graph identifier");}
    static Property encode(Object value){
        if(value instanceof String text)return new Property("STRING",text);
        if(value instanceof Boolean bool)return new Property("BOOLEAN",bool);
        if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long)return new Property("INTEGER",((Number)value).longValue());
        if(value instanceof Float||value instanceof Double)return new Property("FLOAT",((Number)value).doubleValue());
        if(value instanceof byte[] bytes)return new Property("BYTES",Base64.getEncoder().encodeToString(bytes));
        if(value instanceof LocalDate)return new Property("DATE",value.toString());
        if(value instanceof LocalTime)return new Property("LOCAL_TIME",value.toString());
        if(value instanceof OffsetTime)return new Property("TIME",value.toString());
        if(value instanceof LocalDateTime)return new Property("LOCAL_DATE_TIME",value.toString());
        if(value instanceof ZonedDateTime)return new Property("DATE_TIME",value.toString());
        if(value instanceof List<?> list)return new Property("LIST",list.stream().map(GraphRecovery::encode).toList());
        if(value!=null&&value.getClass().isArray()){
            var list=new ArrayList<Property>();for(int i=0;i<Array.getLength(value);i++)list.add(encode(Array.get(value,i)));return new Property("LIST",list);
        }
        throw new IllegalArgumentException("Unsupported graph property type; backup not created");
    }
    static Object decode(Property property){
        return switch(property.type){
            case "STRING","BOOLEAN"->property.value;
            case "INTEGER"->((Number)property.value).longValue();
            case "FLOAT"->((Number)property.value).doubleValue();
            case "BYTES"->Base64.getDecoder().decode((String)property.value);
            case "DATE"->LocalDate.parse((String)property.value);
            case "LOCAL_TIME"->LocalTime.parse((String)property.value);
            case "TIME"->OffsetTime.parse((String)property.value);
            case "LOCAL_DATE_TIME"->LocalDateTime.parse((String)property.value);
            case "DATE_TIME"->ZonedDateTime.parse((String)property.value);
            case "LIST"->array(property.value);
            default->throw new IllegalArgumentException("Unsupported saved property type");
        };
    }
    static Object array(Object value){
        var values=(List<?>)value;var items=values.stream().map(item->item instanceof Property p?p:JSON.convertValue(item,Property.class)).toList();
        if(items.isEmpty())return new String[0];
        var type=items.getFirst().type;if(items.stream().anyMatch(item->!item.type.equals(type)))throw new IllegalArgumentException("Mixed graph property array");
        return switch(type){
            case "STRING"->items.stream().map(item->(String)decode(item)).toArray(String[]::new);
            case "INTEGER"->items.stream().mapToLong(item->(Long)decode(item)).toArray();
            case "FLOAT"->items.stream().mapToDouble(item->(Double)decode(item)).toArray();
            case "BOOLEAN"->{boolean[] result=new boolean[items.size()];for(int i=0;i<result.length;i++)result[i]=(Boolean)decode(items.get(i));yield result;}
            default->throw new IllegalArgumentException("Unsupported graph property array type");
        };
    }
    static DatabaseManagementService open(Path home){
        return new DatabaseManagementServiceBuilder(home)
                .setConfig(GraphDatabaseSettings.pagecache_memory,128L*1024*1024).build();
    }
    static Mapping restore(Snapshot snapshot,Path home){
        var dbms=open(home);var nodeMap=new HashMap<String,String>();var relationshipMap=new HashMap<String,String>();
        try{
            var db=dbms.database("neo4j");
            for(int start=0;start<snapshot.nodes.size();start+=500)try(var tx=db.beginTx()){
                for(var item:snapshot.nodes.subList(start,Math.min(start+500,snapshot.nodes.size()))){
                    var labels=item.labels.stream().map(Label::label).toArray(Label[]::new);var node=tx.createNode(labels);
                    item.properties.forEach((key,value)->node.setProperty(key,decode(value)));nodeMap.put(item.key,node.getElementId());
                }tx.commit();
            }
            for(int start=0;start<snapshot.relationships.size();start+=500)try(var tx=db.beginTx()){
                for(var item:snapshot.relationships.subList(start,Math.min(start+500,snapshot.relationships.size()))){
                    var from=tx.getNodeByElementId(nodeMap.get(item.source));var to=tx.getNodeByElementId(nodeMap.get(item.target));
                    var relation=from.createRelationshipTo(to,RelationshipType.withName(item.type));
                    item.properties.forEach((key,value)->relation.setProperty(key,decode(value)));relationshipMap.put(item.key,relation.getElementId());
                }tx.commit();
            }
            for(var item:snapshot.constraints)try(var tx=db.beginTx()){
                tx.schema().constraintFor(Label.label(item.labelsOrTypes.getFirst())).withName(item.name).assertPropertyIsUnique(item.properties.getFirst()).create();tx.commit();
            }
            for(var item:snapshot.indexes)try(var tx=db.beginTx()){
                var creator=item.entityType.equals("NODE")?tx.schema().indexFor(Label.label(item.labelsOrTypes.getFirst())):tx.schema().indexFor(RelationshipType.withName(item.labelsOrTypes.getFirst()));
                for(var property:item.properties)creator=creator.on(property);creator.withName(item.name).create();tx.commit();
            }
            try(var tx=db.beginTx()){tx.schema().awaitIndexesOnline(60,TimeUnit.SECONDS);}
            var mapping=new Mapping(nodeMap,relationshipMap);verify(snapshot,mapping,db);return mapping;
        }finally{dbms.shutdown();}
    }
    static void verify(Snapshot snapshot,Mapping mapping,GraphDatabaseService db){
        try(var tx=db.beginTx()){
            long nodes=((Number)tx.execute("MATCH (n) RETURN count(n) AS count").next().get("count")).longValue();
            long relations=((Number)tx.execute("MATCH ()-[r]->() RETURN count(r) AS count").next().get("count")).longValue();
            check(nodes==snapshot.nodes.size()&&relations==snapshot.relationships.size(),"Restored graph counts differ");
            for(var expected:snapshot.nodes){
                var actual=tx.getNodeByElementId(mapping.nodes.get(expected.key));
                var labels=new ArrayList<String>();actual.getLabels().forEach(label->labels.add(label.name()));Collections.sort(labels);
                check(labels.equals(expected.labels)&&equalProperties(expected.properties,actual.getAllProperties()),"Restored node differs");
            }
            for(var expected:snapshot.relationships){
                var actual=tx.getRelationshipByElementId(mapping.relationships.get(expected.key));
                check(actual.getType().name().equals(expected.type)
                        &&actual.getStartNode().getElementId().equals(mapping.nodes.get(expected.source))
                        &&actual.getEndNode().getElementId().equals(mapping.nodes.get(expected.target))
                        &&equalProperties(expected.properties,actual.getAllProperties()),"Restored relationship differs");
            }
            var constraints=new ArrayList<SchemaItem>();
            try(var result=tx.execute("SHOW CONSTRAINTS YIELD name,type,entityType,labelsOrTypes,properties RETURN name,type,entityType,labelsOrTypes,properties")){
                result.stream().forEach(row->constraints.add(JSON.convertValue(row,SchemaItem.class)));
            }
            var indexes=new ArrayList<SchemaItem>();
            try(var result=tx.execute("SHOW INDEXES YIELD name,type,entityType,labelsOrTypes,properties,owningConstraint WHERE owningConstraint IS NULL AND type <> 'LOOKUP' RETURN name,type,entityType,labelsOrTypes,properties")){
                result.stream().forEach(row->indexes.add(JSON.convertValue(row,SchemaItem.class)));
            }
            try{
                check(JSON.writeValueAsString(constraints.stream().sorted(Comparator.comparing(SchemaItem::name)).toList())
                        .equals(JSON.writeValueAsString(snapshot.constraints.stream().sorted(Comparator.comparing(SchemaItem::name)).toList())),"Restored constraint definitions differ");
                check(JSON.writeValueAsString(indexes.stream().sorted(Comparator.comparing(SchemaItem::name)).toList())
                        .equals(JSON.writeValueAsString(snapshot.indexes.stream().sorted(Comparator.comparing(SchemaItem::name)).toList())),"Restored index definitions differ");
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
    }
    static boolean equalProperties(Map<String,Property> expected,Map<String,Object> actual){
        var encoded=new TreeMap<String,Property>();actual.forEach((key,value)->encoded.put(key,encode(value)));
        try{return JSON.writeValueAsString(new TreeMap<>(expected)).equals(JSON.writeValueAsString(encoded));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    static String logical(Snapshot snapshot)throws Exception{
        var content=new TreeMap<String,Object>();
        content.put("nodes",snapshot.nodes.stream().sorted(Comparator.comparing(SavedNode::key)).toList());
        content.put("relationships",snapshot.relationships.stream().sorted(Comparator.comparing(SavedRelationship::key)).toList());
        content.put("constraints",snapshot.constraints.stream().sorted(Comparator.comparing(SchemaItem::name)).toList());
        content.put("indexes",snapshot.indexes.stream().sorted(Comparator.comparing(SchemaItem::name)).toList());
        return JSON.writeValueAsString(content);
    }
}
