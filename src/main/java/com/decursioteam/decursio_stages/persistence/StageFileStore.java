package com.decursioteam.decursio_stages.persistence;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;

/** Keeps the legacy uncompressed NBT format and UUID.dat path. Replacements are always atomic. */
public final class StageFileStore {
    private StageFileStore() {}
    public record Loaded(CompoundTag tag, boolean backup) {}
    public static Path backup(Path file) { return file.resolveSibling(file.getFileName() + ".bak"); }

    public static void validate(CompoundTag tag) throws IOException {
        if (!(tag.get("Stages") instanceof ListTag list)) throw new IOException("Missing or invalid Stages string list");
        for (Tag entry : list) if (!(entry instanceof StringTag)) throw new IOException("Stages contains a non-string entry");
    }

    public static CompoundTag read(Path file) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
            input.mark(2);
            int first = input.read(), second = input.read();
            input.reset();
            CompoundTag tag;
            if (first == 0x1f && second == 0x8b) tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            else tag = NbtIo.read(new DataInputStream(input), NbtAccounter.unlimitedHeap());
            validate(tag);
            return tag;
        } catch (RuntimeException error) { throw new IOException("Invalid stage NBT: " + error.getMessage(), error); }
    }

    public static Loaded load(Path file) throws IOException {
        if (Files.notExists(file) && Files.notExists(backup(file))) return new Loaded(null, false);
        IOException primaryError;
        try { return new Loaded(read(file), false); }
        catch (IOException error) { primaryError = error; }
        try { return new Loaded(read(backup(file)), true); }
        catch (IOException error) {
            IOException failure = new IOException("Primary and backup cannot be read: " + file + " (" + primaryError.getMessage()
                    + "); " + backup(file) + " (" + error.getMessage() + ")", primaryError);
            failure.addSuppressed(error); throw failure;
        }
    }

    public static synchronized void save(Path file, CompoundTag tag) throws IOException {
        validate(tag);
        Path directory = file.toAbsolutePath().normalize().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, file.getFileName() + ".", ".tmp");
        Path backupTemporary = null;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE);
                 DataOutputStream output = new DataOutputStream(Channels.newOutputStream(channel))) {
                NbtIo.write(tag, output); output.flush(); channel.force(true);
            }
            CompoundTag current = null;
            boolean primaryValid = false;
            if (Files.exists(file)) {
                try { current = read(file); primaryValid = true; }
                catch (IOException ignored) { /* Never rotate a corrupt primary over a valid backup. */ }
            }
            boolean backupValid = false;
            try { read(backup(file)); backupValid = true; }
            catch (IOException ignored) { }
            if (primaryValid && backupValid && tag.equals(current)) return; // Keep the older recovery point on unchanged saves.
            if (primaryValid || !backupValid) {
                backupTemporary = Files.createTempFile(directory, file.getFileName() + ".backup.", ".tmp");
                Files.copy(primaryValid ? file : temporary, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
                try (FileChannel channel = FileChannel.open(backupTemporary, StandardOpenOption.WRITE)) { channel.force(true); }
                Files.move(backupTemporary, backup(file), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
            if (backupTemporary != null) Files.deleteIfExists(backupTemporary);
        }
    }
}
