using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Text;

// A GUI entry point for preinstalled javaw/pythonw with repeatable local review arguments.
// This does not automate UI. All clicks/keys are performed by the Computer Use tool.
class ReviewAppLauncher {
    static string Quote(string value) {
        var text=new StringBuilder("\""); int slashes=0;
        foreach(char c in value) {
            if(c=='\\') { slashes++; continue; }
            if(c=='\"') { text.Append('\\',slashes*2+1); text.Append(c); }
            else { text.Append('\\',slashes); text.Append(c); }
            slashes=0;
        }
        text.Append('\\',slashes*2); text.Append('"'); return text.ToString();
    }
    [STAThread] static void Main() {
        string config=Path.ChangeExtension(Assembly.GetExecutingAssembly().Location,".launch");
        try {
            var lines=File.ReadAllLines(config);
            var args=new StringBuilder();
            for(int i=2;i<lines.Length;i++) { if(i>2) args.Append(' '); args.Append(Quote(lines[i])); }
            Process.Start(new ProcessStartInfo(lines[0],args.ToString()) { WorkingDirectory=lines[1], UseShellExecute=false, CreateNoWindow=true });
        } catch(Exception error) { File.WriteAllText(config+".error",error.ToString()); }
    }
}
