import java.util.Arrays;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Update only resolve's six travel constants, independent of constant-pool order. */
public class PatchLevelTravel {
  public static void main(String[] args) throws Exception {
    ClassReader reader = new ClassReader(System.in.readAllBytes());
    if (!reader.getClassName().equals("com/rabpit/backroom/core/LevelTravelEngine"))
      throw new IllegalArgumentException("Unexpected travel class");
    ClassWriter writer = new ClassWriter(reader, 0);
    int[] edits = new int[3];
    reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
      @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
          String signature, String[] exceptions) {
        MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!name.equals("resolve") || !descriptor.equals(
            "(Lcom/rabpit/backroom/core/GameState;Ljava/lang/String;ILjava/lang/String;Z)Lcom/rabpit/backroom/core/LevelTravelEngine$Outcome;"))
          return method;
        return new MethodVisitor(Opcodes.ASM9, method) {
          @Override public void visitLdcInsn(Object value) {
            if (Double.valueOf(0.4).equals(value)) { value = 0.5; edits[0]++; }
            super.visitLdcInsn(value);
          }
          @Override public void visitInsn(int opcode) {
            if (opcode == Opcodes.ICONST_3) { opcode = Opcodes.ICONST_4; edits[1]++; }
            super.visitInsn(opcode);
          }
          @Override public void visitIntInsn(int opcode, int value) {
            if (opcode == Opcodes.BIPUSH && (value == 40 || value == 60)) {
              value = 50; edits[2]++;
            }
            super.visitIntInsn(opcode, value);
          }
        };
      }
    }, 0);
    if (!Arrays.equals(edits, new int[] {1, 3, 2}))
      throw new IllegalStateException("Unexpected travel constants: " + Arrays.toString(edits));
    System.out.write(writer.toByteArray());
  }
}
