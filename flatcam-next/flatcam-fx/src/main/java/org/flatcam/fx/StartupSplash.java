package org.flatcam.fx;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Independent EDT: the splash can repaint while Glass/Prism and the JavaFX scene are being initialized. */
final class StartupSplash implements AutoCloseable {
    private static final System.Logger LOG=System.getLogger(StartupSplash.class.getName());
    static final int LOGICAL_WIDTH=620, LOGICAL_HEIGHT=300;
    static final long READING_NANOS=java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    private final AtomicBoolean closed=new AtomicBoolean();
    private final ThemeOption theme;
    private final String screenId;
    private final LongSupplier clock;
    private volatile String message=I18n.text("ui.9c6ce7dbb362");
    // All window/timer state belongs exclusively to Swing's EDT. Never wait for it from the FX thread.
    private JWindow window;
    private Artwork artwork;
    private Timer timer;
    private boolean ready;
    private long firstReadyPaint=Long.MIN_VALUE;

    static boolean enabled(String[] args, boolean preference, String override, boolean headless) {
        var options=Arrays.asList(args);
        return !headless && !options.contains("--no-splash") && !options.contains("--diagnostics-probe")
                && !options.contains("--probe") && (override==null ? preference : Boolean.parseBoolean(override));
    }

    static StartupSplash open(ThemeOption theme,String screenId) {
        return open(theme,screenId,System::nanoTime);
    }

    static StartupSplash open(ThemeOption theme,String screenId,LongSupplier clock) {
        var splash=new StartupSplash(theme,screenId,clock);
        SwingUtilities.invokeLater(splash::show);
        return splash;
    }

    private StartupSplash(ThemeOption theme,String screenId,LongSupplier clock) { this.theme=theme; this.screenId=screenId; this.clock=clock; }

    void message(String text) {
        if (text==null || text.isBlank() || closed.get()) return;
        message=displayText(text);
        SwingUtilities.invokeLater(()-> {
            if (artwork!=null && !closed.get()) {
                artwork.message=message;
                artwork.getAccessibleContext().setAccessibleDescription(message);
                artwork.repaint();
            }
        });
    }

    void keepVisible() {
        SwingUtilities.invokeLater(()-> { if(window!=null && !closed.get()) window.toFront(); });
    }

    /** Called after the main scene has pulsed and the active render adapter query has completed. */
    void ready(GraphicsRuntimeInfo graphics) {
        SwingUtilities.invokeLater(()-> {
            if(closed.get() || artwork==null || ready) return;
            ready=true;
            artwork.graphicsStatus=GraphicsStatus.of(graphics);
            artwork.message=I18n.text("ui.b8a706aadcf6");
            artwork.footer=I18n.text("ui.b3e2d06129eb");
            artwork.onReadyPaint=()->firstReadyPaint=clock.getAsLong();
            artwork.getAccessibleContext().setAccessibleDescription(artwork.message+" "+artwork.graphicsStatus.mode()+". "+artwork.graphicsStatus.device()+". "+artwork.footer);
            artwork.setToolTipText(artwork.graphicsStatus.mode()+" — "+artwork.graphicsStatus.device());
            window.toFront(); artwork.repaint();
        });
    }

    private void tick() {
        if(closed.get() || artwork==null) return;
        if(firstReadyPaint!=Long.MIN_VALUE) {
            long remaining=READING_NANOS-(clock.getAsLong()-firstReadyPaint);
            if(remaining<=0) { close(); return; }
            artwork.footer=I18n.text("ui.9b6f05659833")+((remaining+999_999_999L)/1_000_000_000L)+" s";
        }
        artwork.repaint();
    }

