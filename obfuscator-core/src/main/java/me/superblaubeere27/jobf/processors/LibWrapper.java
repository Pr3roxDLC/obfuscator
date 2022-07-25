package me.superblaubeere27.jobf.processors;

import me.superblaubeere27.annotations.ObfuscationTransformer;
import me.superblaubeere27.jobf.IClassTransformer;
import me.superblaubeere27.jobf.IPreClassTransformer;
import me.superblaubeere27.jobf.JObfImpl;
import me.superblaubeere27.jobf.ProcessorCallback;
import me.superblaubeere27.jobf.processors.name.ClassWrapper;
import me.superblaubeere27.jobf.utils.values.BooleanValue;
import me.superblaubeere27.jobf.utils.values.DeprecationLevel;
import me.superblaubeere27.jobf.utils.values.EnabledValue;
import me.superblaubeere27.jobf.utils.values.NumberValue;
import org.checkerframework.checker.units.qual.C;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.openjdk.nashorn.internal.ir.ReturnNode;

import java.util.*;
import java.util.stream.Collectors;

public class LibWrapper implements IPreClassTransformer {

    public static final String PROCESSOR_NAME = "LibWrapper";
    private EnabledValue enabled = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.GOOD, true);
    public NumberValue<Integer> numberOfProxyPools = new NumberValue<>(PROCESSOR_NAME, "ProxyPools", DeprecationLevel.GOOD, 3);
    public BooleanValue booleanValue = new BooleanValue(PROCESSOR_NAME, "BooleanValue", DeprecationLevel.GOOD, true);

    private HashSet<String> methodNameBlackList = new HashSet<>();

    private JObfImpl inst;
    public HashMap<String, MethodNodeRecord> methodNodeHashMap = new HashMap<>();
    public HashMap<String, MethodNodeRecord> staticMethodNodeHashMap = new HashMap<>();

    public HashMap<String, MethodNodeRecord> specialNodeHashMap = new HashMap<>();

    //Map of all classes to which new methods to wrap library methods will be added
    public HashMap<String, ClassNode> proxyClasses = new HashMap<>();

    int classToAddMethodsIndex = 0;

    public LibWrapper(JObfImpl inst) {
        this.inst = inst;
    }


    @Override
    public void process(Collection<ClassNode> node) {

        proxyClasses.clear();
        methodNodeHashMap.clear();
        staticMethodNodeHashMap.clear();

        if (!enabled.getObject()) return;
        ArrayList<ClassNode> nodes = node.stream().filter(n -> !n.name.contains("$")).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(nodes);
        //Set up proxy classes
        for (int i = 0; i < numberOfProxyPools.getObject(); i++) {
            System.out.println("Adding: " + nodes.get(i).name + " to ProxyPool");
            proxyClasses.put(nodes.get(i).name, nodes.get(i));
        }


        //Create a Map of all ClassNodes for easier handling
        HashMap<String, ClassNode> nodeMap = new HashMap<>();
        node.forEach(n -> nodeMap.put(n.name, n));

        //Find all method Calls for a class and add redirect them to our proxy class if the owner of the method is not from our code
        node.forEach(classNode ->

        {
            classNode.methods.forEach(methodNode -> {
                methodNode.instructions.forEach(abstractInsnNode -> {
                    if (abstractInsnNode instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
                        if (!nodeMap.containsKey(methodInsnNode.owner)) {
                            switch (methodInsnNode.getOpcode()) {
                                case Opcodes.INVOKEVIRTUAL:
                                    if (!methodInsnNode.owner.contains("[") && !methodNameBlackList.contains(methodInsnNode.name)) {
                                        ClassNode proxyClass = getRandomProxyClass();
                                        if (!methodNodeHashMap.containsKey(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc)) {
                                            methodNodeHashMap.put(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc, new MethodNodeRecord(methodInsnNode.owner, proxyClass.name, methodInsnNode));
                                            methodNode.instructions.insertBefore(methodInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, proxyClass.name, methodInsnNode.name, methodInsnNode.desc.replace("(", "(L" + methodInsnNode.owner + ";")));
                                        } else {
                                            MethodNodeRecord oldMethod = methodNodeHashMap.get(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc);
                                            methodNode.instructions.insertBefore(methodInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, oldMethod.newOwner, oldMethod.methodInsnNode.name, oldMethod.methodInsnNode.desc.replace("(", "(L" + oldMethod.methodInsnNode.owner + ";")));

                                        }
                                        methodNode.instructions.remove(methodInsnNode);
                                    }
                                    break;
                                case Opcodes.INVOKESTATIC:
                                    if (!methodInsnNode.owner.contains("[") && !methodNameBlackList.contains(methodInsnNode.name)) {
                                        ClassNode proxyClass = getRandomProxyClass();
                                        if (!staticMethodNodeHashMap.containsKey(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc)) {
                                            staticMethodNodeHashMap.put(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc, new MethodNodeRecord(methodInsnNode.owner, proxyClass.name, methodInsnNode));
                                            methodNode.instructions.insertBefore(methodInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, proxyClass.name, methodInsnNode.name, methodInsnNode.desc));
                                        } else {
                                            MethodNodeRecord oldMethod = staticMethodNodeHashMap.get(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc);
                                            methodNode.instructions.insertBefore(methodInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, oldMethod.newOwner, oldMethod.methodInsnNode.name, oldMethod.methodInsnNode.desc));
                                        }
                                        methodNode.instructions.remove(methodInsnNode);
                                    }
                                    break;
                                case Opcodes.INVOKESPECIAL:
                                    //TODO Remove NEW and DUP instructions
                                    if (!methodInsnNode.owner.contains("[") && !methodNameBlackList.contains(methodInsnNode.name)) {
                                        ClassNode proxyClass = getRandomProxyClass();
                                        if (!specialNodeHashMap.containsKey(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc)) {
                                            specialNodeHashMap.put(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc,
                                                    new MethodNodeRecord(methodInsnNode.owner,
                                                            proxyClass.name,
                                                            new MethodInsnNode(methodInsnNode.getOpcode(),
                                                                    methodInsnNode.owner,
                                                                    "init" + methodInsnNode.owner.replaceAll("/", ""),
                                                                    methodInsnNode.desc.replaceAll("\\)V", ")L" + methodInsnNode.owner + ";"))));
                                            methodNode.instructions.insertBefore(methodInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, proxyClass.name, "init" + methodInsnNode.owner.replaceAll("/", ""), methodInsnNode.desc.replaceAll("\\)V", ")L" + methodInsnNode.owner + ";")));
                                        } else {
                                            MethodNodeRecord oldMethod = specialNodeHashMap.get(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc);
                                            methodNode.instructions.insertBefore(methodInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, oldMethod.newOwner, oldMethod.methodInsnNode.name, oldMethod.methodInsnNode.desc));
                                        }
                                        methodNode.instructions.remove(methodInsnNode);
                                        System.out.println("InvokeSpecial");
                                    }
                            }
                        }
                    }
                });
            });
        });
        ///home/tim/Desktop/ByteCodeTest-1.0-SNAPSHOT.jar
        //home/tim/Desktop/Go-Game.jar
        methodNodeHashMap.values().
                forEach(n ->
                {
                    MethodNode methodNode = new MethodNode(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, n.methodInsnNode.name, getUpdatedDescriptor(n.methodInsnNode.desc, n.oldOwner), null, new String[0]);
                    proxyClasses.get(n.newOwner).methods.add(methodNode);


                    Type methodType = Type.getMethodType(getUpdatedDescriptor(n.methodInsnNode.desc, n.oldOwner));

                    //Load this pointer
                    //VOID, BOOLEAN, CHAR, BYTE, SHORT, INT, FLOAT, LONG, DOUBLE, ARRAY, OBJECT or METHOD.
                    int i = 0;
                    for (Type type : methodType.getArgumentTypes()) {
                        switch (type.getSort()) {
                            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT ->
                                    methodNode.instructions.add(new VarInsnNode(Opcodes.ILOAD, i));
                            case Type.FLOAT -> methodNode.instructions.add(new VarInsnNode(Opcodes.FLOAD, i));
                            case Type.LONG -> methodNode.instructions.add(new VarInsnNode(Opcodes.LLOAD, i));
                            case Type.DOUBLE -> methodNode.instructions.add(new VarInsnNode(Opcodes.DLOAD, i));
                            case Type.ARRAY, Type.OBJECT ->
                                    methodNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, i));
                        }
                        i++;
                    }
                    methodNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, n.oldOwner, n.methodInsnNode.name, n.methodInsnNode.desc));
                    switch (methodType.getReturnType().getSort()) {
                        case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT ->
                                methodNode.instructions.add(new InsnNode(Opcodes.IRETURN));
                        case Type.FLOAT -> methodNode.instructions.add(new InsnNode(Opcodes.FRETURN));
                        case Type.LONG -> methodNode.instructions.add(new InsnNode(Opcodes.LRETURN));
                        case Type.DOUBLE -> methodNode.instructions.add(new InsnNode(Opcodes.DRETURN));
                        case Type.ARRAY, Type.OBJECT -> methodNode.instructions.add(new InsnNode(Opcodes.ARETURN));
                        case Type.VOID -> methodNode.instructions.add(new InsnNode(Opcodes.RETURN));
                    }
                });

        staticMethodNodeHashMap.values().forEach(n -> {
            MethodNode methodNode = new MethodNode(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, n.methodInsnNode.name, n.methodInsnNode.desc, null, new String[0]);
            proxyClasses.get(n.newOwner).methods.add(methodNode);

            Type methodType = Type.getMethodType(n.methodInsnNode.desc);

            int i = 0;
            for (Type type : methodType.getArgumentTypes()) {
                switch (type.getSort()) {
                    case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT ->
                            methodNode.instructions.add(new VarInsnNode(Opcodes.ILOAD, i));
                    case Type.FLOAT -> methodNode.instructions.add(new VarInsnNode(Opcodes.FLOAD, i));
                    case Type.LONG -> methodNode.instructions.add(new VarInsnNode(Opcodes.LLOAD, i));
                    case Type.DOUBLE -> methodNode.instructions.add(new VarInsnNode(Opcodes.DLOAD, i));
                    case Type.ARRAY, Type.OBJECT -> methodNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, i));
                }
                i++;
            }
            methodNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, n.oldOwner, n.methodInsnNode.name, n.methodInsnNode.desc));
            switch (methodType.getReturnType().getSort()) {
                case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT ->
                        methodNode.instructions.add(new InsnNode(Opcodes.IRETURN));
                case Type.FLOAT -> methodNode.instructions.add(new InsnNode(Opcodes.FRETURN));
                case Type.LONG -> methodNode.instructions.add(new InsnNode(Opcodes.LRETURN));
                case Type.DOUBLE -> methodNode.instructions.add(new InsnNode(Opcodes.DRETURN));
                case Type.ARRAY, Type.OBJECT -> methodNode.instructions.add(new InsnNode(Opcodes.ARETURN));
                case Type.VOID -> methodNode.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        });
    }


    private static String getUpdatedDescriptor(String desc, String oldOwner) {
        String newDesc = "";
        //Array?
        if (oldOwner.startsWith("[")) {
            newDesc = desc.replace("(", "(" + oldOwner);
        } else {
            newDesc = desc.replace("(", "(L" + oldOwner + ";");
        }
        return newDesc;
    }


    private ClassNode getRandomProxyClass() {
        List<ClassNode> nodes = new ArrayList<>(proxyClasses.values().stream().toList());
        Collections.shuffle(nodes);
        return nodes.get(0);
    }


    private static record MethodNodeRecord(String oldOwner, String newOwner, MethodInsnNode methodInsnNode) {

    }

}
