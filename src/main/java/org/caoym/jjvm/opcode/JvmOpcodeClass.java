package org.caoym.jjvm.opcode;

import com.sun.tools.classfile.*;
import org.caoym.jjvm.lang.JvmClass;
import org.caoym.jjvm.lang.JvmField;
import org.caoym.jjvm.lang.JvmMethod;
import org.caoym.jjvm.runtime.Env;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 字节码定义的 Java 类( 区别于 native 类 )
 */
public class JvmOpcodeClass implements JvmClass {

    private final ClassFile classFile;
    private Map<Map.Entry<String, String>, JvmOpcodeMethod> methods = new HashMap<>();
    private Map<String, JvmField> fields = new HashMap<>();
    /**
     *  是否已经初始化
     */
    boolean inited = false;

    static public JvmOpcodeClass read(Path path) throws ClassNotFoundException {
        try {
            return  new JvmOpcodeClass(ClassFile.read(path));
        } catch (IOException e) {
            throw new ClassNotFoundException(e.toString());
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }
    /**
     *
     * @param classFile
     * @throws ConstantPoolException
     */
    private JvmOpcodeClass(ClassFile classFile) throws ConstantPoolException, Descriptor.InvalidDescriptor {
        this.classFile = classFile;
        for (Method method : classFile.methods) {
            String name = method.getName(classFile.constant_pool);
            String desc = method.descriptor.getValue(classFile.constant_pool);
            methods.put(new AbstractMap.SimpleEntry<>(name, desc), new JvmOpcodeMethod(this, method));
        }
        //准备阶段
        prepare();
    }

    /**
     * 准备阶段（Preparation）
     * 分配静态变量，并初始化为默认值，但不会执行任何字节码，在初始化阶段（init) 会有显式的初始化器来初始化这些静态字段，所以准备阶段不做
     * 这些事情。
     * @see `http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-5.html#jvms-5.4.2`
     */
    private void prepare() throws ConstantPoolException, Descriptor.InvalidDescriptor {
        for(Field i : this.classFile.fields){
            if(i.access_flags.is(AccessFlags.ACC_STATIC)){
                fields.put(i.getName(classFile.constant_pool), new JvmOpcodeStaticField(this, i));
            }else{
                fields.put(i.getName(classFile.constant_pool), new JvmOpcodeObjectField(this, i));
            }
        }
    }

    /**
     * 初始化阶段（Initialization）
     * 调用类的<clinit>方法（如果有）
     * @see `http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-5.html#jvms-5.5`
     */
    public void clinit(Env env) throws Exception {
        if(inited){
            return;
        }
        synchronized(this){
            if(inited){
                return;
            }
            inited = true;
            JvmOpcodeMethod method = methods.get(new AbstractMap.SimpleEntry<>("<clinit>", "()V"));
            if(method != null){
                method.call(env, null);
            }
        }
    }

    @Override
    public Object newInstance(Env env) throws InstantiationException, IllegalAccessException {
        try {
            clinit(env);
        } catch (Exception e) {
            throw new InstantiationException(e.getMessage());
        }
        return new JvmOpcodeObject(this);
    }

    @Override
    public JvmMethod getMethod(String name, String desc) throws NoSuchMethodException {
        JvmOpcodeMethod method = methods.get(new AbstractMap.SimpleEntry<>(name, desc));
        if(method == null){
            throw new NoSuchMethodException("method "+name+":"+ desc+" not exist");
        }
        return method;
    }

    @Override
    public JvmField getField(String name) throws NoSuchFieldException, IllegalAccessException {
        return null;
    }

    public ClassFile getClassFile() {
        return classFile;
    }
}
