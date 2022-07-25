/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27.jobf.processors;

import me.superblaubeere27.annotations.ObfuscationTransformer;
import me.superblaubeere27.jobf.IClassTransformer;
import me.superblaubeere27.jobf.JObfImpl;
import me.superblaubeere27.jobf.ProcessorCallback;
import me.superblaubeere27.jobf.utils.NameUtils;
import me.superblaubeere27.jobf.utils.values.BooleanValue;
import me.superblaubeere27.jobf.utils.values.DeprecationLevel;
import me.superblaubeere27.jobf.utils.values.EnabledValue;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;

public class LocalVariableReplacer implements IClassTransformer {


    private EnabledValue enabled = new EnabledValue("LocalVariableReplacer", DeprecationLevel.GOOD, true);
    private JObfImpl inst;

    public LocalVariableReplacer(JObfImpl inst) {
        this.inst = inst;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node) {
        if(!enabled.getObject())return;
      // node.methods.forEach(methodNode -> methodNode.localVariables.forEach(n -> n.name="\uD834\uDD78"));
        node.methods.forEach(methodNode -> methodNode.localVariables.forEach(n ->{
            if(n.name.equals("this")){
                n.name = "_";
            }else{
                n.name = "\uD834\uDD78\n";
            }
        }));
        inst.setWorkDone();
    }

    @Override
    public ObfuscationTransformer getType() {
        return ObfuscationTransformer.LOCAL_VARIABLE_REPLACER;
    }


}