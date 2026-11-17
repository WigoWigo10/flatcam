package org.flatcam.fx;

import java.awt.GraphicsEnvironment;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.List;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Window;
import static java.lang.foreign.ValueLayout.*;

/** Optional, read-only Win32 geometry. Stage coordinates can be stale during a DPI transition.
 * All calls concern this process's HWND; no desktop input or display settings are changed. */
final class WindowsDpi {
    record Rect(double x,double y,double width,double height) {
        boolean valid(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(width)&&Double.isFinite(height)&&width>0&&height>0;}
    }
    record Sample(long monitor,Rect physical,Rect monitorPhysical,Screen screen,String deviceId,
                  double scaleX,double scaleY,boolean maximized,boolean busy) {
        Placement placement(){return fit(physical,monitorPhysical,screen.getBounds(),screen.getVisualBounds(),scaleX,scaleY,maximized);}
    }
    record Placement(double x,double y,double width,double height,boolean maximized) { }
    private static boolean unavailable;
    private WindowsDpi(){}

    static Sample sample(Window window){
        if(unavailable||!System.getProperty("os.name","").startsWith("Windows")||!window.isShowing())return null;
        try{
            Object peer=Class.forName("com.sun.javafx.stage.WindowHelper").getMethod("getPeer",Window.class).invoke(null,window);
            if(peer==null)return null;
            Object glass=Class.forName("com.sun.javafx.tk.quantum.WindowStage").getMethod("getPlatformWindow").invoke(peer);
            long hwnd=(long)Class.forName("com.sun.glass.ui.Window").getMethod("getNativeWindow").invoke(glass);
            return Native.read(hwnd);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){unavailable=true;return null;}
        catch(Throwable failure){unavailable=true;return null;}
    }
    static String screenId(Screen screen){
        if(unavailable||!System.getProperty("os.name","").startsWith("Windows"))return null;
        try{
            for(Object glass:(List<?>)Class.forName("com.sun.glass.ui.Screen").getMethod("getScreens").invoke(null)){
                if(!glassBounds(glass).equals(screen.getBounds()))continue;
                long handle=(long)glass.getClass().getMethod("getNativeScreen").invoke(glass);
                try(var arena=Arena.ofConfined()){
                    var info=Native.monitorInfo(MemorySegment.ofAddress(handle),arena);
                    return info==null?null:deviceId(info);
                }
            }
        }catch(Throwable failure){unavailable=true;}
        return null;
    }
    private static Rectangle2D glassBounds(Object screen)throws ReflectiveOperationException {
        Class<?> type=Class.forName("com.sun.glass.ui.Screen");
        return new Rectangle2D((int)type.getMethod("getX").invoke(screen),(int)type.getMethod("getY").invoke(screen),
            (int)type.getMethod("getWidth").invoke(screen),(int)type.getMethod("getHeight").invoke(screen));
    }
    private record Monitor(Rect bounds,String name){}
    private static String deviceId(Monitor monitor){
        // AWT origins and Glass origins differ on mixed-DPI desktops. Compare only native rectangles.
        for(var device:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()){
            var c=device.getDefaultConfiguration();var b=c.getBounds();var t=c.getDefaultTransform();
            double width=b.width*t.getScaleX(),height=b.height*t.getScaleY();
            if(close(width,monitor.bounds.width)&&close(height,monitor.bounds.height)
                &&((close(b.x,monitor.bounds.x)&&close(b.y,monitor.bounds.y))
                ||(close(b.x*t.getScaleX(),monitor.bounds.x)&&close(b.y*t.getScaleY(),monitor.bounds.y))))return device.getIDstring();
        }
        return "win32:"+monitor.name;
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<2;}

