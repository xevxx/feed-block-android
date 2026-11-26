import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jf.baksmali.Baksmali;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

class DexRoundTrip {
    public static void main(String[] args) throws Exception {
        String inputs = args[0];
        String output = args[1];
        File disassembled = new File(args[2]);
        int minSdk = Integer.parseInt(args[3]);
        String targetMethod = args[4];
        List<String> classes = Arrays.asList(args).subList(5, args.length);

        SmaliOptions assemble = new SmaliOptions();
        assemble.apiLevel = minSdk;
        assemble.outputDexFile = output;
        if (!Smali.assemble(assemble, inputs)) {
            throw new IllegalStateException("Smali assembly failed");
        }

        var dex = DexFileFactory.loadDexFile(output, null);
        Set<String> verified = new HashSet<>();
        for (var definition : dex.getClasses()) {
            for (Method method : definition.getMethods()) {
                String descriptor = method.getName() + "(" + String.join("", method.getParameterTypes())
                        + ")" + method.getReturnType();
                if (descriptor.equals(targetMethod)) {
                    var implementation = method.getImplementation();
                    var instructions = implementation.getInstructions().iterator();
                    Instruction first = instructions.next();
                    if (first.getOpcode() != Opcode.RETURN_VOID || instructions.hasNext()
                            || !implementation.getTryBlocks().isEmpty()) {
                        throw new IllegalStateException("Unexpected playback implementation");
                    }
                    verified.add(definition.getType());
                }
            }
        }
        if (!verified.equals(Set.copyOf(classes)) || dex.getClasses().size() != classes.size()) {
            throw new IllegalStateException("DEX classes or playback methods differ from the plan");
        }
        BaksmaliOptions decode = new BaksmaliOptions();
        decode.apiLevel = minSdk;
        if (!Baksmali.disassembleDexFile(dex, disassembled, 2, decode)) {
            throw new IllegalStateException("Baksmali round trip failed");
        }
    }
}
