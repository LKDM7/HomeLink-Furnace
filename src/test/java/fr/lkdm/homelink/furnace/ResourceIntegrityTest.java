package fr.lkdm.homelink.furnace;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Prevent broken resource references and missing translations from reaching a release JAR. */
class ResourceIntegrityTest {
    private static JsonObject json(String path) throws Exception {
        try(var stream=ResourceIntegrityTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream,"Missing resource "+path);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void frenchAndEnglishHaveIdenticalNonemptyKeys() throws Exception {
        var french=json("assets/homelink_furnace/lang/fr_fr.json");var english=json("assets/homelink_furnace/lang/en_us.json");
        assertEquals(english.keySet(),french.keySet());
        assertTrue(english.size()>100,"Incomplete language catalog");
        for(var entry:french.entrySet())assertFalse(entry.getValue().getAsString().isBlank(),entry.getKey());
        for(var entry:english.entrySet())assertFalse(entry.getValue().getAsString().isBlank(),entry.getKey());
    }
    @Test void everyBlockstateResolvesToAModelWithPresentTextures() throws Exception {
        Set<String> models=new HashSet<>();
        for(int tier=1;tier<=3;tier++) {
            var variants=json("assets/homelink_furnace/blockstates/industrial_furnace_"+tier+".json").getAsJsonObject("variants");
            assertEquals(32,variants.size(),"Four orientations and eight possible local cells");
            for(var entry:variants.entrySet())models.add(entry.getValue().getAsJsonObject().get("model").getAsString());
        }
        assertEquals(11,models.size(),"One + two + eight real cell models");
        for(String id:models)validateModel(id,new HashSet<>());
    }
    @Test void itemAndAnimatedModelsHaveValidGeometryAndTextureReferences() throws Exception {
        validateModel("homelink_furnace:block/fan",new HashSet<>());
        for(int tier=1;tier<=3;tier++) {
            validateModel("homelink_furnace:item/industrial_furnace_"+tier,new HashSet<>());
            validateModel("homelink_furnace:block/heat_"+tier,new HashSet<>());
        }
    }
    private static Map<String,String> validateModel(String id,Set<String> ancestry) throws Exception {
        assertTrue(ancestry.add(id),"Cyclic model parent: "+id);
        var model=json("assets/"+id.replace(":","/models/")+".json");
        Map<String,String> textures=new HashMap<>();
        if(model.has("parent")) {
            String parent=model.get("parent").getAsString();
            if(parent.startsWith("homelink_furnace:"))textures.putAll(validateModel(parent,ancestry));
            else assertTrue(parent.startsWith("minecraft:"),id+" unexpected external parent "+parent);
        }
        if(model.has("textures"))for(var entry:model.getAsJsonObject("textures").entrySet())
            textures.put(entry.getKey(),entry.getValue().getAsString());
        for(String key:textures.keySet())validateTexture("#"+key,textures,id);
        if(model.has("elements"))for(var entry:model.getAsJsonArray("elements")) {
            var element=entry.getAsJsonObject();
            double[] from=vector(element.getAsJsonArray("from"),3,-16,32,id+" from");
            double[] to=vector(element.getAsJsonArray("to"),3,-16,32,id+" to");
            for(int axis=0;axis<3;axis++)assertTrue(from[axis]<=to[axis],id+" inverted element on axis "+axis);
            var faces=element.getAsJsonObject("faces");assertNotNull(faces,id+" element missing faces");
            assertFalse(faces.isEmpty(),id+" element has no visible faces");
            for(var faceEntry:faces.entrySet()) {
                var face=faceEntry.getValue().getAsJsonObject();
                int normal=switch(faceEntry.getKey()) {
                    case "east","west"->0;case "up","down"->1;case "north","south"->2;
                    default->throw new AssertionError(id+" invalid face "+faceEntry.getKey());
                };
                for(int axis=0;axis<3;axis++)if(axis!=normal)
                    assertTrue(to[axis]>from[axis],id+" zero-area "+faceEntry.getKey()+" face");
                validateTexture(face.get("texture").getAsString(),textures,id);
                if(face.has("uv"))vector(face.getAsJsonArray("uv"),4,0,16,id+" "+faceEntry.getKey()+" UV");
                if(face.has("rotation")) {
                    int rotation=face.get("rotation").getAsInt();
                    assertTrue(rotation==0||rotation==90||rotation==180||rotation==270,id+" invalid UV rotation");
                }
            }
            if(element.has("rotation")) {
                var rotation=element.getAsJsonObject("rotation");
                vector(rotation.getAsJsonArray("origin"),3,-16,32,id+" rotation origin");
                assertTrue(Set.of("x","y","z").contains(rotation.get("axis").getAsString()),id+" invalid rotation axis");
                double angle=rotation.get("angle").getAsDouble();
                assertTrue(Double.isFinite(angle)&&Set.of(-45.0,-22.5,0.0,22.5,45.0).contains(angle),id+" invalid element rotation");
            }
        }
        ancestry.remove(id);
        return textures;
    }
    private static String validateTexture(String reference,Map<String,String> textures,String model) throws Exception {
        Set<String> aliases=new HashSet<>();
        while(reference.startsWith("#")) {
            assertTrue(aliases.add(reference),model+" cyclic texture alias "+reference);
            String next=textures.get(reference.substring(1));
            assertNotNull(next,model+" unresolved texture "+reference);reference=next;
        }
        if(reference.startsWith("minecraft:"))return reference;
        assertTrue(reference.startsWith("homelink_furnace:"),model+" unexpected external texture "+reference);
        String path="assets/"+reference.replace(":","/textures/")+".png";
        try(var stream=ResourceIntegrityTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream,model+" missing "+reference);
            var image=ImageIO.read(stream);assertNotNull(image,model+" invalid PNG "+reference);
            assertTrue(image.getWidth()>0&&image.getHeight()>0,model+" empty texture "+reference);
        }
        return reference;
    }
    private static double[] vector(JsonArray array,int size,double minimum,double maximum,String context) {
        assertNotNull(array,context);assertEquals(size,array.size(),context);
        double[] values=new double[size];
        for(int index=0;index<size;index++) {
            values[index]=array.get(index).getAsDouble();
            assertTrue(Double.isFinite(values[index])&&values[index]>=minimum&&values[index]<=maximum,
                    context+" value outside ["+minimum+", "+maximum+"]: "+values[index]);
        }
        return values;
    }
    @Test void craftingUpgradesAndSingleItemResultsAreDeclared() throws Exception {
        for(int tier=1;tier<=3;tier++) {
            var recipe=json("data/homelink_furnace/recipe/industrial_furnace_"+tier+".json");
            assertEquals("minecraft:crafting_shaped",recipe.get("type").getAsString());
            assertEquals("homelink_furnace:industrial_furnace_"+tier,recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(1,recipe.getAsJsonObject("result").get("count").getAsInt());
            assertEquals(3,recipe.getAsJsonArray("pattern").size());
            if(tier>1)assertEquals("homelink_furnace:industrial_furnace_"+(tier-1),recipe.getAsJsonObject("key").getAsJsonObject("F").get("item").getAsString());
        }
    }
}
