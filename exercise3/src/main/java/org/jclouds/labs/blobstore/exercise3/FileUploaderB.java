/*
 * @(#)BlobWriterReader.java     2 Nov 2011
 *
 * Copyright © 2010 Andrew Phillips.
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.jclouds.labs.blobstore.exercise3;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.logging.log4j.config.Log4JLoggingModule;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;

/**
 * @author aphillips
 * @since 2 Nov 2011
 *
 */
public class FileUploaderB {
    private static final int QUERY_RETRY_INTERVAL_MILLIS = 100;
    
    private final BlobStoreContext ctx;
    private final ExecutorService executor;
    
    public FileUploaderB(String provider, String identity, String credential) {
        ctx = ContextBuilder.newBuilder(provider).credentials(identity, credential)
              .modules(ImmutableSet.of(new Log4JLoggingModule())).buildView(BlobStoreContext.class);
        executor = Executors.newSingleThreadExecutor(); // many other options available here
    }
    
    public void uploadFile(final File file) throws IOException, InterruptedException, ExecutionException {
        final BlobStore store = ctx.getBlobStore();
        final String containerName = "test-container-3";
        long fileSize = file.length();
        System.out.format("Starting upload of %d bytes%n", fileSize);
        final String filename = file.getName();
        // simulates a different user uploading a blob for which we are waiting
        Future<String> putBlobOperation = executor.submit(new Callable<String>() {
              @Override
              public String call() throws Exception {
                 return store.putBlob(containerName, store.blobBuilder(filename).payload(file).build());
              }
           });
        waitUntilBlobExistsTrue(store, containerName, filename);
        byte[] payloadRead = ByteStreams.toByteArray(
                store.getBlob(containerName, filename).getPayload().getInput());
        System.out.format("Retrieved blob size: %d bytes%n", payloadRead.length);
        System.out.format("Blob metadata: %s%n", store.blobMetadata(containerName, filename).getContentMetadata());
        waitUntilUploaded(putBlobOperation);
        Blob retrievedBlob = store.getBlob(containerName, filename);
        payloadRead = ByteStreams.toByteArray(retrievedBlob.getPayload().getInput());
        System.out.format("Retrieved blob size now: %d bytes%n", payloadRead.length);
        System.out.format("Blob metadata now: %s%n", store.blobMetadata(containerName, filename).getContentMetadata());
        tryDeleteContainer(store, containerName);
    }
    
    private static void waitUntilBlobExistsTrue(BlobStore store, String containerName, String blobName) throws InterruptedException {
        while (!store.blobExists(containerName, blobName)) {
            TimeUnit.MILLISECONDS.sleep(QUERY_RETRY_INTERVAL_MILLIS);
            System.out.println("Waiting for blob to 'exist'");
        }
        System.out.println("Blob exists");
    }
    
    private static void waitUntilUploaded(Future<String> uploadOperation) throws InterruptedException, ExecutionException {
        System.out.println("Waiting for upload to complete");
        uploadOperation.get();
        System.out.println("Upload completed");        
    }
    
    private static void tryDeleteContainer(BlobStore store, String containerName) {
        try {
            store.deleteContainer(containerName);
        } catch (RuntimeException exception) {
            System.err.format("Unable to delete container due to: %s%n", exception.getMessage());
        }
    }
    
    public void cleanup() {
        ctx.close();
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.format("%nUsage: %s <provider> <identity> <credential>%n", FileUploaderB.class.getSimpleName());
            System.exit(1);
        }
        FileUploaderB uploader = new FileUploaderB(args[0], args[1], args[2]);
        try {
            uploader.uploadFile(new File("src/main/resources/s3-qrc.pdf"));
        } finally {
            uploader.cleanup();
        }
    }
}
