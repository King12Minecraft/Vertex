/**
 * CustomGameUploader
 * -------------------
 * The one place that actually sends a CUSTOM_GAME_UPLOAD_REQUEST -
 * shared by UploadCustomGameDialog ("Upload Project", jar picked from
 * disk) and CodeEditorWindow ("Publish to Server", jar built in-memory
 * from freshly compiled source) so the two entry points into this
 * feature don't duplicate the same request-building/error-handling
 * logic. BLOCKING - always call from a background thread.
 */
public class CustomGameUploader
{
    private CustomGameUploader()
    {
        // Static utility class - never instantiated.
    }

    /** Returns null on success, or a user-facing error message. */
    public static String upload(String name, String entryClassName, byte[] jarBytes)
    {
        Message request = new Message();
        request.setType(MessageType.CUSTOM_GAME_UPLOAD_REQUEST);
        request.setCustomGameName(name);
        request.setCustomGameEntryClass(entryClassName);
        request.setFileData(jarBytes);

        Message response = NetworkManager.send(request);
        if (response == null)
        {
            return "Could not reach the server.";
        }
        if (!response.isSuccess())
        {
            return response.getErrorText() != null ? response.getErrorText() : "Upload failed.";
        }
        return null;
    }
}
