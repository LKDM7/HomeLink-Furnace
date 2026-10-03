package fr.lkdm.homelink.furnace;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lkdm.homelink.furnace.block.FurnaceTier;
import fr.lkdm.homelink.furnace.furnace.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TranslationCompletenessTest {
    private static final Path ROOT=Path.of(System.getProperty("homelink_furnace.projectDir","."));
    private static JsonObject language(String locale)throws Exception{
        String text=Files.readString(ROOT.resolve("src/main/resources/assets/homelink_furnace/lang/"+locale+".json"),StandardCharsets.UTF_8);
        var keys=Pattern.compile("(?m)^\\s*\"([^\"]+)\"\\s*:").matcher(text);Set<String> unique=new HashSet<>();
        while(keys.find())assertTrue(unique.add(keys.group(1)),"Duplicate "+locale+" key "+keys.group(1));
        return JsonParser.parseString(text).getAsJsonObject();
    }
    private static void present(JsonObject language,String key){
        assertTrue(language.has(key),"Missing translation "+key);
        assertFalse(language.get(key).getAsString().isBlank(),"Empty translation "+key);
    }
    @Test void frenchAndEnglishHaveIdenticalNonemptyKeys()throws Exception{
        var french=language("fr_fr");var english=language("en_us");
        assertEquals(english.keySet(),french.keySet());
        for(String key:english.keySet()){present(english,key);present(french,key);}
    }
    @Test void everyLiteralAndDynamicallyDerivedUiKeyExists()throws Exception{
        var english=language("en_us");
        var literal=Pattern.compile("\"([A-Za-z][A-Za-z0-9_]*\\.homelink_furnace(?:\\.[A-Za-z0-9_]+)*)\"");
        try(var sources=Files.walk(ROOT.resolve("src/main/java/fr/lkdm/homelink/furnace"))){
            for(Path source:sources.filter(p->p.toString().endsWith(".java")).toList()){
                var matcher=literal.matcher(Files.readString(source,StandardCharsets.UTF_8));
                while(matcher.find())present(english,matcher.group(1));
            }
        }
        for(var tier:FurnaceTier.values()){
            present(english,"block.homelink_furnace."+tier.id());
            present(english,"gui.homelink_furnace.ports."+tier.number());
            present(english,"viewer.homelink_furnace.tier."+tier.number());
        }
        for(var status:FurnaceStatus.values())present(english,status.translationKey());
        for(var state:FurnaceHeat.State.values())present(english,"heat.homelink_furnace."+state.name().toLowerCase(Locale.ROOT));
        for(var state:FurnaceJobState.values())present(english,"job.homelink_furnace."+state.name().toLowerCase(Locale.ROOT));
        for(var mode:RedstoneMode.values())present(english,"redstone.homelink_furnace."+mode.name().toLowerCase(Locale.ROOT));
        for(String tab:new String[]{"production","energy","settings"})present(english,"gui.homelink_furnace."+tab);
        for(String page:new String[]{"start","energy","ports","jobs","network","troubleshooting"}){
            present(english,"manual.homelink_furnace."+page+".title");present(english,"manual.homelink_furnace."+page+".body");
        }
        String screen=Files.readString(ROOT.resolve("src/main/java/fr/lkdm/homelink/furnace/client/FurnaceScreen.java"),StandardCharsets.UTF_8);
        var gui=Pattern.compile("(?:text|button|line|label)\\((?:graphics,\\s*)?\"([a-z0-9_]+)\"").matcher(screen);
        while(gui.find())present(english,"gui.homelink_furnace."+gui.group(1));
        String device=Files.readString(ROOT.resolve("src/main/java/fr/lkdm/homelink/furnace/homelink/FurnaceDevice.java"),StandardCharsets.UTF_8);
        var metrics=Pattern.compile("(?:integer|percentage|enumeration|bool|count|decimal|name)\\(\"([a-z0-9_]+)\"").matcher(device);
        while(metrics.find())present(english,"metric.homelink_furnace."+metrics.group(1));
        var events=Pattern.compile("id\\(\"(furnace_[a-z_]+)\"").matcher(device);
        while(events.find())present(english,"event.homelink_furnace."+events.group(1));
    }
}
