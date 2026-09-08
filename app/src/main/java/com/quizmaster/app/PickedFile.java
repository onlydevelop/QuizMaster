package com.quizmaster.app;

/**
 * Identity of a user-picked source file: its content hash (stable across
 * different picker URIs for the same physical file, see
 * MainActivity.computeContentHash), the picker URI it was most recently
 * opened through, and its display name.
 */
public class PickedFile {

    public final String contentKey;
    public final String uri;
    public final String displayName;

    public PickedFile(String contentKey, String uri, String displayName) {
        this.contentKey = contentKey;
        this.uri = uri;
        this.displayName = displayName;
    }
}
