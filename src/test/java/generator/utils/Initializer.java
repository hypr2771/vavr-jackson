package generator.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.javapoet.*;
import io.vavr.Lazy;
import io.vavr.Tuple;
import io.vavr.Value;
import io.vavr.collection.*;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.jackson.datatype.VavrModule;

import javax.lang.model.element.Modifier;

/**
 * @author <a href="mailto:ruslan.sennov@gmail.com">Ruslan Sennov</a>
 */
public class Initializer {

    public static void initMapper(TypeSpec.Builder builder, String name) {
        initMapper(builder, name, null);
    }

    public static void initMapper(TypeSpec.Builder builder, String name, VavrModule.Settings settings) {
        Modifier[] mods = new Modifier[] { Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL };
        if (settings != null) {
            builder.addField(FieldSpec.builder(ClassName.get(VavrModule.Settings.class), name + "_SETTINGS", mods)
                    .initializer("new $T()\n        .useOptionInPlainFormat($L).deserializeNullAsEmptyCollection($L)",
                            ClassName.get(VavrModule.Settings.class),
                            settings.useOptionInPlainFormat(), settings.deserializeNullAsEmptyCollection())
                    .build());
            builder.addField(FieldSpec.builder(ClassName.get(VavrModule.class), name + "_MODULE", mods)
                    .initializer("new $T($L)", ClassName.get(VavrModule.class), name + "_SETTINGS")
                    .build());
        } else {
            builder.addField(FieldSpec.builder(ClassName.get(VavrModule.class), name + "_MODULE", mods)
                    .initializer("new $T()", ClassName.get(VavrModule.class))
                    .build());
        }
        builder.addField(FieldSpec.builder(ClassName.get(ObjectMapper.class), name, mods)
                .initializer("new $T().registerModule($L)", ClassName.get(ObjectMapper.class), name + "_MODULE")
                .build());
    }

    public static TypeName initValue(MethodSpec.Builder builder, String name, Object obj) {
        if (obj instanceof Either) {
            Either<?, ?> either = (Either<?, ?>) obj;
            ParameterizedTypeName ptn;
            if (either.isLeft()) {
                TypeName subType = initValue(builder, name + "l", either.left().get());
                ptn = ParameterizedTypeName.get(ClassName.get(Either.class), subType, ClassName.get(Object.class));
                builder.addStatement("$T $L = $T.left($L)", ptn, name, ClassName.get(Either.class), name + "l");
            } else {
                TypeName subType = initValue(builder, name + "r", either.right().get());
                ptn = ParameterizedTypeName.get(ClassName.get(Either.class), ClassName.get(Object.class), subType);
                builder.addStatement("$T $L = $T.right($L)", ptn, name, ClassName.get(Either.class), name + "r");
            }
            return ptn;
        }
        if (obj instanceof Option) {
            TypeName subType = initValue(builder, name + "0", ((Option<?>) obj).get());
            ParameterizedTypeName ptn = ParameterizedTypeName.get(ClassName.get(Option.class), subType);
            builder.addStatement("$T $L = $T.of($L)", ptn, name, ClassName.get(Option.class), name + "0");
            return ptn;
        }
        if (obj instanceof Lazy) {
            TypeName subType = initValue(builder, name + "0", ((Lazy<?>) obj).get());
            ParameterizedTypeName ptn = ParameterizedTypeName.get(ClassName.get(Lazy.class), subType);
            builder.addStatement("$T $L = $T.of(() -> $L)", ptn, name, ClassName.get(Lazy.class), name + "0");
            return ptn;
        }
        if (obj instanceof Seq) {
            Seq<?> seq = (Seq<?>) obj;
            TypeName[] subTypes = initValues(builder, name, seq.toJavaArray());
            ParameterizedTypeName ptn = ParameterizedTypeName.get(clsName(seq), commonTypeName(subTypes));
            String args = List.range(0, subTypes.length).map(i -> name + i).mkString(", ");
            builder.addStatement("$T $L = $T.of($L)", ptn, name, clsName(seq), args);
            return ptn;
        }
        if (obj instanceof Set) {
            Set<?> seq = (Set<?>) obj;
            TypeName[] subTypes = initValues(builder, name, seq.toJavaArray());
            ParameterizedTypeName ptn = ParameterizedTypeName.get(clsName(seq), commonTypeName(subTypes));
            String args = List.range(0, subTypes.length).map(i -> name + i).mkString(", ");
            builder.addStatement("$T $L = $T.of($L)", ptn, name, clsName(seq), args);
            return ptn;
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            TypeName[] subTypes = initValues(builder, name, map.toJavaArray());
            ParameterizedTypeName ctn = (ParameterizedTypeName) commonTypeName(subTypes);
            ParameterizedTypeName ptn = ParameterizedTypeName.get(clsName(map), ctn.typeArguments.get(0), ctn.typeArguments.get(1));
            String args = List.range(0, subTypes.length).map(i -> name + i).mkString(", ");
            builder.addStatement("$T $L = $T.ofEntries($L)", ptn, name, clsName(map), args);
            return ptn;
        }
        if (obj instanceof Multimap) {
            Multimap<?, ?> multimap = (Multimap<?, ?>) obj;
            String withContainerType;
            switch (multimap.getContainerType()) {
                case SEQ:
                    withContainerType = "withSeq";
                    break;
                case SET:
                    withContainerType = "withSet";
                    break;
                case SORTED_SET:
                    withContainerType = "withSortedSet";
                    break;
                default:
                    throw new RuntimeException();
            }
            TypeName[] subTypes = initValues(builder, name, multimap.toJavaArray());
            ParameterizedTypeName ctn = (ParameterizedTypeName) commonTypeName(subTypes);
            ParameterizedTypeName ptn = ParameterizedTypeName.get(clsName(multimap), ctn.typeArguments.get(0), ctn.typeArguments.get(1));
            String args = List.range(0, subTypes.length).map(i -> name + i).mkString(", ");
            builder.addStatement("$T $L = $T.$L().ofEntries($L)", ptn, name, clsName(multimap), withContainerType, args);
            return ptn;
        }
        if (obj instanceof Tuple) {
            TypeName[] subTypes = initValues(builder, name, ((Tuple) obj).toSeq().toJavaArray());
            ParameterizedTypeName ptn = ParameterizedTypeName.get(ClassName.get(obj.getClass()), subTypes);
            String args = List.range(0, subTypes.length).map(i -> name + i).mkString(", ");
            builder.addStatement("$T $L = $T.of($L)", ptn, name, ClassName.get(Tuple.class), args);
            return ptn;
        }
        ClassName className = ClassName.get(obj.getClass());
        builder.addStatement("$T $L = " + (obj instanceof String ? "$S" : "$L"), className, name, obj);
        return className;
    }

    private static TypeName[] initValues(MethodSpec.Builder builder, String name, Object... objs) {
        TypeName[] types = new TypeName[objs.length];
        for (int i = 0; i < objs.length; i++) {
            types[i] = initValue(builder, name + i, objs[i]);
        }
        return types;
    }

    private static TypeName commonTypeName(TypeName[] subTypes) {
        return subTypes[0];
    }

    private static ClassName clsName(Value<?> value) {
        if (value instanceof List) {
            return ClassName.get(List.class);
        }
        return ClassName.get(value.getClass());
    }
}
