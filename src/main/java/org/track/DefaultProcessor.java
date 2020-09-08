package org.track;

import com.squareup.javawriter.JavaWriter;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.*;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class DefaultProcessor extends AbstractProcessor {

    /**
     * print logs
     */
    private Messager messager;

    /**
     * process elements
     */
    private Elements elementsUtil;

    /**
     * compiler and generate class
     */
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        elementsUtil = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> set = new HashSet<>();
        set.add(GetterAndSetter.class.getCanonicalName());
        return Collections.unmodifiableSet(set);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(GetterAndSetter.class);
        Element element = null;
        boolean isClass = false;
        String classQualifiedName = null;
        String packageName = null;
        for (Element e : elements) {
            if (ElementKind.CLASS == e.getKind() && e instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) e;
                isClass = true;
                classQualifiedName = ((TypeElement) e).getQualifiedName().toString();
                element = typeElement;

                packageName = elementsUtil.getPackageOf(e).getQualifiedName().toString();
                break;
            }
        }
        List<? extends Element> enclosedElements = element.getEnclosedElements();
        Map<TypeMirror, Name> fileds = new HashMap<>();
        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.getKind() == ElementKind.FIELD) {
                TypeMirror filedType = enclosedElement.asType();
                Name filedName = enclosedElement.getSimpleName();
                fileds.put(filedType, filedName);
            }
        }

        try {
            JavaFileObject sourceFile = filer.createSourceFile(getClassName(classQualifiedName));
            generateSourceFile(classQualifiedName,packageName,fileds,sourceFile.openWriter());
            compile(sourceFile.toUri().getPath());
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,e.getMessage());
        }
        messager.printMessage(Diagnostic.Kind.NOTE,packageName);
        messager.printMessage(Diagnostic.Kind.NOTE,classQualifiedName);
        return true;
    }

    private void compile(String path) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();;
        StandardJavaFileManager javaFileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> fileObjects = javaFileManager.getJavaFileObjects(path);
        JavaCompiler.CompilationTask task = compiler.getTask(null, javaFileManager, null, null, null, fileObjects);
        task.call();
        javaFileManager.close();
    }

    private void generateSourceFile(String className,String packageName, Map<TypeMirror,Name> fileds, Writer writer) throws IOException {
        JavaWriter jw = new JavaWriter(writer);
        jw.emitPackage(packageName);
        jw.beginMethod(packageName,"class",EnumSet.of(Modifier.PUBLIC));
        for (Map.Entry<TypeMirror,Name> map : fileds.entrySet()) {
            String type = map.getKey().toString();
            String name = map.getValue().toString();
            jw.emitField(type,name,EnumSet.of(Modifier.PRIVATE));
        }
        for (Map.Entry<TypeMirror, Name> map : fileds.entrySet()) {
            String type = map.getKey().toString();
            String name = map.getValue().toString();
            jw.beginMethod(type, "get" + humpString(name), EnumSet.of(Modifier.PUBLIC))
                    .emitStatement("return " + name)
                    .endMethod();
            jw.beginMethod("void", "set" + humpString(name), EnumSet.of(Modifier.PUBLIC), type, "arg")
                    .emitStatement("this." + name + " = arg")
                    .endMethod();
        }
        jw.endType().close();
    }


    private String getClassName(String name) {
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf(".") + 1);
        }
        return name;
    }

    private String humpString(String name) {
        String result = name;
        if (name.length() == 1) {
            result = name.toUpperCase();
        }
        if (name.length() > 1) {
            result = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return result;
    }

}
