package org.flatcam.fx;

/** Context-aware help for every persisted default; never infer units from a caption alone. */
final class ToolDefaultsDescriptions {
    private ToolDefaultsDescriptions() { }

    static String text(ToolDefaults.Setting setting, boolean metric) {
        String body = specific(setting);
        if (setting.key().startsWith("editor.")) {
            body = I18n.text("ui.daa8f5884b69") + setting.label() + I18n.text("ui.cb4904e125e4");
            if (setting.key().endsWith(".selectionlimit")) body += I18n.text("ui.d09def098953");
            if (setting.key().startsWith("editor.excellon.") && (setting.key().endsWith("pitchx") || setting.key().endsWith("pitchy") || setting.key().endsWith("rows") || setting.key().endsWith("columns"))) body += I18n.text("ui.62c9fb0f78df");
            if (setting.key().endsWith(".diameter")) body += I18n.text("ui.5b4902320e95");
            if (setting.kind() == ToolDefaults.Kind.COLOR) body += I18n.text("ui.665564621f49");
        }
        if (setting.key().startsWith("export.")) {
            body = I18n.text("ui.27ad86d781a0");
            if (setting.key().endsWith(".zeros")) body += setting.key().contains("gerber")
                ? I18n.text("ui.edabd3b0f7d8")
                : I18n.text("ui.ee4ad1252d96");
            if (setting.key().endsWith(".slots")) body += I18n.text("ui.9383c2fb5322");
            if (setting.key().endsWith(".format")) body += I18n.text("ui.947a800882a6");
        }
        if (body == null) {
            String caption = caption(setting);
            body = PanelTooltips.help(context(setting), caption);
        }
        if (body == null) throw new IllegalArgumentException(I18n.text("ui.dc14d86c8ff9") + setting.key());
        body = body.replace(I18n.text("ui.aa1e925251e3"), "")
                .replace(I18n.text("ui.d35139417031"), "");
        if (setting.perUnit()) body += I18n.text("ui.3970547f0ca7") + (metric ? "mm" : "in")
                + (isFeed(setting) ? "/min" : "") + I18n.text("ui.b615ac02add9");
        if (!setting.choices().isEmpty()) body += I18n.text("ui.52d1c9e00d5e") + String.join(", ", setting.choices()) + ".";
        return body + I18n.text("ui.5fe4883c4d2a") + setting.factory(metric)
                + I18n.text("ui.34edd649b134")
                + (setting.key().startsWith("display.") ? I18n.text("ui.116020e7afe3") : I18n.text("ui.1ba130c2bd84"));
    }

    private static boolean isFeed(ToolDefaults.Setting s) {
        return s.key().endsWith(".feedz") || s.key().endsWith(".feedrate") || s.key().endsWith(".feedrapid");
    }

    private static String context(ToolDefaults.Setting s) {
        return switch (s.tool()) {
            case "Cutout" -> "Cutout Tool";
            case "Isolation" -> "Isolation Tool";
            case "NCC" -> "NCC Tool";
            case "Paint" -> "Paint Tool";
            case "Film" -> "Film Tool";
            case "Panelize" -> "Panelize Tool";
            case "2-Sided" -> I18n.text("ui.b595bfb73393");
            case "Copper Thieving" -> "Copper Thieving Tool";
            case "Rules Check" -> "Rules Check Tool";
            case "Fiducials" -> "Fiducials Tool";
            case "Corner Markers" -> "Corner Markers Tool";
            case "QRCode" -> "QRCode Tool";
            case "Etch Compensation" -> "Etch Compensation Tool";
            case "Punch Gerber" -> "Punch Gerber Tool";
            case "Extract Drills" -> "Extract Drills Tool";
            case "Invert Gerber" -> "Invert Gerber Tool";
            case "SolderPaste" -> "SolderPaste Tool";
            case "Calibration" -> "Calibration Tool";
            case "Optimal" -> "Optimal Tool";
            case "Drilling" -> "Drilling Tool";
            case "Geometry CNC", "CNC Job (geral)" -> "Geometry CNC Job";
            case "Excellon Milling" -> "Milling Tool";
            default -> I18n.text("ui.6706dbe8b723");
        };
    }

