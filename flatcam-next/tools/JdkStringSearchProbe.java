import java.util.*;
public class JdkStringSearchProbe {
 static volatile int sink;
 static boolean find(String haystack,String needle) { return haystack.contains(needle); }
 public static void main(String[] args) {
  String needle = new String(new char[]{'3','.','0','0','2','5'});
  String[] haystacks = {"3.00125 → 3.0025 mm","3.00125 → 3.0025 in","native value 3.0025","no such value here","125 → 126","3.00125 → 3.0025 mm /min"};
  for(int i=0;i<500000;i++) {
   int n=i%haystacks.length; String h=haystacks[n]; boolean expected=n==0||n==1||n==2||n==5;
   boolean actual=find(h,needle);
   if(actual!=expected) throw new AssertionError("iteration="+i+" actual="+actual+" expected="+expected+" value="+h+" codepoints="+h.codePoints().boxed().toList());
   if(actual) sink++;
  }
  System.out.println("PASS "+sink+" "+System.getProperty("java.runtime.version"));
 }
}
