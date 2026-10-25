package studypilot;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

final class JsonFiles {
    private JsonFiles() { }

    static Path namedFile(Path directory, String id) {
        try {
            String validId = UUID.fromString(id).toString();
            if (!validId.equals(id)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid saved object ID.");
        }
        return directory.resolve(id + ".json");
    }

    static JSONObject read(Path file) {
        try {
            String text = Files.readString(file);
            return new JSONObject(text);
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("Saved data could not be read. The original file was not changed.");
        }
    }

    static void write(Path file, JSONObject data) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "save-", ".tmp");
            Files.writeString(temporary, data.toString(2));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Data could not be saved. Check the data directory permissions.");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    System.err.println("A temporary save file could not be removed.");
                }
            }
        }
    }
}