    private static String caption(ToolDefaults.Setting s) {
        String field = s.key().substring(s.key().indexOf('.')+1);
        return switch (field) {
            case "tooldia", "newdia", "newnozzle" -> s.tool().equals("Excellon Milling") ? "Tool diameter (mm)" : "Tool dia";
            case "tooltype" -> "TT";
            case "type" -> "Isolation type";
            case "order" -> "Tool order";
            case "checkvalidity" -> "Check validity";
            case "forcedrest" -> "Forced Rest";
            case "milling" -> "Milling type";
            case "thinz" -> "Thin Depth";
            case "gapsize" -> "Gap size";
            case "gaptype" -> I18n.text("ui.38015c03d086");
            case "gaps" -> "Gaps";
            case "bitediameter" -> "M-Bites Dia";
            case "bitespacing" -> "M-Bites Spacing";
            case "convex" -> "Convex Shape";
            case "spacingcolumns" -> I18n.text("ui.4a0b50a2b1ce");
            case "spacingrows" -> I18n.text("ui.ce00249f5b21");
            case "limitwidth" -> I18n.text("ui.5d6a22d7076b");
            case "limitheight" -> I18n.text("ui.196624f8ac36");
            case "drilldia" -> I18n.text("ui.7cd23abc62e0");
            case "dotdiameter", "squaresize", "linesize", "robberthickness", "thickness", "size" -> I18n.text("ui.b768a470f771");
            case "dotspacing", "squarespacing", "linespacing" -> I18n.text("ui.f53807aa6cf5");
            case "robbermargin", "maskclearance", "clearance" -> I18n.text("ui.dfea8d0b0700");
            case "travelz" -> "Travel Z";
            case "toolchangez", "probechangez" -> I18n.text("ui.f57f7231f865");
            case "verificationz" -> I18n.text("ui.7bc80ebbfc59");
            case "vtipdia" -> "V-Tip Dia";
            case "vtipangle" -> I18n.text("ui.e9ab82cb192c");
            default -> s.label();
        };
    }

