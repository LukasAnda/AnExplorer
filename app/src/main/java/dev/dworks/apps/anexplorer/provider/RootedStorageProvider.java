/*
 * Copyright (C) 2014 Hari Krishna Dulipudi
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.dworks.apps.anexplorer.provider;

import android.annotation.SuppressLint;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.stericson.RootTools.RootTools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import dev.dworks.apps.anexplorer.BuildConfig;
import dev.dworks.apps.anexplorer.R;
import dev.dworks.apps.anexplorer.cursor.MatrixCursor;
import dev.dworks.apps.anexplorer.cursor.MatrixCursor.RowBuilder;
import dev.dworks.apps.anexplorer.misc.CancellationSignal;
import dev.dworks.apps.anexplorer.misc.FileUtils;
import dev.dworks.apps.anexplorer.misc.MimePredicate;
import dev.dworks.apps.anexplorer.root.RootCommands;
import dev.dworks.apps.anexplorer.root.RootFile;
import dev.dworks.apps.anexplorer.model.DocumentsContract;
import dev.dworks.apps.anexplorer.model.DocumentsContract.Document;
import dev.dworks.apps.anexplorer.model.DocumentsContract.Root;
import dev.dworks.apps.anexplorer.model.GuardedBy;

public class RootedStorageProvider extends StorageProvider {
    private static final String TAG = "RootedStorage";

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".rootedstorage.documents";

    // docId format: root:path/to/file

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID, Root.COLUMN_AVAILABLE_BYTES, Root.COLUMN_TOTAL_BYTES, Root.COLUMN_PATH,
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_PATH, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_SUMMARY,
    };

    private static class RootInfo {
        public String rootId;
        public int flags;
        public String title;
        public String docId;
        public String path;
    }

    public static final String ROOT_ID_ROOT = "Root";

    private final Object mRootsLock = new Object();

    @GuardedBy("mRootsLock")
    private ArrayList<RootInfo> mRoots;
    @GuardedBy("mRootsLock")
    private HashMap<String, RootInfo> mIdToRoot;
    @GuardedBy("mRootsLock")
    private HashMap<String, RootFile> mIdToPath;

    @Override
    public boolean onCreate() {
        mRoots = Lists.newArrayList();
        mIdToRoot = Maps.newHashMap();
        mIdToPath = Maps.newHashMap();

    	try {
            final String rootId = ROOT_ID_ROOT;
            final RootFile path = new RootFile("/");
            mIdToPath.put(rootId, path);

            final RootInfo root = new RootInfo();
            root.rootId = rootId;
            root.flags =  Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_EDIT | Root.FLAG_LOCAL_ONLY | Root.FLAG_ADVANCED
                    | Root.FLAG_SUPPORTS_SEARCH;
            root.title = getContext().getString(R.string.root_root_storage);
            root.docId = getDocIdForRootFile(path);
            mRoots.add(root);
            mIdToRoot.put(rootId, root);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
        return true;
    }
    
    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    private String getDocIdForRootFile(RootFile file) throws FileNotFoundException {
        String path = file.getAbsolutePath();

        // Find the most-specific root path
        Map.Entry<String, RootFile> mostSpecific = null;
        synchronized (mRootsLock) {
            for (Map.Entry<String, RootFile> root : mIdToPath.entrySet()) {
                final String rootPath = root.getValue().getPath();
                if (path.startsWith(rootPath) && (mostSpecific == null
                        || rootPath.length() > mostSpecific.getValue().getPath().length())) {
                    mostSpecific = root;
                }
            }
        }

        if (mostSpecific == null) {
            throw new FileNotFoundException("Failed to find root that contains " + path);
        }

        // Start at first char of path under root
        final String rootPath = mostSpecific.getValue().getPath();
        if (rootPath.equals(path)) {
            path = "";
        } else if (rootPath.endsWith("/")) {
            path = path.substring(rootPath.length());
        } else {
            path = path.substring(rootPath.length() + 1);
        }

        return mostSpecific.getKey() + ':' + path;
    }
    
    private RootFile getRootFileForDocId(String docId) throws FileNotFoundException {
        final int splitIndex = docId.indexOf(':', 1);
        final String tag = docId.substring(0, splitIndex);
        final String path = docId.substring(splitIndex + 1);

        RootFile target;
        synchronized (mRootsLock) {
            target = mIdToPath.get(tag);
        }
        if (target == null) {
            throw new FileNotFoundException("No root for " + tag);
        }

        target = new RootFile(target.getAbsolutePath(), path);
        return target;
    }

    private void includeRootFile(MatrixCursor result, String docId, RootFile file)
            throws FileNotFoundException {
        if (docId == null) {
            docId = getDocIdForRootFile(file);
        } else {
            file = getRootFileForDocId(docId);
        }

        int flags = 0;

        if(!file.isValid()){
        	return;
        }
        
        if (file.canWrite()) {
            if (file.isDirectory()) {
                flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
                flags |= Document.FLAG_SUPPORTS_DELETE;
                flags |= Document.FLAG_SUPPORTS_RENAME;
            } else {
                flags |= Document.FLAG_SUPPORTS_WRITE;
                flags |= Document.FLAG_SUPPORTS_DELETE;
                flags |= Document.FLAG_SUPPORTS_RENAME;
            }
            flags |= Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_EDIT ;
        }

        final String displayName = file.getName();
        final String mimeType = getTypeForFile(file);
        if(MimePredicate.mimeMatches(MimePredicate.VISUAL_MIMES, mimeType)){
            flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
        }

        final RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, docId);
        row.add(Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        row.add(Document.COLUMN_PATH, file.getAbsolutePath());
        row.add(Document.COLUMN_FLAGS, flags);
/*        if(file.isDirectory() && null != file.list()){
        	row.add(Document.COLUMN_SUMMARY, file.list().length + " files");
        }*/
        // Only publish dates reasonably after epoch