    private void show() {
        if (closed.get()) return;
        try {
            GraphicsDevice device=selectDevice(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices(),screenId);
            GraphicsConfiguration configuration=device.getDefaultConfiguration();
            window=new JWindow(configuration);
            window.setName(I18n.text("ui.da231af29b4d"));
            window.setType(Window.Type.POPUP);
            window.setFocusableWindowState(false);
            window.setAutoRequestFocus(false);
            artwork=new Artwork(theme,AboutInfo.build().getProperty("appVersion",I18n.text("ui.90e590455651")));
            artwork.message=message;
            artwork.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent event) { if(ready) close(); }
            });
            window.setContentPane(artwork);
            Rectangle usable=new Rectangle(configuration.getBounds());
            Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(configuration);
            usable.x+=insets.left; usable.y+=insets.top;
            usable.width-=insets.left+insets.right; usable.height-=insets.top+insets.bottom;
            window.setBounds(centeredBounds(usable));
            window.setBackground(artwork.palette.background());
            if(device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))
                window.setShape(windowShape(window.getWidth(),window.getHeight()));
            if(closed.get()) { closeOnEdt(); return; }
            window.setVisible(true);
            timer=new Timer(33,event->tick());
            timer.start();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING,I18n.text("ui.8d82cd85c0a7"),failure);
            closeOnEdt(); closed.set(true);
        }
    }

    private static GraphicsDevice selectDevice(GraphicsDevice[] devices,String saved) {
        for (var device:devices) if(device.getIDstring().equals(saved)) return device;
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    }

    static Rectangle centeredBounds(Rectangle usable) {
        double scale=Math.min(1,Math.min(Math.max(1,usable.width-32)/(double)LOGICAL_WIDTH,Math.max(1,usable.height-32)/(double)LOGICAL_HEIGHT));
        int width=Math.max(1,(int)Math.round(LOGICAL_WIDTH*scale)), height=Math.max(1,(int)Math.round(LOGICAL_HEIGHT*scale));
        return new Rectangle(usable.x+(usable.width-width)/2,usable.y+(usable.height-height)/2,width,height);
    }

    static Shape windowShape(int width,int height) {
        // Match the artwork's transform when a small work area shrinks the card.
        return new RoundRectangle2D.Double(0,0,width,height,
                18.0*width/LOGICAL_WIDTH,18.0*height/LOGICAL_HEIGHT);
    }

    @Override public void close() {
        if (closed.compareAndSet(false,true)) SwingUtilities.invokeLater(this::closeOnEdt);
    }

    private void closeOnEdt() {
        if(timer!=null) { timer.stop(); timer=null; }
        if(window!=null) { window.dispose(); window=null; }
        artwork=null;
    }

    static String displayText(String value) {
        return value==null ? "" : Normalizer.normalize(value,Normalizer.Form.NFC).replaceAll("\\p{Cntrl}"," ").strip();
    }

    record GraphicsStatus(String mode,String device) {
        static GraphicsStatus of(GraphicsRuntimeInfo info) {
            if(info!=null && "com.sun.prism.sw.SWPipeline".equals(info.pipeline()))
                return new GraphicsStatus(I18n.text("ui.718b9753a47d"),I18n.text("ui.dd62ad893781"));
            if(info!=null && "com.sun.prism.d3d.D3DPipeline".equals(info.pipeline()))
                return new GraphicsStatus(I18n.text("ui.5a2f59f9ed03"),I18n.text("ui.6ac23552823d")+displayText(info.adapter()));
            return new GraphicsStatus(I18n.text("ui.59db012cd2f2"),I18n.text("ui.409e72a244b3"));
        }
    }

    record Palette(Color background,Color end,Color text,Color secondary,Color accent,Color border,Color track) {
        static Palette of(ThemeOption theme) {
            return switch(theme) {
                case CLASSIC_DARK -> new Palette(color("#000000"),color("#000000"),color("#F5F5F5"),color("#C3C3C3"),color("#8BC9FF"),color("#606060"),color("#333333"));
                case CLASSIC_LIGHT -> new Palette(color("#FFFFFF"),color("#FFFFFF"),color("#202020"),color("#555555"),color("#245FB3"),color("#B0B0B0"),color("#DEDEDE"));
                case ICE_DARK -> new Palette(color("#122333"),color("#213D51"),color("#F4FAFF"),color("#BDCEDB"),color("#71D9CD"),color("#526F83"),color("#34566B"));
                case ICE_LIGHT -> new Palette(color("#F7FBFE"),color("#E2EFF6"),color("#172F42"),color("#48677D"),color("#087C79"),color("#9DB8CB"),color("#C3D9E5"));
            };
        }
        private static Color color(String hex) { return Color.decode(hex); }
    }

    /** Resolution-independent artwork; reuses the same circuit-logo resource as the app. */
    static final class Artwork extends JPanel {
        final Palette palette;
        private final String version;
        private BufferedImage logo;
        String message=I18n.text("ui.9c6ce7dbb362");
        GraphicsStatus graphicsStatus=new GraphicsStatus(I18n.text("ui.f6c867ac989c"),"");
        String footer=I18n.text("ui.f83ad355fdae");
        Runnable onReadyPaint;
        Artwork(ThemeOption theme,String version) {
            palette=Palette.of(theme); this.version=displayText(version);
            setOpaque(true); setBackground(palette.background());
            getAccessibleContext().setAccessibleName(I18n.text("ui.da231af29b4d"));
            getAccessibleContext().setAccessibleDescription(message);
            try (var stream=StartupSplash.class.getResourceAsStream("icons/"+(theme.isDark() ? "dark/" : "")+"flatcam_icon256.png")) {
                if(stream!=null) logo=ImageIO.read(stream);
            } catch(java.io.IOException ignored) { /* The wordmark and PCB traces remain available. */ }
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g=(Graphics2D)graphics.create();
            try {
                g.scale(getWidth()/(double)LOGICAL_WIDTH,getHeight()/(double)LOGICAL_HEIGHT);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0,0,palette.background(),LOGICAL_WIDTH,LOGICAL_HEIGHT,palette.end()));
                g.fillRect(0,0,LOGICAL_WIDTH,LOGICAL_HEIGHT);
                drawBoard(g);
                if(logo!=null) g.drawImage(logo,32,44,62,62,null);
                g.setFont(new Font("Segoe UI",Font.BOLD,39)); g.setColor(palette.text());
                g.drawString("FlatCAM",108,86);
                int badge=108+g.getFontMetrics().stringWidth("FlatCAM")+14;
                g.setColor(palette.accent()); g.fillRoundRect(badge,53,48,34,10,10);
                g.setColor(palette.background()); g.setFont(new Font("Segoe UI",Font.BOLD,21)); g.drawString("FX",badge+10,78);
                g.setFont(new Font("Segoe UI",Font.PLAIN,15)); g.setColor(palette.secondary());
                g.drawString(I18n.text("ui.e2c057e95ee9"),34,137);
                g.setFont(new Font("Segoe UI",Font.PLAIN,13));
                g.drawString(version+"  ·  "+AboutInfo.channel(version),34,164);
                g.setColor(palette.text());
                drawText(g,graphicsStatus.mode(),Font.BOLD,13,186);
                g.setColor(palette.secondary());
                drawText(g,graphicsStatus.device(),Font.PLAIN,13,205);
                g.setColor(palette.text()); g.setFont(new Font("Segoe UI",Font.PLAIN,14));
                drawText(g,message,Font.PLAIN,14,232);
                g.setColor(palette.track()); g.fillRoundRect(34,246,LOGICAL_WIDTH-68,5,5,5);
                double phase=(System.nanoTime()%1_600_000_000L)/1_600_000_000.0;
                Shape old=g.getClip(); g.clip(new RoundRectangle2D.Double(34,246,LOGICAL_WIDTH-68,5,5,5));
                g.setColor(palette.accent()); g.fillRoundRect(34+(int)(phase*(LOGICAL_WIDTH-68+144))-144,246,144,5,5,5); g.setClip(old);
                g.setColor(palette.secondary()); g.setFont(new Font("Segoe UI",Font.PLAIN,11));
                drawText(g,footer,Font.PLAIN,11,276);
                g.setColor(palette.border()); g.setStroke(new BasicStroke(1));
                g.drawRoundRect(0,0,LOGICAL_WIDTH-1,LOGICAL_HEIGHT-1,18,18);
                if(onReadyPaint!=null) { var painted=onReadyPaint; onReadyPaint=null; painted.run(); }
            } finally { g.dispose(); }
        }
        private void drawBoard(Graphics2D g) {
            g.setComposite(AlphaComposite.SrcOver.derive(.16f));
            g.setColor(palette.accent()); g.setStroke(new BasicStroke(2));
            g.drawRoundRect(465,-35,180,230,22,22);
            for(int i=0;i<6;i++) {
                int y=25+i*25;
                Path2D path=new Path2D.Double(); path.moveTo(535,y); path.lineTo(510,y); path.lineTo(486,y+24); path.lineTo(445-i*9,y+24);
                g.draw(path); g.fillOval(531,y-4,8,8); g.drawOval(436-i*9,y+20,8,8);
            }
            g.setComposite(AlphaComposite.SrcOver);
        }
        static Font textFont(int style,int size,String text) {
            Font preferred=new Font("Segoe UI",style,size);
            return preferred.canDisplayUpTo(text)<0 ? preferred : new Font(Font.SANS_SERIF,style,size);
        }
        private static void drawText(Graphics2D g,String value,int style,int size,int baseline) {
            String text=displayText(value);
            g.setFont(textFont(style,size,text));
            g.drawString(fit(g.getFontMetrics(),text,LOGICAL_WIDTH-68),34,baseline);
        }
        static String fit(FontMetrics metrics,String text,int width) {
            text=displayText(text);
            if(metrics.stringWidth(text)<=width) return text;
            if(metrics.stringWidth("…")>width) return "";
            BreakIterator characters=BreakIterator.getCharacterInstance(Locale.ROOT); characters.setText(text);
            int end=characters.last();
            while(end>0 && metrics.stringWidth(text.substring(0,end)+"…")>width) end=characters.previous();
            return text.substring(0,end)+"…";
        }
    }
}
