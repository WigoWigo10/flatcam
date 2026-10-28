package org.flatcam.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolProfile;

/** Read-only review of the submitted settings. No buffering, G-code generation or machine certification. */
final class CncPreflight {
    record Target(int tool, String field) { }
    record Value(String label, String text, Target target) { }
    record Issue(boolean blocking, String text, Target target) { }
    record Tool(int key, String name, String diameter, String cutZ, String travelZ,
                String feedXY, String feedZ, String power, List<Value> details) {
        Tool { details = List.copyOf(details); }
    }
    record Report(String source, String operation, String units, String profile,
                  List<Tool> tools, List<Value> common, List<Issue> issues) {
        Report { tools = List.copyOf(tools); common = List.copyOf(common); issues = List.copyOf(issues); }
        boolean canContinue() { return issues.stream().noneMatch(Issue::blocking); }
    }

    static Report geometry(String source, String units, GeometryCncToolPanel.Result result) {
        var rows = new ArrayList<Tool>();
        var issues = new ArrayList<Issue>();
        var common = new ArrayList<Value>();
        var profile = result.preprocessor();
        var base = result.parameters();
        nativeInfo(common,profile,units);
        boolean cutting = !profile.isLaser() && !profile.isPlotter();
        double clearance = Math.max(base.safeZ(), result.parametersByTool().values().stream()
                .mapToDouble(GeometryGCodeParameters::safeZ).max().orElse(base.safeZ()));
        if (profile.isLaser()) clearance = java.util.stream.IntStream.range(0,result.tools().size())
                .filter(i -> !result.tools().get(i).geometry().isEmpty())
                .mapToDouble(i -> result.parametersByTool().getOrDefault(i,base).safeZ()).max().orElse(base.safeZ());
        var travelPolicy=base.jobOptions().travelClearance();
        double lastTravelZ=clearance;
        var activeDiameters = new java.util.HashSet<Double>();
        for (int i = 0; i < result.tools().size(); i++) {
            var tool = result.tools().get(i);
            var p = result.parametersByTool().getOrDefault(i, base);
            boolean empty = tool.geometry().isEmpty();
            double effectiveTravel=profile.isLaser() ? p.safeZ() : travelPolicy.effective(p.safeZ(),clearance);
            if (!empty) lastTravelZ=effectiveTravel;
            if (!empty) activeDiameters.add(tool.toolDiameter());
            double depth = p.cutDepth();
            var details = new ArrayList<Value>();
            if (cutting && !empty && tool.toolProfile() == ToolProfile.V) {
                var tip = result.vTools().get(i);
                if (tip == null) issues.add(new Issue(true, "T" + (i + 1) + I18n.text("ui.7ecba51e2280"), target(i, "cnc-v-tip-dia-" + i)));
                else {
                    depth = tip.cutDepth(tool.toolDiameter());
                    details.add(value(I18n.text("ui.1feec4e1d55d"), I18n.text("ui.89567ec41c36") + number(tip.tipDiameter()) + I18n.text("ui.6fda6180d28e") + number(tip.angleDegrees())
                            + I18n.text("ui.9768f55ed772"), i, "cnc-v-tip-angle-" + i));
                }
            }
            details.add(value(profile.isRoland() ? I18n.text("ui.f4981419d2de") : I18n.text("ui.454b2dde1174"), cutting ? number(-depth) + " " + unit(units) : I18n.text("ui.bba24fda01d2"), i, "cnc-cut-depth"));
            if (profile == GCodePreprocessor.TOOLCHANGE_MANUAL && base.pauseForToolChange() && !empty)
                details.add(value(I18n.text("ui.3edd3670ebce"), "Feed Z " + number(p.feedRateZ()) + " " + unit(units)
                        + I18n.text("ui.e11acb29f47c"), i, "cnc-feed-z"));
            if (cutting && !profile.requiresProbe() && !profile.isRoland())
                details.add(value(I18n.text("ui.598eac606bd7"), number(effectiveTravel) + " " + unit(units)
                        + (travelPolicy==GeometryTravelClearance.PER_TOOL ? I18n.text("ui.5b3813375052") : I18n.text("ui.9adf9e460a04"))
                        + I18n.text("ui.d32a964b0228") + number(p.safeZ()) + ")", i, "cnc-safe-z"));
            details.add(value("Passes", cutting ? passes(depth, p.multiDepth(), p.depthPerPass())
                    + (p.multiDepth() && p.passStrategy() != GeometryPassStrategy.RETRACT_SAME_DIRECTION ? I18n.text("ui.b8fb070f5481") : "")
                    : I18n.text("ui.fc981a1b1b65"), i, "cnc-multi-depth"));
            details.add(value(I18n.text("ui.965a7648e4b2"), p.passStrategy() == GeometryPassStrategy.CONTINUOUS_ALTERNATING && p.multiDepth()
                    ? I18n.text("ui.2d1b7da75e46") : cutting && p.multiDepth() && p.passStrategy()==GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION
                    ? I18n.text("ui.8084676ac91c") : cutting ? I18n.text("ui.06c26f490331") : I18n.text("ui.55018fee5816"), i, "cnc-pass-strategy"));
            if (profile == GCodePreprocessor.Z_LASER || profile == GCodePreprocessor.MARLIN_LASER_FAN_PIN
                    || profile == GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN)
                details.add(value(I18n.text("ui.57d91052f6a7"), number(p.safeZ()), i, "cnc-safe-z"));
            details.add(value("Dwell", !p.dwell() ? I18n.text("ui.f47dfde9cb6f") : profile.isLaser() ? I18n.text("ui.db7705bb30cd") : profile.controlsSpindle(p.spindleSpeedRpm())
                    ? number(p.dwellSeconds()) + " s" : I18n.text("ui.826446dfe890"), i, "cnc-dwell"));
            details.add(value(I18n.text("ui.60fa7b5bd3b3"), p.offset() + (p.offset() == ToolPathOffset.CUSTOM ? " (" + number(p.customOffset()) + ")" : ""), i, "cnc-offset"));
            details.add(value("Extra Cut", profile.isLaser() ? I18n.text("ui.9e3cc8a124a3") : p.extraCut() ? number(p.extraCutLength()) + " " + unit(units) : I18n.text("ui.f47dfde9cb6f"), i, "cnc-extra-length"));
            details.add(value(I18n.text("ui.4ab70b7c88af"), empty ? I18n.text("ui.3fb8df6736c4") : I18n.text("ui.958178d22851") + (i + 1), i, "cnc-tools"));
            if(profile.isRoland() || profile.isPlotter())
                nativeSample(details,issues,profile,units,i,"cnc-tools",empty ? null : tool.geometry().getCoordinate());
            String cutText=cutting ? number(-depth) : "—", travelText=cutting || profile.isLaser() && profile != GCodePreprocessor.GRBL_LASER ? number(p.safeZ()) : "—";
            String feedXYText=profile.isPlotter() ? "—" : number(p.feedRate()), feedZText=cutting ? number(p.feedRateZ()) : "—";
            if(profile.isRoland()) {
                cutText=nativeValue(details,issues,units,i,"cnc-cut-depth","Cut Z",-depth,false,false);
                travelText=nativeValue(details,issues,units,i,"cnc-safe-z","Travel Z",p.safeZ(),false,false);
                feedXYText=nativeValue(details,issues,units,i,"cnc-feed","Feed XY",p.feedRate(),true,false);
                feedZText=nativeValue(details,issues,units,i,"cnc-feed-z","Feed Z",p.feedRateZ(),true,false);
                if(p.multiDepth()) nativeValue(details,issues,units,i,"cnc-depth-per-pass",I18n.text("ui.818257a55a3b"),-Math.min(depth,p.depthPerPass()),false,false);
            }
            rows.add(new Tool(i, (profile.isLaser() ? I18n.text("ui.d032112ef2c6") : "T") + (i + 1) + (empty ? I18n.text("ui.819050e28a59") : ""), number(tool.toolDiameter()),
                    cutText,travelText,feedXYText,feedZText,
                    profile.isPlotter() || profile.isRoland() ? "—" : powerText(profile, p.spindleSpeedRpm()), details));
            if (empty) issues.add(new Issue(false, "T" + (i + 1) + I18n.text("ui.6ca16626fd81"), target(i, "cnc-tools")));
            if (!empty && cutting && p.spindleSpeedRpm() == 0 && !profile.isRoland() && profile != GCodePreprocessor.REPETIER)
                issues.add(new Issue(false, "T" + (i + 1) + I18n.text("ui.0d4bcab951ab"), target(i, "cnc-power")));
            if (!empty && cutting && p.multiDepth() && p.passStrategy() == GeometryPassStrategy.CONTINUOUS_ALTERNATING)
                issues.add(new Issue(false, "T" + (i + 1) + I18n.text("ui.022775b3004a"), target(i, "cnc-pass-strategy")));
            if (!empty && cutting && p.multiDepth() && p.passStrategy()==GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION)
                issues.add(new Issue(false,"T"+(i+1)+I18n.text("ui.57dd0c310e16"),target(i,"cnc-pass-strategy")));
            if (!empty && p.offset() != ToolPathOffset.PATH)
                issues.add(new Issue(false, "T" + (i + 1) + I18n.text("ui.18f32844797a"), target(i, "cnc-offset")));
        }
        if (activeDiameters.isEmpty()) issues.add(new Issue(true, I18n.text("ui.752f69ab47a1"), null));
        if (cutting && activeDiameters.size() > 1 && !base.pauseForToolChange() && !profile.automaticToolSelection())
            issues.add(new Issue(true, I18n.text("ui.632b77caee52"), target(-1, "cnc-tool-change")));
        var pos = base.jobOptions();
        common.add(value(I18n.text("ui.d3f7187a0cb2"), profile.automaticToolSelection() ? I18n.text("ui.37ba65db3f4a")
                : base.pauseForToolChange() ? I18n.text("ui.45a2fb181b21") : I18n.text("ui.83b1836da0c7"), -1, "cnc-tool-change"));
        if (cutting) {
            positions(common, "cnc", pos.startZ(), pos.toolChangeZ() == null ? clearance : pos.toolChangeZ(),
                    pos.toolChangeX(), pos.toolChangeY(), pos.endZ() == null ? lastTravelZ : pos.endZ(), pos.endX(), pos.endY());
            nativeEnd(common,issues,profile,units,"cnc",pos.endZ()==null ? lastTravelZ : pos.endZ(),pos.endX(),pos.endY());
            common.add(value(I18n.text("ui.8c3eae9bf115"),travelPolicy.toString(),-1,"cnc-travel-clearance"));
            common.add(value(I18n.text("ui.bc15603f215e"),travelPolicy==GeometryTravelClearance.PER_TOOL ? I18n.text("ui.25a6cd938a5b")
                    : number(clearance) + " " + unit(units) + I18n.text("ui.f1cb8edcdce4"), -1, "cnc-travel-clearance"));
        } else if (profile.isLaser()) {
            double end=pos.endZ()==null ? lastTravelZ : pos.endZ();
            common.add(value(I18n.text("ui.41ce2c7fac86"), number(end) + " " + unit(units), -1, "cnc-end-z"));
            common.add(value(I18n.text("ui.0d91f99432af"),xy(pos.endX(),pos.endY())+I18n.text("ui.e1a819653a88")+number(Math.max(clearance,end))+" "+unit(units), -1,"cnc-end-xy"));
            common.add(value(I18n.text("ui.f0231d7ac01d"), I18n.text("ui.f7cde5af2cd1"), -1, "cnc-tools"));
            if(profile!=GCodePreprocessor.GRBL_LASER)
                common.add(value(I18n.text("ui.480a766e1a02"),number(clearance)+" "+unit(units)+I18n.text("ui.75bad5b65e12"),-1,"cnc-safe-z"));
            try { GCodeGenerator.validateLaserGroups(profile,base,result.parametersByTool()); }
            catch(IllegalArgumentException ex) { issues.add(new Issue(true,ex.getMessage(),target(-1,"cnc-tools"))); }
            common.add(value(I18n.text("ui.588c281177c2"), I18n.text("ui.4ac31037b19e"), -1, "cnc-preprocessor"));
        } else common.add(value(I18n.text("ui.6607f07b0453"), I18n.text("ui.2063dbcaaf18"), -1, "cnc-preprocessor"));
        if (profile.requiresProbe() && base.probing() != null) probe(common, base.probing());
        startChangeNotice(issues,pos.startZ(),pos.toolChangeZ()==null ? clearance : pos.toolChangeZ(),
                base.pauseForToolChange() || profile.automaticToolSelection(),profile,"cnc");
        exclusionNotice(issues,pos.exclusionsEnabled(),pos.exclusions());
        common.add(value(I18n.text("ui.8f7cf848f7cb"), switch (pos.routing().mode()) {
            case SOURCE -> I18n.text("ui.dec43c8e2218"); case NEAREST -> I18n.text("ui.35bd3e91c020"); case BOARDS -> I18n.text("ui.fb608f3098ec");
        }, -1, "cnc-route-mode"));
        common.add(value(I18n.text("ui.bde3d6323042"), pos.exclusionsEnabled() ? pos.exclusions().size() + I18n.text("ui.b8635ec76b15") : I18n.text("ui.19af1b43f4a5"), -1, "cnc-exclusions-pane"));
        rapid(common,issues, profile, base.rapidFeedRate(), units, "cnc-rapid-feed");
        if (travelPolicy==GeometryTravelClearance.PER_TOOL)
            issues.add(new Issue(false,I18n.text("ui.103c1f62c954"),target(-1,"cnc-travel-clearance")));
        double parkingZ=profile.isLaser() ? clearance : lastTravelZ;
        if (pos.endX() != null && pos.endZ() != null && pos.endZ() < parkingZ)
            issues.add(new Issue(false, I18n.text("ui.123e9523d0fd") + number(parkingZ) + I18n.text("ui.6c00500ce751") + number(pos.endZ()) + ".", target(-1, "cnc-end-z")));
        issues.add(new Issue(false, I18n.text("ui.aa05c5e0f37a"), null));
        limitations(issues, profile);
        return new Report(source, "Geometry → CNC", unit(units), I18n.caption(profile.label()), rows, common, issues);
    }