    static Placement fit(Rect window,Rect monitor,Rectangle2D screen,Rectangle2D visual,double sx,double sy,boolean maximized){
        if(!window.valid()||!monitor.valid()||!Double.isFinite(sx)||!Double.isFinite(sy)||sx<=0||sy<=0)throw new IllegalArgumentException("Invalid DPI geometry");
        double width=Math.min(window.width/sx,Math.max(1,visual.getWidth()-16));
        double height=Math.min(window.height/sy,Math.max(1,visual.getHeight()-16));
        double x=screen.getMinX()+(window.x-monitor.x)/sx,y=screen.getMinY()+(window.y-monitor.y)/sy;
        x=Math.max(visual.getMinX()+8,Math.min(x,visual.getMaxX()-width-8));
        y=Math.max(visual.getMinY()+8,Math.min(y,visual.getMaxY()-height-8));
        return new Placement(x,y,width,height,maximized);
    }
    private static final class Native {
        static final SymbolLookup USER=SymbolLookup.libraryLookup("user32",Arena.global());
        static final MethodHandle RECT=call("GetWindowRect",JAVA_INT,ADDRESS,ADDRESS);
        static final MethodHandle PID=call("GetWindowThreadProcessId",JAVA_INT,ADDRESS,ADDRESS);
        static final MethodHandle MONITOR=call("MonitorFromWindow",ADDRESS,ADDRESS,JAVA_INT);
        static final MethodHandle INFO=call("GetMonitorInfoW",JAVA_INT,ADDRESS,ADDRESS);
        static final MethodHandle ZOOMED=call("IsZoomed",JAVA_INT,ADDRESS);
        static final MethodHandle ICONIC=call("IsIconic",JAVA_INT,ADDRESS);
        static final MethodHandle ENABLED=call("IsWindowEnabled",JAVA_INT,ADDRESS);
        static final MethodHandle GUI=call("GetGUIThreadInfo",JAVA_INT,JAVA_INT,ADDRESS);
        static final MethodHandle KEY=call("GetAsyncKeyState",JAVA_SHORT,JAVA_INT);
        static final MethodHandle CONTEXT=call("SetThreadDpiAwarenessContext",ADDRESS,ADDRESS);
        private static MethodHandle call(String name,MemoryLayout result,MemoryLayout... args){
            return Linker.nativeLinker().downcallHandle(USER.find(name).orElseThrow(),FunctionDescriptor.of(result,args));
        }
        static Monitor monitorInfo(MemorySegment handle,Arena arena)throws Throwable{
            var info=arena.allocate(104,4);info.set(JAVA_INT,0,104);
            if((int)INFO.invokeExact(handle,info)==0)return null;
            StringBuilder name=new StringBuilder();for(long i=40;i<104;i+=2){char c=info.get(JAVA_CHAR,i);if(c==0)break;name.append(c);}
            return new Monitor(rect(info,4),name.toString());
        }
        static Sample read(long handle)throws Throwable{
            MemorySegment hwnd=MemorySegment.ofAddress(handle);
            try(var arena=Arena.ofConfined()){
                var pid=arena.allocate(JAVA_INT);int thread=(int)PID.invokeExact(hwnd,pid);
                if(thread==0||Integer.toUnsignedLong(pid.get(JAVA_INT,0))!=ProcessHandle.current().pid()||(int)ICONIC.invokeExact(hwnd)!=0)return null;
                MemorySegment previous=(MemorySegment)CONTEXT.invokeExact(MemorySegment.ofAddress(-4));
                try{
                    var r=arena.allocate(16,4);if((int)RECT.invokeExact(hwnd,r)==0)return null;
                    MemorySegment monitor=(MemorySegment)MONITOR.invokeExact(hwnd,2);
                    Monitor info=monitorInfo(monitor,arena);if(info==null)return null;
                    Object selected=null;
                    Class<?> type=Class.forName("com.sun.glass.ui.Screen");
                    for(Object screen:(List<?>)type.getMethod("getScreens").invoke(null)){
                        if((long)type.getMethod("getNativeScreen").invoke(screen)==monitor.address()){selected=screen;break;}
                    }
                    if(selected==null)return null;
                    Rectangle2D bounds=glassBounds(selected);
                    Screen fx=Screen.getScreens().stream().filter(s->s.getBounds().equals(bounds)).findFirst().orElse(null);
                    if(fx==null)return null;
                    var gui=arena.allocate(72,8);gui.set(JAVA_INT,0,72);
                    boolean moving=(int)GUI.invokeExact(thread,gui)!=0&&(gui.get(JAVA_INT,4)&2)!=0;
                    boolean busy=moving||(short)KEY.invokeExact(1)<0||(short)KEY.invokeExact(2)<0||(int)ENABLED.invokeExact(hwnd)==0;
                    return new Sample(monitor.address(),rect(r,0),info.bounds,fx,deviceId(info),fx.getOutputScaleX(),fx.getOutputScaleY(),(int)ZOOMED.invokeExact(hwnd)!=0,busy);
                }finally{if(previous.address()!=0){MemorySegment ignored=(MemorySegment)CONTEXT.invokeExact(previous);}}
            }
        }
        private static Rect rect(MemorySegment r,long start){
            int x=r.get(JAVA_INT,start),y=r.get(JAVA_INT,start+4);
            return new Rect(x,y,(double)r.get(JAVA_INT,start+8)-x,(double)r.get(JAVA_INT,start+12)-y);
        }
    }
}
