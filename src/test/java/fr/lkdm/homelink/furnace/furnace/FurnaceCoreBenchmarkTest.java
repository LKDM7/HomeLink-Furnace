package fr.lkdm.homelink.furnace.furnace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** CPU microbenchmark of pure jobs. No chunks, networking, HE network or real TPS measurement. */
class FurnaceCoreBenchmarkTest {
    private static final int LANES=8,TICKS=2_000,DURATION=100;
    private static final ResourceLocation RECIPE=ResourceLocation.parse("minecraft:iron_ingot_from_smelting_raw_iron");
    private static FurnaceJob job(long id){return new FurnaceJob(new UUID(0,id),RECIPE,
            new ItemStack(Items.RAW_IRON),new ItemStack(Items.IRON_INGOT),null,null,0,200,DURATION,100,.7f);}
    private record Measurement(int machines,int ticks,long laneVisits,long completed,long energy,long elapsedNanos){}
    private static Measurement measure(int machines){
        FurnaceJob[][] jobs=new FurnaceJob[machines][LANES];long identity=0;
        for(int m=0;m<machines;m++)for(int lane=0;lane<LANES;lane++)jobs[m][lane]=job(++identity);
        long completed=0,energy=0,start=System.nanoTime();
        for(int tick=0;tick<TICKS;tick++)for(int m=0;m<machines;m++)for(int lane=0;lane<LANES;lane++){
            var job=jobs[m][lane];energy+=job.advance(100);
            if(job.finish()){completed++;jobs[m][lane]=job(++identity);}
        }
        long elapsed=System.nanoTime()-start;
        assertEquals((long)machines*LANES*TICKS/DURATION,completed);assertEquals(completed*100,energy);
        return new Measurement(machines,TICKS,(long)machines*LANES*TICKS,completed,energy,elapsed);
    }
    @Test void fiftyAndHundredTierThreePureJobSimulationsConserveEnergy()throws Exception{
        measure(10); // Warm the JVM before recording; no timing pass/fail threshold.
        var fifty=measure(50);var hundred=measure(100);
        String json="{\n  \"scope\":\"Pure FurnaceJob CPU simulation; excludes world, ports, heat and networking; does not measure TPS\",\n  \"measurements\":[\n";
        Measurement[] measurements={fifty,hundred};
        for(int i=0;i<measurements.length;i++){
            var result=measurements[i];
            json+="    {\"machines\":"+result.machines+",\"lanesPerMachine\":8,\"ticks\":"+result.ticks
                    +",\"laneVisits\":"+result.laneVisits+",\"completed\":"+result.completed+",\"energyHE\":"+result.energy
                    +",\"elapsedNanos\":"+result.elapsedNanos+"}"+(i==0?",":"")+"\n";
            System.out.println("Furnace pure-job benchmark: "+result);
        }
        json+="  ]\n}\n";
        Path report=Path.of(System.getProperty("homelink_furnace.projectDir",".")).resolve("build/reports/furnace-core-benchmark.json");
        Files.createDirectories(report.getParent());Files.writeString(report,json);
    }
}
