import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Parse, without resolving dependencies, the actual Java literals used by the FX frontend. */
class UiTextInventory {
    static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char ch : value.toCharArray()) switch (ch) {
            case '"' -> result.append("\\\""); case '\\' -> result.append("\\\\");
            case '\n' -> result.append("\\n"); case '\r' -> result.append("\\r"); case '\t' -> result.append("\\t");
            default -> { if (ch < 32) result.append(String.format(Locale.ROOT,"\\u%04x",(int)ch)); else result.append(ch); }
        }
        return result.append('"').toString();
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "flatcam-fx/src/main/java/org/flatcam/fx" : args[0]);
        List<Path> paths;
        try (var files = Files.walk(root)) { paths = files.filter(p -> p.toString().endsWith(".java")).sorted().toList(); }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null,Locale.ROOT,StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null,manager,null,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(paths));
            Trees trees = Trees.instance(task);
            List<String> entries = new ArrayList<>();
            for (CompilationUnitTree unit : task.parse()) {
                String source = unit.getSourceFile().getCharContent(true).toString();
                new TreePathScanner<Void,Void>() {
                    private void recordSwitch(ExpressionTree selector, List<? extends CaseTree> cases) {
                        List<String> labels = new ArrayList<>();
                        for (CaseTree branch : cases) for (CaseLabelTree label : branch.getLabels())
                            if (label instanceof ConstantCaseLabelTree constant && constant.getConstantExpression() instanceof LiteralTree literal
                                    && literal.getValue() instanceof String value) labels.add(value);
                        if (labels.isEmpty()) return;
                        long start = trees.getSourcePositions().getStartPosition(unit, selector), end = trees.getSourcePositions().getEndPosition(unit, selector);
                        entries.add("{\"file\":"+quote(Path.of(unit.getSourceFile().toUri()).getFileName().toString())
                                +",\"start\":"+source.codePointCount(0,(int)start)+",\"end\":"+source.codePointCount(0,(int)end)
                                +",\"selector\":"+quote(source.substring((int)start,(int)end))+",\"cases\":["+String.join(",",labels.stream().map(UiTextInventory::quote).toList())+"]}");
                    }
                    @Override public Void visitSwitch(SwitchTree node, Void unused) {
                        if (args.length > 2 && args[2].equals("switches")) recordSwitch(node.getExpression(),node.getCases());
                        return super.visitSwitch(node,unused);
                    }
                    @Override public Void visitSwitchExpression(SwitchExpressionTree node, Void unused) {
                        if (args.length > 2 && args[2].equals("switches")) recordSwitch(node.getExpression(),node.getCases());
                        return super.visitSwitchExpression(node,unused);
                    }
                    @Override public Void visitLiteral(LiteralTree literal, Void unused) {
                        if (args.length > 2 && args[2].equals("switches")) return null;
                        if (!(literal.getValue() instanceof String value)) return null;
                        long start = trees.getSourcePositions().getStartPosition(unit,literal), end = trees.getSourcePositions().getEndPosition(unit,literal);
                        boolean caseLabel = false, key = false;
                        List<String> parents = new ArrayList<>(); String field = "", call = "";
                        for (TreePath path = getCurrentPath().getParentPath(); path != null; path = path.getParentPath()) {
                            Tree node = path.getLeaf(); parents.add(node.getKind().name());
                            if (node.getKind().name().equals("CONSTANT_CASE_LABEL")) caseLabel = true;
                            if (node instanceof VariableTree var && field.isEmpty()) field = var.getName().toString();
                            if (node instanceof MethodInvocationTree invocation) {
                                if (call.isEmpty()) call = invocation.getMethodSelect().toString();
                                String method = invocation.getMethodSelect().toString();
                                if (method.equals("Map.entry") || method.equals("Map.of")) {
                                    var arguments = invocation.getArguments();
                                    for (int i = 0; i < arguments.size(); i++) {
                                        long a = trees.getSourcePositions().getStartPosition(unit,arguments.get(i));
                                        long b = trees.getSourcePositions().getEndPosition(unit,arguments.get(i));
                                        if (start >= a && end <= b && (method.equals("Map.entry") ? i == 0 : i % 2 == 0)) key = true;
                                    }
                                }
                            }
                        }
                        entries.add("{\"file\":"+quote(Path.of(unit.getSourceFile().toUri()).getFileName().toString())
                                +",\"start\":"+source.codePointCount(0,(int)start)+",\"end\":"+source.codePointCount(0,(int)end)
                                +",\"value\":"+quote(value)+",\"caseLabel\":"+caseLabel+",\"mapKey\":"+key
                                +",\"field\":"+quote(field)+",\"call\":"+quote(call)+",\"parents\":"+quote(String.join("/",parents))+"}");
                        return null;
                    }
                }.scan(unit,null);
            }
            String json = "["+String.join(",\n",entries)+"]";
            if (args.length > 1) Files.writeString(Path.of(args[1]), json, StandardCharsets.UTF_8);
            else System.out.print(json);
        }
    }
}
