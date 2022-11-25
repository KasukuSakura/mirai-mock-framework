package com.kasukusakura.miraimockconsole.wrapper;

import net.mamoe.mirai.console.extension.PluginComponentStorage;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginClasspath;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class WrapperLoaderPlugin extends JavaPlugin {
    private static Class<?> bootstrap;

    public WrapperLoaderPlugin() throws Throwable {
        super(JvmPluginDescription.loadFromResource("plugin.yml", WrapperLoaderPlugin.class.getClassLoader()));

        Properties metadata = new Properties();
        try (InputStream data = WrapperLoaderPlugin.class.getResourceAsStream("/metadata.properties")) {
            metadata.load(data);
        }
        File projLoc = new File(metadata.getProperty("projdir"));

        File realJarFile = new File(projLoc, "plugin/build/mirai/mirai-mock-framework-console-1.0.0.mirai2.jar");
        if (!realJarFile.exists()) {
            throw new AssertionError("Real jar not found: " + realJarFile);
        }

        Map<String, Range> dataregions = new HashMap<>();
        Object initialBuffer;

        MethodHandle ByteBuf$writerIndex, ByteBuf$writeBytes, ByteBuf$slice, ByteBufInputStream$new, ByteBuf$getBytes;

        try (ZipFile theJarFile = new ZipFile(realJarFile)) {
            ZipEntry dependenciesEntry = theJarFile.getEntry("META-INF/mirai-console-plugin/dependencies-private.txt");
            if (dependenciesEntry == null) throw new AssertionError("dependencies info not found");

            List<String> dependencies = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(theJarFile.getInputStream(dependenciesEntry)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    dependencies.add(line);
                }
            }
            getJvmPluginClasspath().downloadAndAddToPath(
                    getJvmPluginClasspath().getPluginSharedLibrariesClassLoader(), dependencies
            );

            Class<?> ByteBuf = Class.forName("io.netty.buffer.ByteBuf");
            Class<?> Unpooled = Class.forName("io.netty.buffer.Unpooled");
            Class<?> ByteBufInputStream = Class.forName("io.netty.buffer.ByteBufInputStream");

            MethodHandles.Lookup lookup = MethodHandles.lookup();

            initialBuffer = lookup.findStatic(
                    Unpooled, "directBuffer", MethodType.methodType(ByteBuf)
            ).invoke();


            ByteBuf$writerIndex = lookup.findVirtual(
                    ByteBuf, "writerIndex", MethodType.methodType(int.class)
            );

            // public abstract ByteBuf writeBytes(byte[] src, int srcIndex, int length);
            ByteBuf$writeBytes = lookup.findVirtual(
                    ByteBuf, "writeBytes", MethodType.methodType(ByteBuf, byte[].class, int.class, int.class)
            );

            // public abstract ByteBuf slice(int index, int length);
            ByteBuf$slice = lookup.findVirtual(
                    ByteBuf, "slice", MethodType.methodType(ByteBuf, int.class, int.class)
            );

            // public abstract ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);
            ByteBuf$getBytes = lookup.findVirtual(
                    ByteBuf, "getBytes", MethodType.methodType(ByteBuf, int.class, byte[].class, int.class, int.class)
            );

            ByteBufInputStream$new = lookup.findConstructor(
                    ByteBufInputStream, MethodType.methodType(void.class, ByteBuf)
            );

            int lastWriterIndex = 0;

            Enumeration<? extends ZipEntry> entries = theJarFile.entries();
            ZipEntry lastEntry = null;
            byte[] bufferx = new byte[2048];
            while (entries.hasMoreElements()) {
                ZipEntry nextEntry = entries.nextElement();
                if (nextEntry.isDirectory()) continue;
                if (lastEntry != null) {
                    int crtWriterIdx = lastWriterIndex;
                    lastWriterIndex = (int) ByteBuf$writerIndex.invoke(initialBuffer);

                    dataregions.put(lastEntry.getName(), new Range(crtWriterIdx, lastWriterIndex));
                }

                lastEntry = nextEntry;
                try (InputStream dataReader = theJarFile.getInputStream(nextEntry)) {
                    while (true) {
                        int len = dataReader.read(bufferx);
                        if (len == -1) break;

                        ByteBuf$writeBytes.invoke(initialBuffer, bufferx, 0, len);
                    }
                }
            }
            if (lastEntry != null) {
                dataregions.put(lastEntry.getName(), new Range(lastWriterIndex, (int) ByteBuf$writerIndex.invoke(initialBuffer)));
            }

        }

//        dataregions.forEach((k, v) -> System.out.println(k + " - " + v));

        ClassLoader delegateClassLoader = new RealClassLoader(
                dataregions, initialBuffer, ByteBuf$slice, ByteBuf$getBytes,
                ByteBufInputStream$new, getJvmPluginClasspath()
        );

        bootstrap = Class.forName("com.kasukusakura.miraimockconsole.InitializeBoot10086", false, delegateClassLoader);
    }

    @Override
    public void onLoad(@NotNull PluginComponentStorage $this$onLoad) {
        try {
            MethodHandles.lookup().findStatic(
                    bootstrap, "load0",
                    MethodType.methodType(void.class,
                            PluginComponentStorage.class,
                            JvmPluginClasspath.class,
                            JvmPlugin.class
                    )
            ).invoke($this$onLoad, getJvmPluginClasspath(), this);
        } catch (Throwable e) {
            thrown(e);
        }
    }

    @Override
    public void onEnable() {
        try {
            MethodHandles.lookup().findStatic(
                    bootstrap, "doEnable",
                    MethodType.methodType(void.class,
                            JvmPlugin.class
                    )
            ).invoke(this);
        } catch (Throwable e) {
            thrown(e);
        }
    }

    static class Range {
        final int s;
        final int e;
        final int len;

        Range(int s, int e) {
            this.s = s;
            this.e = e;
            this.len = e - s;
        }

        @Override
        public String toString() {
            return s + " -> " + e + " | " + len;
        }
    }


    private static class RealClassLoader extends ClassLoader {
        private final Map<String, Range> dataregions;
        private final Object buffer;
        private final MethodHandle byteBuf$slice;
        private final MethodHandle byteBuf$getBytes;
        private final MethodHandle byteBufInputStream$new;

        RealClassLoader(
                Map<String, Range> dataregions,
                Object buffer,
                MethodHandle ByteBuf$slice,
                MethodHandle ByteBuf$getBytes,
                MethodHandle ByteBufInputStream$new,
                JvmPluginClasspath classpath
        ) {
            super(classpath.getPluginClassLoader());
            this.dataregions = dataregions;
            this.buffer = buffer;
            byteBuf$slice = ByteBuf$slice;
            byteBuf$getBytes = ByteBuf$getBytes;
            byteBufInputStream$new = ByteBufInputStream$new;
        }

        static {
            ClassLoader.registerAsParallelCapable();
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resPath = name.replace('.', '/') + ".class";
            Range range = dataregions.get(resPath);
            if (range == null) throw new ClassNotFoundException(name);

            try {
                byte[] data = new byte[range.len];
                byteBuf$getBytes.invoke(buffer, range.s, data, 0, range.len);

                return defineClass(name, data, 0, data.length);
            } catch (Throwable throwable) {
                return thrown(throwable);
            }
        }

        @Nullable
        @Override
        public InputStream getResourceAsStream(String name) {
            String resPath = name.replace('.', '/') + ".class";
            Range range = dataregions.get(resPath);

            if (range == null) return super.getResourceAsStream(name);

            try {
                Object srange = byteBuf$slice.invoke(buffer, range.s, range.len);
                return (InputStream) byteBufInputStream$new.invoke(srange);
            } catch (Throwable throwable) {
                return thrown(throwable);
            }
        }
    }

    private static <T extends Throwable> void sthrow(Throwable argx) throws T {
        //noinspection unchecked
        throw (T) argx;
    }

    public static <T> T thrown(Throwable error) {
        sthrow(error);
        throw new RuntimeException(error);
    }

}