    static Report drilling(DrillGCodeToolPanel.Result result) {
        var image = result.source().image();
        var rows = new ArrayList<Tool>();
        var issues = new ArrayList<Issue>();
        var common = new ArrayList<Value>();
        var counts = new LinkedHashMap<Integer, int[]>();
        var samples=new LinkedHashMap<Integer,org.locationtech.jts.geom.Coordinate>();
        image.drills().forEach(hit -> { counts.computeIfAbsent(hit.toolId(), key -> new int[2])[0]++;
            samples.computeIfAbsent(hit.toolId(),key->new org.locationtech.jts.geom.Coordinate(hit.x(),hit.y())); });
        image.slots().forEach(hit -> { counts.computeIfAbsent(hit.toolId(), key -> new int[2])[1]++;
            samples.computeIfAbsent(hit.toolId(),key->new org.locationtech.jts.geom.Coordinate(hit.x1(),hit.y1())); });
        var profile = result.preprocessor();
        nativeInfo(common,profile,image.units());
        for (int id : result.orderedToolIds()) {
            var p = result.settingsByTool().get(id);
            int[] count = counts.getOrDefault(id, new int[2]);
            long curved=image.slots().stream().filter(slot->slot.toolId()==id && slot.hasPath()).count();
            var details = new ArrayList<>(List.of(value(I18n.text("ui.0c986c41fcf9"), count[0] + " / " + count[1], id, "drill-tools"),
                    value(profile.isRoland() ? I18n.text("ui.f4981419d2de") : I18n.text("ui.454b2dde1174"), number(-p.effectiveDepth()) + " " + unit(image.units()) + " (Cut Z + Offset Z)", id, "drill-cut-z"),
                    value("Offset Z", number(p.offsetZ()), id, "drill-offset-z"),
                    value("Passes", passes(p.effectiveDepth(), p.multiDepth(), p.depthPerPass()), id, "drill-multi-depth"),
                    value("Dwell", p.dwell() ? number(p.dwellSeconds()) + " s" : I18n.text("ui.f47dfde9cb6f"), id, "drill-dwell-time"),
                    value(I18n.text("ui.4780e976f1d7"), p.slots().enabled() ? I18n.text("ui.b6ca28948c16") : I18n.text("ui.77b8ef9a718c"), id, "drill-slots"),
                    value(I18n.text("ui.d4b99375c542"), number(p.slots().overlapPercent()) + "% / "
                            + (p.slots().addLastHole() ? I18n.text("ui.5fc7e20f01a4") : I18n.text("ui.f47dfde9cb6f")), id, "drill-slot-overlap")));
            if (profile == GCodePreprocessor.TOOLCHANGE_MANUAL && result.options().pauseForToolChange() && count[0]+count[1]>0)
                details.add(value(I18n.text("ui.3edd3670ebce"), "Feed Z " + number(p.feedRate()) + " " + unit(image.units())
                        + I18n.text("ui.e11acb29f47c"), id, "drill-feed-z"));
            nativeSample(details,issues,profile,image.units(),id,"drill-tools",samples.get(id));
            String cutText=number(-p.effectiveDepth()),travelText=number(p.safeZ()),feedText=number(p.feedRate());
            if(profile.isRoland()) {
                cutText=nativeValue(details,issues,image.units(),id,"drill-cut-z","Cut Z + Offset Z",-p.effectiveDepth(),false,false);
                travelText=nativeValue(details,issues,image.units(),id,"drill-safe-z","Travel Z",p.safeZ(),false,false);
                feedText=nativeValue(details,issues,image.units(),id,"drill-feed-z","Feed Z",p.feedRate(),true,false);
                if(p.multiDepth()) nativeValue(details,issues,image.units(),id,"drill-depth-per-pass",I18n.text("ui.818257a55a3b"),-Math.min(p.effectiveDepth(),p.depthPerPass()),false,false);
            }
            if(curved>0) details.add(value(I18n.text("ui.fb57e55bd12a"),curved+I18n.text("ui.55f20620986c"),id,"drill-tools"));
            rows.add(new Tool(id, "T" + id + (count[0] + count[1] == 0 ? I18n.text("ui.819050e28a59") : ""), number(image.toolDiameters().get(id)),cutText,
                    travelText, "—",feedText, profile.isRoland() ? "—" : powerText(profile, p.spindleSpeedRpm()), details));
            if (count[0] + count[1] == 0) issues.add(new Issue(false, "T" + id + I18n.text("ui.ca5e280811b2"), target(id, "drill-tools")));
            if (count[1] > 0) issues.add(new Issue(false, p.slots().enabled()
                    ? "T" + id + I18n.text("ui.0e313edafab3")
                    : "T" + id + I18n.text("ui.352a6fba5e0a"), target(id, "drill-slots")));
            if (p.spindleSpeedRpm() == 0 && !profile.isRoland() && profile != GCodePreprocessor.REPETIER)
                issues.add(new Issue(false, "T" + id + I18n.text("ui.0d4bcab951ab"), target(id, "drill-power")));
        }
        var selected = java.util.Set.copyOf(result.orderedToolIds());
        int omitted = counts.entrySet().stream().filter(entry -> !selected.contains(entry.getKey()))
                .mapToInt(entry -> entry.getValue()[0] + entry.getValue()[1]).sum();
        if (omitted > 0) issues.add(new Issue(false, omitted + I18n.text("ui.c0bb4b617161"), target(-1, "drill-tools")));
        var options = result.options();
        common.add(value(I18n.text("ui.d3f7187a0cb2"), profile.automaticToolSelection() ? I18n.text("ui.37ba65db3f4a")
                : options.pauseForToolChange() ? I18n.text("ui.45a2fb181b21") : I18n.text("ui.83b1836da0c7"), -1, "drill-tool-change"));
        positions(common, "drill", options.startZ(), options.toolChangeZ(), options.toolChangeX(), options.toolChangeY(),
                options.endMoveZ(), options.endMoveX(), options.endMoveY());
        nativeEnd(common,issues,profile,image.units(),"drill",options.endMoveZ(),options.endMoveX(),options.endMoveY());
        if (profile.requiresProbe() && options.probing() != null) probe(common, options.probing());
        startChangeNotice(issues,options.startZ(),options.toolChangeZ(),
                options.pauseForToolChange() || profile.automaticToolSelection(),profile,"drill");
        exclusionNotice(issues,options.exclusionsEnabled(),options.exclusions());
        common.add(value(I18n.text("ui.cff0cf65dbb1"), options.optimizeDrills() ? I18n.text("ui.c38ab086c02a") : I18n.text("ui.dec43c8e2218"), -1, "drill-optimize-order"));
        common.add(value(I18n.text("ui.bde3d6323042"), options.exclusionsEnabled() ? options.exclusions().size() + I18n.text("ui.b8635ec76b15") : I18n.text("ui.19af1b43f4a5"), -1, "cnc-exclusions-pane"));
        rapid(common,issues, profile, options.rapidFeedRate(), image.units(), "drill-rapid-feed");
        var active = result.orderedToolIds().stream().filter(id -> {
            int[] count = counts.getOrDefault(id, new int[2]); return count[0] + count[1] > 0;
        }).toList();
        if (!active.isEmpty() && options.endMoveX() != null) {
            double z = Math.max(options.endMoveZ(), result.settingsByTool().get(active.getLast()).safeZ());
            if (z > options.endMoveZ()) issues.add(new Issue(false, I18n.text("ui.123e9523d0fd") + number(z)
                    + I18n.text("ui.6c00500ce751") + number(options.endMoveZ()) + ".", target(-1, "drill-end-z")));
        }
        if (rows.isEmpty()) issues.add(new Issue(true, I18n.text("ui.2244c551a96a"), target(-1, "drill-tools")));
        else if (counts.entrySet().stream().filter(entry -> selected.contains(entry.getKey()))
                .mapToInt(entry -> entry.getValue()[0] + entry.getValue()[1]).sum() == 0)
            issues.add(new Issue(true, I18n.text("ui.0f090dc51300"), target(-1, "drill-tools")));
        limitations(issues, profile);
        return new Report(result.source().item().getValue(), "Excellon → CNC", unit(image.units()), I18n.caption(profile.label()), rows, common, issues);
    }

