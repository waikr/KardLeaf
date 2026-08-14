package com.kangle.kardleaf.data.task;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class TaskTestDocumentsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.kangle.kardleaf.tasktest.documents";
    public static final String ROOT_ID = "root";

    private static final String[] ROOT_COLUMNS = {
        DocumentsContract.Root.COLUMN_ROOT_ID,
        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_TITLE,
        DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_ICON,
        DocumentsContract.Root.COLUMN_MIME_TYPES,
        DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
    };

    private static final String[] DOCUMENT_COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_ICON,
    };

    private static TaskTestDocumentsProvider instance;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong();
    private File rootDirectory;

    private static final class Node {
        final String id;
        final String parentId;
        String name;
        final boolean directory;
        final File file;

        Node(String id, String parentId, String name, boolean directory, File file) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.directory = directory;
            this.file = file;
        }
    }

    public static void reset() {
        if (instance != null) instance.resetTree();
    }

    @Override
    public boolean onCreate() {
        instance = this;
        resetTree();
        return true;
    }

    @Override
    public String getType(Uri uri) {
        List<String> path = uri.getPathSegments();
        try {
            if (path.size() == 4 && "tree".equals(path.get(0)) && "document".equals(path.get(2))) {
                return getDocumentType(path.get(3));
            }
            if (path.size() == 2 && "document".equals(path.get(0))) {
                return getDocumentType(path.get(1));
            }
        } catch (FileNotFoundException ignored) {
            return null;
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        List<String> path = uri.getPathSegments();
        try {
            if (path.size() == 1 && "root".equals(path.get(0))) {
                return queryRoots(projection);
            }
            if (path.size() == 2 && "document".equals(path.get(0))) {
                return queryDocument(path.get(1), projection);
            }
            if (path.size() == 4 && "tree".equals(path.get(0)) && "document".equals(path.get(2))) {
                return queryDocument(path.get(3), projection);
            }
            if (path.size() == 3 && "document".equals(path.get(0)) && "children".equals(path.get(2))) {
                return queryChildDocuments(path.get(1), projection, sortOrder);
            }
            if (path.size() == 5 && "tree".equals(path.get(0)) && "document".equals(path.get(2)) &&
                "children".equals(path.get(4))) {
                return queryChildDocuments(path.get(3), projection, sortOrder);
            }
        } catch (FileNotFoundException error) {
            throw new IllegalArgumentException(error);
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal) {
        return query(uri, projection, null, null, null);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        return handleCall(method, extras);
    }

    @Override
    public Bundle call(String authority, String method, String arg, Bundle extras) {
        return handleCall(method, extras);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return openDocument(DocumentsContract.getDocumentId(uri), mode, null);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal)
        throws FileNotFoundException {
        return openDocument(DocumentsContract.getDocumentId(uri), mode, signal);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            deleteDocument(DocumentsContract.getDocumentId(uri));
            return 1;
        } catch (FileNotFoundException error) {
            return 0;
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private Bundle handleCall(String method, Bundle extras) {
        if ("testReset".equals(method)) {
            resetTree();
            return Bundle.EMPTY;
        }
        if (extras == null) return Bundle.EMPTY;
        Uri documentUri = extras.getParcelable("uri");
        if (documentUri == null) return Bundle.EMPTY;
        String documentId = DocumentsContract.getDocumentId(documentUri);
        Bundle result = new Bundle();
        try {
            if ("android:createDocument".equals(method)) {
                String newDocumentId = createDocument(
                    documentId,
                    extras.getString(DocumentsContract.Document.COLUMN_MIME_TYPE),
                    extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                );
                result.putParcelable("uri", buildDocumentUri(documentUri, newDocumentId));
            } else if ("android:renameDocument".equals(method)) {
                String newDocumentId = renameDocument(
                    documentId,
                    extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                );
                result.putParcelable("uri", buildDocumentUri(documentUri, newDocumentId));
            } else if ("android:deleteDocument".equals(method)) {
                deleteDocument(documentId);
            }
        } catch (FileNotFoundException error) {
            throw new IllegalArgumentException(error);
        }
        return result;
    }

    private Uri buildDocumentUri(Uri baseUri, String documentId) {
        return DocumentsContract.isTreeUri(baseUri)
            ? DocumentsContract.buildDocumentUriUsingTree(baseUri, documentId)
            : DocumentsContract.buildDocumentUri(AUTHORITY, documentId);
    }

    public String getDocumentType(String documentId) throws FileNotFoundException {
        return node(documentId).directory
            ? DocumentsContract.Document.MIME_TYPE_DIR
            : "text/markdown";
    }

    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = cursor(projection, ROOT_COLUMNS);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            Object value;
            switch (column) {
                case DocumentsContract.Root.COLUMN_ROOT_ID:
                case DocumentsContract.Root.COLUMN_DOCUMENT_ID:
                    value = ROOT_ID;
                    break;
                case DocumentsContract.Root.COLUMN_TITLE:
                    value = "Task test files";
                    break;
                case DocumentsContract.Root.COLUMN_FLAGS:
                    value = DocumentsContract.Root.FLAG_SUPPORTS_CREATE;
                    break;
                case DocumentsContract.Root.COLUMN_ICON:
                    value = 0;
                    break;
                case DocumentsContract.Root.COLUMN_MIME_TYPES:
                    value = DocumentsContract.Document.MIME_TYPE_DIR;
                    break;
                case DocumentsContract.Root.COLUMN_AVAILABLE_BYTES:
                    value = rootDirectory.getFreeSpace();
                    break;
                default:
                    value = null;
            }
            row.add(value);
        }
        return cursor;
    }

    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = cursor(projection, DOCUMENT_COLUMNS);
        addDocumentRow(cursor, node(documentId));
        return cursor;
    }

    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
        throws FileNotFoundException {
        MatrixCursor cursor = cursor(projection, DOCUMENT_COLUMNS);
        List<Node> children = new ArrayList<>();
        for (Node child : nodes.values()) {
            if (parentDocumentId.equals(child.parentId)) children.add(child);
        }
        children.sort((first, second) -> first.name.compareTo(second.name));
        for (Node child : children) addDocumentRow(cursor, child);
        return cursor;
    }

    public String createDocument(String parentDocumentId, String mimeType, String displayName)
        throws FileNotFoundException {
        Node parent = node(parentDocumentId);
        if (!parent.directory) throw new FileNotFoundException("Not a directory: " + parentDocumentId);
        for (Node child : nodes.values()) {
            if (parentDocumentId.equals(child.parentId) && displayName.equals(child.name)) {
                throw new FileNotFoundException("Duplicate document: " + displayName);
            }
        }
        File file = new File(parent.file, displayName);
        boolean directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
        try {
            if (directory ? !file.mkdirs() : !file.createNewFile()) {
                throw new IOException("Could not create document");
            }
        } catch (IOException error) {
            FileNotFoundException failure = new FileNotFoundException(file.toString());
            failure.initCause(error);
            throw failure;
        }
        String id = "node-" + nextId.incrementAndGet();
        nodes.put(id, new Node(id, parentDocumentId, displayName, directory, file));
        return id;
    }

    public void deleteDocument(String documentId) throws FileNotFoundException {
        Node target = node(documentId);
        List<String> removed = new ArrayList<>();
        for (Node candidate : nodes.values()) {
            if (candidate.id.equals(documentId) || isDescendant(candidate.id, documentId)) {
                removed.add(candidate.id);
            }
        }
        for (String id : removed) nodes.remove(id);
        if (!deleteTree(target.file)) throw new FileNotFoundException(target.file.toString());
    }

    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        Node target = node(documentId);
        File renamed = new File(target.file.getParentFile(), displayName);
        if (!target.file.renameTo(renamed)) throw new FileNotFoundException(target.file.toString());
        target.name = displayName;
        return target.id;
    }

    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
        throws FileNotFoundException {
        return ParcelFileDescriptor.open(node(documentId).file, ParcelFileDescriptor.parseMode(mode));
    }

    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return isDescendant(documentId, parentDocumentId);
    }

    private void resetTree() {
        rootDirectory = new File(getContext().getCacheDir(), "task-test-documents");
        deleteTree(rootDirectory);
        if (!rootDirectory.mkdirs()) throw new IllegalStateException("Could not create test document root");
        nodes.clear();
        nextId.set(0L);
        nodes.put(ROOT_ID, new Node(ROOT_ID, null, "root", true, rootDirectory));
    }

    private Node node(String documentId) throws FileNotFoundException {
        Node result = nodes.get(documentId);
        if (result == null) throw new FileNotFoundException("Unknown document: " + documentId);
        return result;
    }

    private boolean isDescendant(String documentId, String ancestorId) {
        Node node = nodes.get(documentId);
        String current = node == null ? null : node.parentId;
        while (current != null) {
            if (ancestorId.equals(current)) return true;
            Node parent = nodes.get(current);
            current = parent == null ? null : parent.parentId;
        }
        return false;
    }

    private void addDocumentRow(MatrixCursor cursor, Node node) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            Object value;
            switch (column) {
                case DocumentsContract.Document.COLUMN_DOCUMENT_ID:
                    value = node.id;
                    break;
                case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
                    value = node.name;
                    break;
                case DocumentsContract.Document.COLUMN_SIZE:
                    value = node.directory ? 0L : node.file.length();
                    break;
                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    value = node.directory ? DocumentsContract.Document.MIME_TYPE_DIR : "text/markdown";
                    break;
                case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                    value = node.file.lastModified();
                    break;
                case DocumentsContract.Document.COLUMN_FLAGS:
                    value = node.directory
                        ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE |
                            DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                        : DocumentsContract.Document.FLAG_SUPPORTS_WRITE |
                            DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
                    break;
                case DocumentsContract.Document.COLUMN_ICON:
                    value = 0;
                    break;
                default:
                    value = null;
            }
            row.add(value);
        }
    }

    private MatrixCursor cursor(String[] projection, String[] fallback) {
        return new MatrixCursor(projection == null ? fallback : projection);
    }

    private boolean deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        return !file.exists() || file.delete();
    }
}