    private static String specific(ToolDefaults.Setting s) {
        String key = s.key();
        if (key.startsWith("display.")) {
            if(key.startsWith("display.cnc.")) return I18n.text("ui.fa693d21c4fb");
            if (key.equals("display.gerber.follow")) return I18n.text("ui.2cdd1cde7f69");
            if (key.equals("display.gerber.aperturetable")) return I18n.text("ui.7dfb138c6b26");
            if (s.kind() == ToolDefaults.Kind.COLOR) return I18n.text("ui.baa07e368cdf");
            if (key.endsWith(".plot")) return I18n.text("ui.5d742879ac65");
            if (key.endsWith(".solid")) return I18n.text("ui.49e69952c72a");
            if (key.endsWith(".multicolor")) return I18n.text("ui.cc28facb205e");
        }
        if (key.equals("geometry.editorcirclesteps"))
            return I18n.text("ui.f44193ad60df");
        if (key.equals("ncc.tooldia") || key.equals("paint.tooldia"))
            return I18n.text("ui.daa022cb0414");
        if (key.equals("ncc.seedpolicy"))
            return I18n.text("ui.6c40ce53a35a");
        if (key.endsWith(".tooltype")) return I18n.text("ui.7d57dc1f9b6c");
        if (key.equals("cutout.margin")) return PanelTooltips.help("Cutout Tool","Margin");
        if (key.equals("fiducials.margin")) return I18n.text("ui.3dd1e46fd590");
        if (key.equals("cnc.preprocessor"))
            return I18n.text("ui.01bfc35ea137");
        if (key.startsWith("import.")) return switch (key) {
            case "import.gerber.units" -> I18n.text("ui.7c9bf0cbede2");
            case "import.gerber.circlesteps" -> I18n.text("ui.3bc8bc319d0d");
            case "import.excellon.integer.in", "import.excellon.integer.mm" -> I18n.text("ui.97a0b59de961");
            case "import.excellon.zeros" -> I18n.text("ui.3ba09f061500");
            case "import.excellon.units" -> I18n.text("ui.0d0f92c0ca59");
            case "import.excellon.decimals.in", "import.excellon.decimals.mm" -> I18n.text("ui.e66d66988505");
            default -> null;
        };
        if (key.startsWith("rules.")) {
            boolean enabled = key.endsWith(".enabled");
            String rule = key.substring(6).replace(".enabled", "");
            String meaning = switch (rule) {
                case "trace_size" -> I18n.text("ui.e27b1998565a");
                case "copper_to_copper" -> I18n.text("ui.39f1c5a86b4c");
                case "copper_to_outline" -> I18n.text("ui.8ed858a945e2");
                case "silk_to_silk" -> I18n.text("ui.e7b8b570e3f5");
                case "silk_to_mask" -> I18n.text("ui.e26f38c5632f");
                case "silk_to_outline" -> I18n.text("ui.af88639b2eb6");
                case "mask_sliver" -> I18n.text("ui.58ca4dc9e07c");
                case "annular_ring" -> I18n.text("ui.7cb4f9da0850");
                case "hole_to_hole" -> I18n.text("ui.8bb7eff83800");
                case "hole_size" -> I18n.text("ui.7de40afecd6b");
                default -> throw new IllegalArgumentException(I18n.text("ui.94a43bc6f4e2") + key);
            };
            return enabled ? I18n.text("ui.40fca7573aa2") + meaning + I18n.text("ui.d213c0921593")
                    : I18n.text("ui.9907fcc7f052") + meaning + I18n.text("ui.19da5e1e3c26");
        }
        if (key.endsWith(".depthperpass")) return I18n.text("ui.bf0c2c37e061");
        if (key.endsWith(".multidepth")) return I18n.text("ui.f6bb6c9b346b");
        if (key.endsWith(".spindle")) return I18n.text("ui.b8b816e3fdad");
        if (key.endsWith(".dwelltime")) return I18n.text("ui.8fdaa06153d7");
        if (key.endsWith(".feedrapid")) return I18n.text("ui.7c2322bb5cc0");
        if (key.equals("drilling.endz")) return I18n.text("ui.c4302f8429b2");
        if (key.equals("geometry.cutdepth")) return I18n.text("ui.6f92ed8ee28f");
        if (key.equals("geometry.probechangez")) return I18n.text("ui.1da32985addd");
        if (key.equals("film.boundary")) return I18n.text("ui.5996c8f45ada");
        if (key.equals("film.scalestroke")) return I18n.text("ui.20fe3652021d");
        if (key.equals("film.padsize")) return I18n.text("ui.19b7c74f74b4");
        if (key.equals("film.scalex") || key.equals("film.scaley")) return I18n.text("ui.4a831ece73d0");
        if (key.equals("film.skewx") || key.equals("film.skewy")) return I18n.text("ui.27feec0deda4");
        if (key.equals("film.dpi")) return I18n.text("ui.28b758fbfe43");
        if (key.equals("film.negative")) return I18n.text("ui.5f841073c4f0");
        if (key.equals("thieving.minarea")) return I18n.text("ui.05bb3de5bf3c");
        if (key.equals("fiducials.size")) return I18n.text("ui.f3bf2ad83ec3");
        if (key.equals("fiducials.thickness")) return I18n.text("ui.d49cdf0cfb07");
        if (key.equals("corners.drill")) return I18n.text("ui.3782980caac8");
        if (key.equals("corners.length")) return I18n.text("ui.d8c42765c768");
        if (key.startsWith("qrcode.")) return switch (key) {
            case "qrcode.version" -> I18n.text("ui.7cd8e2ac7cc0");
            case "qrcode.boxsize" -> I18n.text("ui.e4d2efa2fdfa");
            case "qrcode.border" -> I18n.text("ui.e6dc3da9a1ba");
            default -> null;
        };
        if (key.equals("etch.thickness")) return I18n.text("ui.cfbf9bf9649c");
        if (key.startsWith("punch.") || key.startsWith("extract.")) {
            if (key.endsWith(".fixeddiameter")) return I18n.text("ui.ddfdaaf023d3");
            if (key.endsWith(".factor")) return I18n.text("ui.2280a8fd5af3");
            return I18n.text("ui.c9fbb66f8e04");
        }
        if (key.equals("optimal.precision")) return I18n.text("ui.8913ed51ad27");
        return null;
    }
}
