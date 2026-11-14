package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.flatcam.fx.MainToolPersistenceTest.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.excellon.ExcellonImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;

/** Optional replay of the reporter's original ZIP through real UI import queues; never opens a Stage. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named="flatcam.test.relayZip",matches=".+")
class GerberRelayImportTest {
    @TempDir Path directory;
    @Test void originalLayersAndDrillsArePublishedInTheProject() throws Exception {
        var gerbers=new ArrayList<File>();var drills=new ArrayList<File>();
        try(var zip=new ZipFile(System.getProperty("flatcam.test.relayZip"))){
            for(var entries=zip.entries();entries.hasMoreElements();){
                var entry=entries.nextElement();String name=entry.getName(),lower=name.toLowerCase(Locale.ROOT);
                if(!lower.endsWith(".drl")&&!lower.startsWith("gerber_"))continue;
                Path file=directory.resolve(name).normalize();assertTrue(file.startsWith(directory));assertTrue(entry.getSize()>=0&&entry.getSize()<2_000_000);
                Files.createDirectories(file.getParent());try(var stream=zip.getInputStream(entry)){Files.copy(stream,file);}
                if(lower.endsWith(".drl"))drills.add(file.toFile());else {
                    assertTrue(CamFileFilters.gerber().getExtensions().contains("*"+lower.substring(lower.lastIndexOf('.'))));gerbers.add(file.toFile());
                }
            }
        }
        assertEquals(7,gerbers.size());assertEquals(2,drills.size());
        try(var s=new MainCamFlowTest.Session("MM")){
            TerminalPanelTest.fx(()->{call(s.window,"openGerberQueue",new Class<?>[]{List.class,int.class},gerbers,0);return null;});awaitQueue(s);
            TerminalPanelTest.fx(()->{
                var imported=(Map<?,GerberImage>)field(s.window,"gerberByItem");assertEquals(8,imported.size());
                for(var image:imported.values()){assertEquals("MM",image.units());assertFalse(image.solidGeometry().isEmpty());assertTrue(image.solidGeometry().isValid());}
                call(s.window,"openExcellonQueue",new Class<?>[]{List.class,int.class},drills,0);return null;
            });awaitQueue(s);
            TerminalPanelTest.fx(()->{
                var imported=(Map<?,ExcellonImage>)field(s.window,"excellonByItem");assertEquals(2,imported.size());
                assertEquals(21,imported.values().stream().mapToInt(ExcellonImage::totalDrills).sum());
                assertEquals(1,imported.values().stream().filter(ExcellonImage::isEmpty).count());return null;
            });
        }
    }
    private static void awaitQueue(MainCamFlowTest.Session session)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(System.nanoTime()<deadline){if(TerminalPanelTest.fx(()->field(session.window,"runningJob")==null))return;Thread.sleep(10);}
        fail("The import queue did not finish");
    }
}