/*        long lastModified = file.lastModified();
        if (lastModified > 31536000000L) {
            row.add(Document.COLUMN_LAST_MODIFIED, lastModified);
        }*/
    }
    
    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
        synchronized (mRootsLock) {
            for (String rootId : mIdToPath.keySet()) {
                final RootInfo root = mIdToRoot.get(rootId);
                final RootFile file = mIdToPath.get(rootId);
                
                final RowBuilder row = result.newRow();
                row.add(Root.COLUMN_ROOT_ID, root.rootId);
                row.add(Root.COLUMN_FLAGS, root.flags);
                row.add(Root.COLUMN_TITLE, root.title);
                row.add(Root.COLUMN_PATH, root.path);
                row.add(Root.COLUMN_DOCUMENT_ID, root.docId);
            }
        }
        return result;
    }

    @Override
    public String createDocument(String docId, String mimeType, String displayName)
            throws FileNotFoundException {
        final RootFile parent = getRootFileForDocId(docId);
        if (!parent.isDirectory()) {
            throw new IllegalArgumentException("Parent document isn't a directory");
        }

        File file;
        String path = parent.getPath();
        if (Document.MIME_TYPE_DIR.equals(mimeType)) {
            file = new File(parent.getPath(), displayName);
            if (!RootCommands.createRootdir(path, displayName)) {
                throw new IllegalStateException("Failed to mkdir " + file);
            }
        } else {
            displayName = FileUtils.removeExtension(mimeType, displayName);
            file = new File(path, FileUtils.addExtension(mimeType, displayName));

            // If conflicting file, try adding counter suffix
            int n = 0;
            while (file.exists() && n++ < 32) {
                file = new File(path, FileUtils.addExtension(mimeType, displayName + " (" + n + ")"));
            }

            try {
                if (!RootCommands.createRootFile(path, file.getName())) {
                    throw new IllegalStateException("Failed to touch " + file);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to touch " + file + ": " + e);
            }
        }
        return getDocIdForRootFile(new RootFile(path, displayName));
    }

    @Override
    public void deleteDocument(String docId) throws FileNotFoundException {
        final RootFile file = getRootFileForDocId(docId);
        if (!RootCommands.deleteFileRoot(file.getPath())) {
            throw new IllegalStateException("Failed to delete " + file);
        }
    }
    
    @Override
    public void moveDocument(String documentIdFrom, String documentIdTo, boolean deleteAfter) throws FileNotFoundException {
    	final RootFile fileFrom = getRootFileForDocId(documentIdFrom);
    	final RootFile fileTo = getRootFileForDocId(documentIdTo);
        if (!RootCommands.moveCopyRoot(fileFrom.getPath(), fileTo.getPath())) {
            throw new IllegalStateException("Failed to copy " + fileFrom);
        }
        else{
        	if (deleteAfter) {
                if (!RootCommands.deleteFileRoot(fileFrom.getPath())) {
                    throw new IllegalStateException("Failed to delete " + fileFrom);
                }
			}
        }
    }

    @Override
    public String renameDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        final RootFile file = getRootFileForDocId(parentDocumentId);
        File newFile;
        String path = file.getPath();
        String parentPath = FileUtils.getPathFromFilepath(path);
        String newName = displayName;


        if (Document.MIME_TYPE_DIR.equals(mimeType)) {
            newFile = new File(parentPath, FileUtils.removeExtension(mimeType, displayName));
        }
        else{
            displayName = FileUtils.removeExtension(mimeType, displayName);
            newFile = new File(parentPath, FileUtils.addExtension(mimeType, displayName));
        }

        if(RootCommands.renameRootTarget(parentPath, FileUtils.getName(path), newName)){
            throw new IllegalStateException("Failed to rename " + file);
        }
        return getDocIdForRootFile(new RootFile(newFile.getParent(), displayName));
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        includeRootFile(result, documentId, null);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        final RootFile parent = getRootFileForDocId(parentDocumentId);
        final MatrixCursor result = new DirectoryCursor(
                resolveDocumentProjection(projection), parentDocumentId, parent);
        try {
            BufferedReader br = RootCommands.listFiles(parent.getPath());
            if (null != br){
            	Scanner scanner = new Scanner(br);
            	while (scanner.hasNextLine()) {
            	  String line = scanner.nextLine();
            	  try {
            		  includeRootFile(result, null, new RootFile(parent, line));
            	  } catch (Exception e) {
            		  e.printStackTrace();
            	  }

            	}
            	scanner.close();
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
        return result;
    }

    @SuppressWarnings("unused")
	@Override
    public Cursor querySearchDocuments(String rootId, String query, String[] projection)
            throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));

        final RootFile parent;
        synchronized (mRootsLock) {
            parent = mIdToPath.get(rootId);
        }

        try {
            BufferedReader br = RootCommands.findFiles(parent.getPath(), query);
            if (null != br){
                Scanner scanner = new Scanner(br);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    try {
                        includeRootFile(result, null, new RootFile(parent, line));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
                scanner.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        final RootFile file = getRootFileForDocId(documentId);
        return getTypeForFile(file);
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        //final File file = getRootFileForDocId(documentId);
        return null;//ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);//ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(
            String documentId, Point sizeHint, CancellationSignal signal)
            throws FileNotFoundException {
        return openOrCreateDocumentThumbnail(documentId, sizeHint, signal);
    }
    
    public AssetFileDescriptor openOrCreateDocumentThumbnail(
            String docId, Point sizeHint, CancellationSignal signal) throws FileNotFoundException {
//        final ContentResolver resolver = getContext().getContentResolver();
        final RootFile file = getRootFileForDocId(docId);
        final String mimeType = getTypeForFile(file);
    	
        final String typeOnly = mimeType.split("/")[0];
    
        final long token = Binder.clearCallingIdentity();
        try {
            if ("audio".equals(typeOnly)) {
                final long id = getAlbumForPathCleared(file.getPath());
                return openOrCreateAudioThumbnailCleared(id, signal);
            } else if ("image".equals(typeOnly)) {
                final long id = getImageForPathCleared(file.getPath());
                return openOrCreateImageThumbnailCleared(id, signal);
            } else if ("video".equals(typeOnly)) {
                final long id = getVideoForPathCleared(file.getPath());
                return openOrCreateVideoThumbnailCleared(id, signal);
            } else {
            	return null;//DocumentsContract.openImageThumbnail(file);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @SuppressWarnings("unused")
	private static String getTypeForFile(File file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        } else {
            return getTypeForName(file.getName());
        }
    }

    private static String getTypeForFile(RootFile file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        } else {
            return getTypeForName(file.getName());
        }
    }
    
    @SuppressLint("DefaultLocale")
	private static String getTypeForName(String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1).toLowerCase();
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }

        return "application/octet-stream";
    }

    private class DirectoryCursor extends MatrixCursor {

        public DirectoryCursor(String[] columnNames, String docId, RootFile file) {
            super(columnNames);
            final Uri notifyUri = DocumentsContract.buildChildDocumentsUri(AUTHORITY, docId);
            setNotificationUri(getContext().getContentResolver(), notifyUri);
        }

        @Override
        public void close() {
            super.close();
        }
    }
}