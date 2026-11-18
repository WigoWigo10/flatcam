import java.lang.classfile.*;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.*;
import java.lang.instrument.*;
import java.lang.management.ManagementFactory;
import java.security.ProtectionDomain;

/** Measurement-only Java 25 agent. Does not replace path selection or geometric predicates.
 * Successful calls only; a cancelled/exceptional case is rejected by the benchmark runner. */
public final class ConnectProfileAgent {
    private static final com.sun.management.ThreadMXBean MX=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static final ThreadLocal<State> LOCAL=ThreadLocal.withInitial(State::new);
    private static final class State {long calls,wall,cpu,bytes,startWall,startCpu,startBytes;int depth;}
    public record Sample(long calls,long wallNanos,long cpuNanos,long allocatedBytes,int openCalls) { }
    public static void reset(){LOCAL.set(new State());}
    public static Sample sample(){var s=LOCAL.get();return new Sample(s.calls,s.wall,s.cpu,s.bytes,s.depth);}
    public static void enter(){var s=LOCAL.get();s.calls++;if(s.depth++==0){s.startBytes=MX.getThreadAllocatedBytes(Thread.currentThread().threadId());s.startCpu=MX.getCurrentThreadCpuTime();s.startWall=System.nanoTime();}}
    public static void exit(){long now=System.nanoTime();var s=LOCAL.get();if(--s.depth==0){s.wall+=now-s.startWall;s.cpu+=MX.getCurrentThreadCpuTime()-s.startCpu;s.bytes+=MX.getThreadAllocatedBytes(Thread.currentThread().threadId())-s.startBytes;}}
    public static void premain(String args,Instrumentation instrumentation){
        if(!MX.isThreadAllocatedMemorySupported()||!MX.isCurrentThreadCpuTimeSupported())throw new IllegalStateException("Thread CPU/allocation counters unavailable");
        MX.setThreadAllocatedMemoryEnabled(true);MX.setThreadCpuTimeEnabled(true);
        System.setProperty("flatcam.connect.agent","true");
        instrumentation.addTransformer(new ClassFileTransformer(){
            @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){
                if(!"org/flatcam/cam/ncc/NccGenerator".equals(name))return null;
                try{
                    var model=ClassFile.of().parse(bytes);
                    return ClassFile.of().transformClass(model,ClassTransform.transformingMethodBodies(
                            m->m.methodName().equalsString("connectSafePaths")||m.methodName().equalsString("connectOrderedPaths"),
                            new CodeTransform(){
                                final ClassDesc agent=ClassDesc.of("ConnectProfileAgent");
                                @Override public void atStart(CodeBuilder b){b.invokestatic(agent,"enter",MethodTypeDesc.ofDescriptor("()V"));}
                                @Override public void accept(CodeBuilder b,CodeElement element){
                                    if(element instanceof ReturnInstruction)b.invokestatic(agent,"exit",MethodTypeDesc.ofDescriptor("()V"));
                                    b.with(element);
                                }
                            }));
                }catch(Throwable failure){throw new IllegalStateException("Cannot instrument Connect",failure);}
            }
        });
    }
}