    private static void startChangeNotice(List<Issue> issues, Double start, double change, boolean enabled,
                                          GCodePreprocessor profile, String prefix) {
        if (enabled && !profile.requiresProbe() && start!=null && Double.compare(start,change)!=0)
            issues.add(new Issue(false,"Start Z " + number(start) + I18n.text("ui.eccdf519623f")
                    + number(change) + I18n.text("ui.81c8813d2ae8"),
                    target(-1,prefix.equals("drill") ? "probe-change-z" : "cnc-change-z")));
    }
    private static void exclusionNotice(List<Issue> issues, boolean enabled, List<CncExclusionArea> areas) {
        if (enabled && areas.stream().anyMatch(area -> area.strategy()==CncExclusionArea.Strategy.OVER))
            issues.add(new Issue(false,I18n.text("ui.7710eccad5a8"),target(-1,"cnc-exclusions-pane")));
    }
    private static void positions(List<Value> values, String prefix, Double start, double change, Double changeX,
                                  Double changeY, double end, Double endX, Double endY) {
        values.add(value("Start Z", start == null ? I18n.text("ui.b803f24d52ed") : number(start), -1, prefix + "-start-z"));
        values.add(value("Tool change Z", number(change) + I18n.text("ui.123178d1ada8"), -1, prefix.equals("drill") ? "probe-change-z" : "cnc-change-z"));
        values.add(value("Tool change X,Y", xy(changeX, changeY), -1, prefix.equals("drill") ? "drill-tool-change-xy" : "cnc-change-xy"));
        values.add(value("End Z", number(end), -1, prefix + "-end-z"));
        values.add(value("End X,Y", xy(endX, endY), -1, prefix + "-end-xy"));
    }
    private static void rapid(List<Value> values,List<Issue> issues, GCodePreprocessor profile, double rate, String units, String field) {
        if(profile.isRoland()) {
            try { values.add(value(I18n.text("ui.654c488aaaf1"),(rate==0 ? I18n.text("ui.48a92c95226a") : precise(rate))+" → "+precise(NativeOutputPrecision.rolandFeed(rate,true))+I18n.text("ui.69406dc866f5"),-1,field)); }
            catch(IllegalArgumentException invalid) { issues.add(new Issue(true,invalid.getMessage(),target(-1,field))); }
            return;
        }
        if(profile.isPlotter()) { values.add(value(I18n.text("ui.f2b2c0f2c6a6"),I18n.text("ui.75b543a553fd"),-1,field)); return; }
        values.add(value(I18n.text("ui.654c488aaaf1"), profile.usesRapidFeed() ? (rate == 0 ? I18n.text("ui.b803f24d52ed") : number(rate) + " " + unit(units) + "/min")
                : I18n.text("ui.f82476856d5a"), -1, field));
    }
    private static void nativeInfo(List<Value> values,GCodePreprocessor profile,String units) {
        if(!profile.isRoland() && !profile.isPlotter()) return;
        values.add(new Value(I18n.text("ui.200c61360bdd"),I18n.text("ui.6ceff054989b"),null));
        if(profile.isRoland()) {
            values.add(new Value(I18n.text("ui.784aa789b523"),I18n.text("ui.58ca52f0c18e"),null));
            values.add(new Value(I18n.text("ui.fd8dc3dcfef2"),I18n.text("ui.562194c02ac6"),null));
        } else {
            double step=NativeOutputPrecision.HPGL_STEP_MM/("IN".equalsIgnoreCase(units) ? 25.4 : 1);
            values.add(new Value(I18n.text("ui.ca4769214886"),I18n.text("ui.26a08515b7a7")+precise(step)+" "+unit(units)+I18n.text("ui.ed0f133cfff0"),null));
            values.add(new Value(I18n.text("ui.8726536247fd"),precise(NativeOutputPrecision.hpglMinimum(units))+" a "+precise(NativeOutputPrecision.hpglMaximum(units))+" "+unit(units)+I18n.text("ui.c761925982e0"),null));
        }
        values.add(new Value(I18n.text("ui.28d9b4e449dd"),I18n.text("ui.93a6d1aa362e"),null));
    }
    private static String nativeValue(List<Value> values,List<Issue> issues,String units,int tool,String field,
                                      String label,double requested,boolean feed,boolean rapid) {
        try {
            double emitted=feed ? NativeOutputPrecision.rolandFeed(requested,rapid)
                    : NativeOutputPrecision.coordinate(GCodePreprocessor.ROLAND_MDX_20,units,requested);
            if(!feed && requested!=0 && emitted==0) throw new IllegalArgumentException(I18n.text("ui.4718c7b605eb"));
            String text=precise(requested)+(Math.abs(requested-emitted)>1e-9 ? " → "+precise(emitted) : "");
            values.add(value(label+I18n.text("ui.f9e61842a2d0"),precise(requested)+" → "+precise(emitted)+" "+unit(units)+(feed ? "/min" : ""),tool,field));
            if(Math.abs(requested-emitted)>1e-9)
                issues.add(new Issue(false,label+": "+precise(requested)+I18n.text("ui.f7c300a0a2d8")+precise(emitted)+" "+unit(units)+(feed ? "/min" : "")+I18n.text("ui.2491d1715d2b"),target(tool,field)));
            return text;
        } catch(IllegalArgumentException invalid) {
            issues.add(new Issue(true,label+": "+invalid.getMessage(),target(tool,field)));
            return I18n.text("ui.6a4a43da69d7");
        }
    }
    private static void nativeSample(List<Value> values,List<Issue> issues,GCodePreprocessor profile,String units,
                                     int tool,String field,org.locationtech.jts.geom.Coordinate sample) {
        if(sample==null || !profile.isRoland() && !profile.isPlotter()) return;
        try {
            double x=NativeOutputPrecision.coordinate(profile,units,sample.x),y=NativeOutputPrecision.coordinate(profile,units,sample.y);
            values.add(value(I18n.text("ui.56da765e0c7e"),"X "+precise(sample.x)+" → "+precise(x)+"; Y "+precise(sample.y)+" → "+precise(y)+" "+unit(units),tool,field));
            if(Math.abs(sample.x-x)>1e-9 || Math.abs(sample.y-y)>1e-9)
                issues.add(new Issue(false,I18n.text("ui.f061b0dcefac")+(profile.isPlotter() ? "HPGL" : "Roland")+I18n.text("ui.d6b89614c768"),target(tool,field)));
        } catch(IllegalArgumentException invalid) { issues.add(new Issue(true,invalid.getMessage(),target(tool,field))); }
    }
    private static void nativeEnd(List<Value> values,List<Issue> issues,GCodePreprocessor profile,String units,
                                  String prefix,double z,Double x,Double y) {
        if(!profile.isRoland()) return;
        nativeValue(values,issues,units,-1,prefix+"-end-z","End Z",z,false,false);
        if(x!=null) nativeSample(values,issues,profile,units,-1,prefix+"-end-xy",new org.locationtech.jts.geom.Coordinate(x,y));
    }
    private static String precise(double value) {
        return String.format(Locale.ROOT,"%.9f",value).replaceAll("0+$","").replaceAll("\\.$","");
    }
    private static void probe(List<Value> values, ProbeToolChangeParameters probe) {
        values.removeIf(value -> value.label().startsWith("Tool change"));
        values.add(value(I18n.text("ui.c78029990dde"), number(probe.toolChangeZ()), -1, "probe-change-z"));
        values.add(value(I18n.text("ui.b689170187f1"), xy(probe.toolChangeX(), probe.toolChangeY()), -1, "probe-xy"));
        values.add(value(I18n.text("ui.7ae1d6a2bc63"), number(probe.probeDepth()), -1, "probe-depth"));
        values.add(value(I18n.text("ui.9d7201a5a46a"), number(probe.feedRate()) + I18n.text("ui.ee1ad0b0255d") + number(probe.feedRate() / 2), -1, "probe-feed"));
        values.add(value(I18n.text("ui.2202dff03c7f"), number(probe.contactZ()), -1, "probe-contact-z"));
    }
    private static void limitations(List<Issue> issues, GCodePreprocessor profile) {
        if (profile.isLaser()) {
            issues.add(new Issue(false, I18n.text("ui.c7a394da9f72"), null));
            issues.add(new Issue(false, I18n.text("ui.e6b011d26eaf"), null));
            if (profile == GCodePreprocessor.GRBL_LASER || profile == GCodePreprocessor.Z_LASER)
                issues.add(new Issue(false, I18n.text("ui.fb3647cef05d"), null));
            else {
                issues.add(new Issue(false, I18n.text("ui.bfb39f3cb997"), null));
                if (profile == GCodePreprocessor.MARLIN_LASER_FAN_PIN)
                    issues.add(new Issue(false, I18n.text("ui.5ed498ea494d"), null));
            }
        }
        if (profile == GCodePreprocessor.REPETIER) {
            issues.add(new Issue(false, I18n.text("ui.833e9f781ddb"), null));
            issues.add(new Issue(false, I18n.text("ui.a963bb6108f4"), null));
        }
        if (profile == GCodePreprocessor.BERTA_CNC) {
            issues.add(new Issue(false, I18n.text("ui.2a98c7ae4ac7"), null));
            issues.add(new Issue(false, I18n.text("ui.12993adbfa62"), null));
        }
        if (profile == GCodePreprocessor.REPETIER || profile == GCodePreprocessor.MARLIN) {
            issues.add(new Issue(false, I18n.text("ui.ad72fab202c4"), null));
        }
        if (profile == GCodePreprocessor.TOOLCHANGE_MANUAL) {
            issues.add(new Issue(false, I18n.text("ui.1a5bfef7cb45"), null));
            issues.add(new Issue(false, I18n.text("ui.bfa4c6d10df1"), null));
        }
        if (profile == GCodePreprocessor.TOOLCHANGE_CUSTOM) {
            issues.add(new Issue(false, I18n.text("ui.056653f07521"), null));
        }
        if (profile == GCodePreprocessor.ISEL_ICP_CNC) {
            issues.add(new Issue(false, I18n.text("ui.551bc50c7c28"), null));
            issues.add(new Issue(false, I18n.text("ui.c055d19b5676"), null));
        }
        if (profile == GCodePreprocessor.LINE_XYZ) {
            issues.add(new Issue(false, I18n.text("ui.b78eb28ba8ec"), null));
        }
        if (profile == GCodePreprocessor.ISEL_CNC) {
            issues.add(new Issue(false, I18n.text("ui.beced43c3234"), null));
            issues.add(new Issue(false, I18n.text("ui.47beaa3c3a96"), null));
            issues.add(new Issue(false, I18n.text("ui.61300f9e6d65"), null));
        }
        if (profile == GCodePreprocessor.MARLIN) {
            issues.add(new Issue(false, I18n.text("ui.b47b197e04cf"), null));
            issues.add(new Issue(false, I18n.text("ui.d39e5a5107c0"), null));
            issues.add(new Issue(false, I18n.text("ui.942ad8da333b"), null));
        }
        issues.add(new Issue(false, profile.requiresProbe()
                ? I18n.text("ui.0fb95639fea3")
                : I18n.text("ui.651add8c8080"), null));
    }
    private static Target target(int tool, String field) { return new Target(tool, field); }
    private static String powerText(GCodePreprocessor profile, int value) {
        return number(value) + (profile == GCodePreprocessor.REPETIER ? " PWM (0–255)"
                : profile == GCodePreprocessor.MARLIN_LASER_FAN_PIN ? " PWM (1–255)" : profile.isLaser() ? " S (laser)" : "");
    }
    private static Value value(String label, String text, int tool, String field) { return new Value(label, text, target(tool, field)); }
    private static String xy(Double x, Double y) { return x == null ? I18n.text("ui.7d64e7a0c00d") : number(x) + "; " + number(y); }
    static String number(double value) { return String.format(Locale.ROOT, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static String unit(String units) { return "MM".equalsIgnoreCase(units) ? "mm" : "in"; }
    private static String passes(double depth, boolean multi, double step) {
        return !multi ? I18n.text("ui.c886b886b84e") : MachiningPasses.count(depth, true, step) + I18n.text("ui.fa4257b7b3a3") + number(step);
    }
    private CncPreflight() { }
}
