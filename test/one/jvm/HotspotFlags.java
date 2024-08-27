package one.jvm;

import sun.misc.Unsafe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class HotspotFlags {

    private static Unsafe unsafe = getUnsafe();
    private ElfSymbolTable symtab;
    private long baseAddress;

    private final Map<String, Boolean> booleanFlag = new HashMap<>();
    private final Map<String, Integer> intFlag = new HashMap<>();


    public HotspotFlags() throws IOException {
        String osType = checkEnvironment();

        if (osType.equals("win")) {
            String jvmLibrary = "C:\\Users\\kulis\\.jdks\\temurin-11.0.22\\bin\\server\\jvm.dll";
            System.out.println("using JVM DLL: " + jvmLibrary);

        } else if (osType.equals("unix")) {
            String maps = findJvmMaps();
            String[] lines = maps.split("\n");
            for (String line : lines) {
                String addressRange = line.substring(0, line.indexOf('-')).trim();
                if (!addressRange.matches("^[0-9a-fA-F]+$")) {
                    throw new IllegalArgumentException("Invalid address range: " + addressRange);
                }
                long jvmAddress = Long.parseLong(addressRange, 16);

                String[] parts = line.split(" ");
                String jvmLibrary = parts[parts.length - 1];

                System.out.println("Path to JVM library: " + jvmLibrary);

                File file = new File(jvmLibrary);
                if (!file.exists()) {
                    throw new FileNotFoundException("JVM library not found: " + jvmLibrary);
                }

                try {
                    ElfReader elfReader = new ElfReader(jvmLibrary);
                    ElfSection symtab = elfReader.section(".symtab");
                    if (!(symtab instanceof ElfSymbolTable)) {
                        throw new IOException(".symtab section not found");
                    }

                    this.symtab = (ElfSymbolTable) symtab;
                    this.baseAddress = elfReader.elf64() ? jvmAddress : 0;
                    break;

                } catch (IOException e) {
                    System.err.println("Failed to initialize ElfReader for: " + jvmLibrary);
                    e.printStackTrace();
                    throw e;
                }
            }

            if (this.symtab == null) {
                throw new IOException("Failed to initialize symbol table from any provided library");
            }
        }
    }


    public static void main(String[] args) throws Exception {
        HotspotFlags flags = new HotspotFlags();

        boolean prevUseBiasedLocking = flags.getBooleanFlag("UseBiasedLocking");
        flags.setBooleanFlag("UseBiasedLocking", false);

        System.out.println("hashCode algorithm = " + flags.getIntFlag("hashCode"));
        for (int i = 0; i < 5; i++) {
            System.out.println(testHashCode(flags));
        }

        flags.setIntFlag("hashCode", 5);

        System.out.println("hashCode algorithm = " + flags.getIntFlag("hashCode"));
        for (int i = 0; i < 5; i++) {
            System.out.println(testHashCode(flags));
        }

        flags.setBooleanFlag("UseBiasedLocking", prevUseBiasedLocking);

        System.out.println("Changing TraceClassLoading policy...");
        flags.setBooleanFlag("TraceClassLoading", true);

        Class.forName("java.net.ServerSocket");


    }

    public static Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("unable to access Unsafe", e);
        }
    }

    public static String checkEnvironment() {
        String osName = System.getProperty("os.name").toLowerCase();
        String jvmName = System.getProperty("java.vm.name").toLowerCase();
        System.out.println("os.name: " + osName + "\njava.vm.name : " + jvmName);

        if (osName.contains("win")) {
            return "windows";
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("mac")) {
            return "unix";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + osName);
        }
    }


    private static String findJvmMaps() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("test/one/jvm/config.txt"))) {
            StringBuilder jvmFlags = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("FLAG")) {
                    jvmFlags.append(line).append("\n");
                }
            }
            return jvmFlags.toString();
        }
    }

    private static long testHashCode(Object obj) {
        long startTime = System.nanoTime();
        int hashCode = obj.hashCode();
        long endTime = System.nanoTime();
        System.out.println("HashCode: " + hashCode);
        return endTime - startTime;
    }

    public String findSymbol(String symbolName) {
        if (symtab == null) {
            throw new IllegalStateException("symbol table is not initialized");
        }

        ElfSymbol symbol = symtab.symbol(symbolName);
        if (symbol != null) {
            return symbol.name();
        } else {
            return null;
        }
    }

    public int getIntFlag(String flagName) {
        return intFlag.getOrDefault(flagName, 0);
    }

    public void setIntFlag(String flagName, int value) {
        intFlag.put(flagName, value);
    }

    public void setBooleanFlag(String flagName, boolean value) {
        booleanFlag.put(flagName, value);
    }

    public boolean getBooleanFlag(String flagName) {
        return booleanFlag.getOrDefault(flagName, false);
    }
}
